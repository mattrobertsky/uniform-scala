package ltbs.uniform

import org.scalatest._, flatspec.AnyFlatSpec, matchers.should.Matchers
import cats.implicits._
import cats.{Id, Monad}
import shapeless.{Id => _, _}
import scala.language.higherKinds
import validation.Rule

class MonoidInterpreter[SupportedTell <: HList, SupportedAsk <: HList](
  implicit askSummoner: TypeclassList[SupportedAsk, cats.Monoid]
) extends Language[Id, SupportedTell, SupportedAsk] {

  def interact[Tell, Ask](
    id: String,
    tell: Tell,
    default: Option[Ask] = None,
    validation: Rule[Ask] = Rule.alwaysPass[Ask],
    customContent: Map[String,(String,List[Any])] = Map.empty
  )(
    implicit
      selectorTell : IndexOf[SupportedTell, Tell],
    selectorAsk : IndexOf[SupportedAsk, Ask]
  ): Ask = askSummoner.forType[Ask].empty
}

class TestInterpreter extends AnyFlatSpec with Matchers {

  "A simple interpreter" should "be able to compose Id monad instances" in {

    type TellTypes = String :: Option[String] :: NilTypes
    type AskTypes = Int :: Option[String] :: NilTypes

    def program[F[_]: Monad](
      interpreter: Language[F, TellTypes, AskTypes]
    ): F[(Int, Option[String])] = {
      import interpreter._
      for {
        a <- interact[String,Int]("hiya", "in")
        b <- interact[String,Int]("hiya2", "in")
        c <- ask[Option[String]]("c")
        _ <- tell[Option[String]]("_", c)
      } yield ((a + b, c))
    }

    val result: (Int, Option[String]) = program(
      new MonoidInterpreter
    )

    result shouldBe ((0, None))
  }
}
