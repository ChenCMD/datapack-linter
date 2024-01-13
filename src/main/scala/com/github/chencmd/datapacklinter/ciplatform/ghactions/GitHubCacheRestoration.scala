package com.github.chencmd.datapacklinter.ciplatform.ghactions

import com.github.chencmd.datapacklinter.ciplatform.CIPlatformCacheRestorationInstr
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformInteractionInstr
import com.github.chencmd.datapacklinter.ciplatform.CIPlatformReadKeyedConfigInstr
import com.github.chencmd.datapacklinter.ciplatform.ghactions.utils.ActionsGitHub
import com.github.chencmd.datapacklinter.generic.EitherTExtra
import com.github.chencmd.datapacklinter.generic.RaiseNec
import com.github.chencmd.datapacklinter.term.RestoreCacheOrSkip

import cats.data.EitherT
import cats.effect.Async
import cats.implicits.*

import scala.scalajs.js

import typings.octokitWebhooksTypes.anon.Label
import typings.octokitWebhooksTypes.anon.Repo
import typings.octokitWebhooksTypes.mod.PushEvent
import typings.octokitWebhooksTypes.mod.Repository

import fs2.io.net.Network
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.ci.*

object GitHubCacheRestoration {
  def createInstr[F[_]: Async]()(using
    ciInteraction: CIPlatformInteractionInstr[F],
    inputReader: CIPlatformReadKeyedConfigInstr[F],
    R: RaiseNec[F, String]
  ): F[CIPlatformCacheRestorationInstr[F]] = for {
    ghCtx <- ActionsGitHub.getContext()

    token <- inputReader.readKey[String]("GITHUB_TOKEN")

    instr = new CIPlatformCacheRestorationInstr[F] {
      override def shouldRestoreCache(): F[RestoreCacheOrSkip] = {
        import RestoreCacheOrSkip.*
        val program = for {
          commitMessages: List[String] <- EitherT.liftF {
            ghCtx.eventName match {
              case "push" => ghCtx.payload.asInstanceOf[PushEvent].commits.toList.map(_.message).pure[F]
              case "pull_request" if List("opened", "reopened", "synchronize").contains(ghCtx.payload.action) =>
                ciInteraction.printInfo(token.show) *>
                token.toOption.filter(_.nonEmpty).fold(
                    ciInteraction
                      .printWarning(
                        "The check for commit messages in pull_request is skipped because GITHUB_TOKEN is not provided as input."
                      )
                      .as(List.empty)
                )(
                  t => {
                    trait PullRequest        {
                      val base: Repo
                      val head: Label
                    }
                    trait PullRequestPayload {
                      val repository: Repository
                      val pull_request: PullRequest
                    }
                    val payload = ghCtx.payload.asInstanceOf[PullRequestPayload]
                    getCommitMessages(payload.repository, payload.pull_request.base, payload.pull_request.head, t)
                  }
                )

              case _ => List.empty.pure[F]
            }
          }
          _ <- EitherTExtra.exitWhenA(commitMessages.exists(_.toLowerCase().contains("[regenerate cache]"))) {
            Skip("The cache is not used because the commit message contains '[regenerate cache]'.")
          }
          _ <- EitherTExtra.exitWhenA(ghCtx.eventName == "workflow_dispatch") {
            Skip("The cache is not used because it is executed from the workflow_dispatch event.")
          }
        } yield Restore
        program.merge
      }

      private def getCommitMessages(repos: Repository, base: Repo, head: Label, token: String): F[List[String]] = {
        given Network[F] = Network.forAsync
        EmberClientBuilder.default[F].build.use { client =>
          def makeGitHubAPIRequest(
            method: Method,
            endpoint: String,
            queryParams: List[(String, String)] = List.empty
          ): F[Request[F]] = {
            val qp = if queryParams.nonEmpty then "?" + queryParams.map(p => s"${p._1}=${p._2}").mkString("&") else ""
            Uri
              .fromString(s"${ghCtx.apiUrl}/$endpoint$qp")
              .map(Request[F](method, _, headers = Headers(Header.Raw(ci"Authorization", token))))
              .fold(fail => R.raiseOne(fail.message), _.pure[F])
          }

          def getPreviousPushCommitHash(): F[Option[String]] = for {
            req <- makeGitHubAPIRequest(
              Method.GET,
              s"repos/${repos.full_name}/actions/runs",
              List("branch" -> head.ref, "event" -> "pull_request", "status" -> "completed")
            )
            res <- client
              .expect(req) {
                case class WorkflowRun(head_sha: String)
                case class RepoActionRunsResult(workflow_runs: List[WorkflowRun])
                jsonOf[F, RepoActionRunsResult]
              }
              .handleErrorWith(err => R.raiseOne(err.getMessage()))
          } yield res.workflow_runs.get(1).map(_.head_sha)

          // TODO support api pagination
          def getTwoCommitBetweenCommitMessages(from: String, to: String): F[List[String]] = for {
            req <- makeGitHubAPIRequest(
              Method.GET,
              s"repos/${repos.full_name}/compare/$from...$to",
              List("per_page" -> "100")
            )
            res <- client
              .expect(req) {
                case class CommitDetail(message: String)
                case class Commit(commit: CommitDetail)
                case class RepoCompareResult(commits: List[Commit])
                jsonOf[F, RepoCompareResult]
              }
              .handleErrorWith(err => R.raiseOne(err.getMessage()))
          } yield res.commits.drop(1).map(_.commit.message)

          for {
            prevPushCommitHash <- getPreviousPushCommitHash()
            prevHash = prevPushCommitHash.getOrElse(base.sha)
            commitMessages <- getTwoCommitBetweenCommitMessages(prevHash, head.sha)
          } yield commitMessages
        }
      }
    }
  } yield instr
}
