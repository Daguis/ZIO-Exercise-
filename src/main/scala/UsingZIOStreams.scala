import zio.stream._
import zio._

object UsingZIOStreams extends ZIOAppDefault {

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    ZIO.never
  }

}
