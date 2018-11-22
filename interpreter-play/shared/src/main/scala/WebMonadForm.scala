package ltbs.uniform.interpreters.playframework

import cats.data.Validated
import play.twirl.api.Html
import play.api.data.Form
import play.api.mvc.{ Request, AnyContent }

trait WebMonadForm[T] {
  def render(key: String, existing: ValidatedData[T], request: Request[AnyContent]): Html
  def encode(in: T): Encoded
  def decode(out: Encoded): T
  def playForm(key: String, validation: T => Validated[ValidationError, T]): Form[T]
}

trait WebMonadSelectPage[T] {
  def toHtml(in: T): Html
  def render(key: String, options: Set[T], existing: ValidatedData[T], request: Request[AnyContent]): Html
  def encode(in: T): Encoded
  def decode(out: Encoded): T
  def playForm(key: String, validation: T => Validated[ValidationError, T]): Form[T]
}
