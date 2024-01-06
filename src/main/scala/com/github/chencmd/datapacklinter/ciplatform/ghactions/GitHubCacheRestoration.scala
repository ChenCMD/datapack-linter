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

import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.circe.*
import fs2.io.net.Network

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

                  def getPreviousPushCommitHash(): F[Option[String]] = for {
                    uri <- Uri
                      .fromString(
                        s"""|${ghCtx.apiUrl}/repos/${repos.owner}/${repos.name}/actions/runs
                            |?branch=${head.ref}
                            |&event=pull_request
                            |&status=completed
                            |""".stripMargin
                      )
                      .fold(fail => R.raiseOne(fail.message), _.pure[F])
                    res <- client
                      .expect(Request[F](Method.GET, uri)) {
                        case class WorkflowRun(head_sha: String)
                        case class RepoActionRunsResult(workflow_runs: List[WorkflowRun])
                        jsonOf[F, RepoActionRunsResult]
                      }
                      .handleErrorWith(err => R.raiseOne(err.getMessage()))
                  } yield res.workflow_runs.get(1).map(_.head_sha)

                  // TODO support api pagination
                  def getTwoCommitBetweenCommitMessages(from: String, to: String): F[List[String]] = for {
                    uri <- Uri
                      .fromString(s"${ghCtx.apiUrl}/repos/${repos.owner}/${repos.name}/compare/$from..$to?per_page=100")
                      .fold(fail => R.raiseOne(fail.message), _.pure[F])
                    res <- client
                      .expect(Request[F](Method.GET, uri)) {
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
