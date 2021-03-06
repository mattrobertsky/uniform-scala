package ltbs.uniform
package common.web

import cats.implicits._
import cats.~>
import concurrent._

case class FutureAdapter[Html]() {

  type WM[A] = WebMonad[A, Html]

  def alwaysRerun = new ~>[Future, WM] {
    def apply[A](fa: Future[A]): WM[A] = new WM[A] {
      def apply(pageIn: PageIn)(implicit ec: ExecutionContext): Future[PageOut[A,Html]] =
        fa.map{ x => pageIn.toPageOut(AskResult.Success[A,Html](x)) }
    }
  }

  def rerunOnPriorStateChange(cacheId: String) = new {

    def sha256Hash(in: String): String = {
      import java.security.MessageDigest
      import java.math.BigInteger
      val bytes = in.getBytes("UTF-8")
      val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
      String.format("%032x", new BigInteger(1, digest))
    }

    def apply[A](fa: Future[A])(implicit codec: Codec[A]): WM[A] = new WM[A] {

      def apply(pageIn: PageIn)(implicit ec: ExecutionContext): Future[PageOut[A,Html]] = {
        import pageIn._
        val triggerValues: List[String] = breadcrumbs.sorted.flatMap{ state.get }
        val trigger: String = sha256Hash(triggerValues.mkString)
        val oldTrigger: Option[String] = state.get(List(cacheId, "trigger"))

        if (oldTrigger == Some(trigger)) {
          val oldValue: Either[ErrorTree,A] =
            Input.fromUrlEncodedString(state(List(cacheId, "value"))) >>= codec.decode

          val Right(oldie) = oldValue
          pageIn.toPageOut(AskResult.Success[A, Html](oldie)).pure[Future]
        } else {
          fa.map{ result =>
            val newData = Map(
              List(cacheId, "value") -> codec.encode(result).toUrlEncodedString,
              List(cacheId, "trigger") -> trigger
            )
            pageIn.toPageOut(AskResult.Success[A, Html](result)).copy (
              db = pageIn.state ++ newData
            )
          }
        }
      }
    }
  }
}
