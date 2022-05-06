import zio.stream._
import zio._

object UsingZIOStreams extends ZIOAppDefault {

  // simple model
  case class ProductId(id: String)
  case class Product(id: ProductId, createdAt: java.time.YearMonth)
  case class ItemId(id: String)
  case class Item(id: ItemId, productId: ProductId)
  case class OrderId(id: String)
  case class Order(id: OrderId, items: Seq[(ItemId, Item)])

  // generator
  sealed trait Generator[A] { def next: A }
  object Generator {
    val random = {
      val r = new scala.util.Random();
      r // r.setSeed(0); r
    }
  }
  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    ZIO.never
  }

}
