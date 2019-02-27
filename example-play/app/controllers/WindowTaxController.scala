package controllers

import cats.implicits._
import cats.kernel.Monoid
import javax.inject._
import ltbs.uniform._
import ltbs.uniform.web._
import ltbs.uniform.web.parser._
import ltbs.uniform.interpreters.playframework._
import ltbs.uniform.sampleprograms.WindowTax2._
import ltbs.uniform.widgets.govuk._
import org.atnos.eff._
import play.api._
import play.api.i18n.{Messages => _, _}
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import play.twirl.api.Html

import InferParser._

@Singleton
class WindowTaxController @Inject()(
  implicit val messagesApi: MessagesApi
) extends Controller with PlayInterpreter with I18nSupport {

  def messages(request: Request[AnyContent]): Messages =
    BestGuessMessages(convertMessages(messagesApi.preferred(request)))

  def renderForm(
    key: List[String],
    errors: ErrorTree,
    form: Html,
    breadcrumbs: List[String],
    request: Request[AnyContent],
    messagesIn: Messages
  ): Html = {
    views.html.chrome(key.last, errors, form, breadcrumbs)(messagesIn, request)
  }

  def listingPage[A](
    key: List[String],
    errors: ErrorTree,
    elements: List[A],
    messages: Messages
  )(implicit evidence$1: Htmlable[A]): Html = ???

  val persistence = new Persistence {
    private var data: DB = Monoid[DB].empty
    def dataGet: Future[DB] = Future.successful(data)
    def dataPut(dataIn: DB): Future[Unit] =
      Future(data = dataIn).map{_ => ()}
  }

  def daylightRobbery(key: String) = {

    implicit val keys: List[String] = key.split("/").toList

    type STACKZ = FxAppend[
      Fx.fx7[
        UniformAsk[List[Window],?],
        Uniform[List[Window],ListControl,?],
        UniformAsk[Int,?],
        UniformAsk[(Int,Int),?],
        UniformAsk[Orientation,?],
        UniformAsk[Boolean,?],
        Uniform[Window,Boolean,?]
      ],
      PlayStack
    ]

    type STACKY = Fx.fx6[
      cats.data.State[UniformCore,?],
      UniformAsk[Int,?],
      UniformAsk[(Int,Int),?],
      UniformAsk[Orientation,?],
      UniformAsk[Boolean,?],
      Uniform[Window,Boolean,?]
    ]


    def fr(i:Window): Html = Html(i.toString)

    def delistSub[S: _uniform[Unit,Window,?] : _uniformCore](
      key: String,
      existing: List[Window],
      default: Option[Window]
    ): Eff[S,Window] = ask[Window](s"add")

    implicit val listControlHtmlField = jdkListControlHtmlField

    Action.async { implicit request =>

      val csrf = _root_.views.html.helper.CSRF.formField
      def fu3(messages: Messages): List[Window] => Html = listingTable(csrf)(
        "windows",
        {ltbs.uniform.widgets.govuk.html.listing(_,_,_,_,_)},
        fr,
        messages
      )(_)

      implicit def renderTellUnit: (Unit, String) => Html = {case _ => Html("")}
      implicit def renderTellWindows: (List[Window], String) => Html =
        {case (v,_) => fu3(messages(request))(v) }
      implicit def renderTellWindow: (Window, String) => Html =
        {case (v,_) => fr(v) }

      runWeb(
        program = program[STACKZ]
          .delist{
            (existing: List[Window], default: Option[Window]) =>
            singleWindowProgram[STACKY](existing,default)
          }
          .useForm(PlayForm.automatic[List[Window],ListControl])
          .useForm(PlayForm.automatic[Unit, Int])
          .useForm(PlayForm.automatic[Unit, (Int,Int)])
          .useForm(PlayForm.automatic[Unit, Orientation])
          .useForm(PlayForm.automatic[Unit, Boolean])
          .useForm(PlayForm.automatic[Window, Boolean]),
        persistence
      )(
        a => Future.successful(Ok(s"You have £$a to pay"))
      )
    }
  }

}