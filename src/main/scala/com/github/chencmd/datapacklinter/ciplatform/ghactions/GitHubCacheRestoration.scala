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

import typings.octokitWebhooksTypes.mod.PullRequestSynchronizeEvent
import typings.octokitWebhooksTypes.mod.PushEvent

import fs2.io.net.Network
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.circe.*
import org.typelevel.ci.*
import org.http4s.ember.client.EmberClientBuilder

object GitHubCacheRestoration {
  def createInstr[F[_]: Async]()(using
    ciInteraction: CIPlatformInteractionInstr[F],
    inputReader: CIPlatformReadKeyedConfigInstr[F],
    R: RaiseNec[F, String]
  ): F[CIPlatformCacheRestorationInstr[F]] = for {
    ghCtx <- ActionsGitHub.getContext()

    tokenOrError <- inputReader.readKey[String]("GITHUB_TOKEN")
    token        <- tokenOrError.fold(R.raise, _.pure[F])

    instr = new CIPlatformCacheRestorationInstr[F] {
      override def shouldRestoreCache(): F[RestoreCacheOrSkip] = {
        import RestoreCacheOrSkip.*
        val program = for {
          commitMessages: List[String] <- EitherT.liftF {
            given Network[F] = Network.forAsync
            EmberClientBuilder.default[F].build.use { client =>
              ghCtx.eventName match {
                case "push" => ghCtx.payload.asInstanceOf[PushEvent].commits.toList.map(_.message).pure[F]
                case "pull_request" if ghCtx.payload.action == "synchronize" =>
                  val payload = ghCtx.payload.asInstanceOf[PullRequestSynchronizeEvent]
                  val repos   = payload.repository
                  val head    = payload.pull_request.head

                  def makeGitHubAPIRequest(
                    method: Method,
                    endpoint: String,
                    queryParams: List[(String, String)] = List.empty
                  ): F[Request[F]] = {
                    val qp = {
                      if (queryParams.nonEmpty) {
                        "?" + queryParams.map { case (k, v) => s"$k=$v" }.mkString("&")
                      } else {
                        ""
                      }
                    }
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
                      s"repos/${repos.full_name}/compare/$from..$to",
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

                  import scala.scalajs.js.JSConverters.*
                  import typings.node.nodeColonconsoleMod.global.console.^ as console
                  for {
                    _              <- Async[F].delay(console.log(js.JSON.stringify(ghCtx)))
                    prevPushCommitHash <- getPreviousPushCommitHash()
                    prevHash = prevPushCommitHash.getOrElse(payload.pull_request.base.sha)
                    _              <- Async[F].delay(console.log(prevHash))
                    commitMessages <- getTwoCommitBetweenCommitMessages(prevHash, head.sha)
                    _              <- Async[F].delay(console.log(commitMessages.toJSArray))
                  } yield commitMessages
                case _                                                       => List.empty.pure[F]
              }
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
    }
  } yield instr
}
