import cats.effect.IOApp
import cats.effect.ExitCode
import cats.effect.IO

object DatapackLinter extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = IO {
    ExitCode.Success
  }
}
