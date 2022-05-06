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

    def id(name: String): Generator[String] =
      new Generator[String] {
        override def next: String = s"$name-${random.nextInt()}"
      }

    object product extends Generator[Product] {
      implicit val id = new Generator[ProductId] {
        override def next: ProductId =
          ProductId(Generator.id("product").next)
      }
      override def next: Product =
        Product(
          id = id.next,
          createdAt = java.time.YearMonth.now minusMonths random.between(1, 24)
        )

    }

    object item extends Generator[Item] {
      implicit val id = new Generator[ItemId] {
        override def next: ItemId =
          ItemId(Generator.id("item").next)
      }
      override def next: Item =
        Item(
          id = id.next,
          productId = product.id.next
        )
    }

    object order extends Generator[Order] {
      implicit val id = new Generator[OrderId] {
        override def next: OrderId =
          OrderId(Generator.id("order").next)
      }
      override def next: Order =
        Order(
          id = id.next,
          items = (1 to 10).map(_ => item.id.next -> item.next)
        )
    }
  }

  trait OrderRepository {
    val stream: zio.stream.Stream[Unit, Order]
  }

  case object OrderRepositoryMock extends OrderRepository {
    val stream = zio.stream.ZStream.repeat(
      Generator.order.next
    )
  }

  trait ProductRepository {
    def get(productId: ProductId): Option[Product]
  }

  case object ProductRepositoryMock extends ProductRepository {
    override def get(productId: ProductId): Option[Product] =
      Some(Generator.product.next)
  }

  object Writeside {

    case class State(state: Map[java.time.YearMonth, BigInt])
    object State { def empty = State(Map.empty) }

    type Pipeline[A, B] = ZStream[Any, Nothing, A] => ZStream[Any, Nothing, B]
    def apply: Pipeline[Order, State] = { stream =>
      (
        for {
          order <- stream
          minYearMonthOfOrder = order.items
            .map(_._2.productId)
            .map(ProductRepositoryMock.get)
            .collect { case Some(value) => value }
            .map(_.createdAt)
            .min
        } yield order.id -> minYearMonthOfOrder
      )
        .aggregateAsyncWithin(
          ZSink.fold[(OrderId, java.time.YearMonth), State](State.empty)(
            _.state.size < 1024
          ) { case (acc, tuple) =>
            acc.state.get(tuple._2) match {
              case Some(value) => State(acc.state + ((tuple._2, value + 1)))
              case None        => State(acc.state + ((tuple._2, 1)))
            }
          },
          Schedule.spaced(1.seconds)
        )
    }

  }

  object Readside {

    case class YearMonth(year: Int, month: Int)
    case class OrdersAmount(amount: BigInt)
    case class Proyection(state: Seq[(YearMonth, OrdersAmount)])

    sealed trait Classifications
    object Classifications {
      sealed trait Unused extends Classifications
      object Unused {
        case object `< 1` extends Unused
      }
      sealed trait InUse extends Classifications
      object InUse {
        case object `1-3` extends InUse
        case object `4-6` extends InUse
        case object `7-12` extends InUse
        case object `> 12` extends InUse
      }
      import Unused._
      import InUse._
      def apply: Int => Either[Unused, InUse] = {
        case m if m == 1 || m <= 3  => Right(`1-3`)
        case m if m == 4 || m <= 6  => Right(`4-6`)
        case m if m == 7 || m <= 12 => Right(`7-12`)
        case m if m > 12            => Right(`> 12`)
        case m if m < 1             => Left(`< 1`)
      }
    }

    // data transfer objects operations
    object Proyection { def empty = Proyection(Seq.empty) }
    object YearMonth {
      def apply(now: java.time.YearMonth): YearMonth =
        YearMonth(now.getYear, now.getMonthValue)
      def now = YearMonth(java.time.YearMonth.now())
    }
    implicit class YearMonthOps(yearMonth: YearMonth) {
      def <(other: YearMonth): Boolean =
        yearMonth.year < other.year ||
          (yearMonth.year == other.year &&
            yearMonth.month < other.month)

      def -(other: YearMonth): YearMonth = {
        val months = yearMonth.totalMonths - other.totalMonths
        YearMonth(
          months / 12,
          months % 12
        )
      }

      def totalMonths: Int = yearMonth.year * 12 + yearMonth.month
    }
  }

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    ZIO.never
  }

}
