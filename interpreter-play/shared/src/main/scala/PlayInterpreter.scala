package ltbs.uniform.interpreters.playframework

import cats.Monoid
import cats.data._
import cats.implicits._
import org.atnos.eff._
import org.atnos.eff.all._
import org.atnos.eff.syntax.all._
import play.api.data.Form
import play.twirl.api.Html
import scala.concurrent.{ ExecutionContext, Future }
import ltbs.uniform._
import play.api._
import play.api.mvc._
import org.atnos.eff.syntax.future._

trait PlayInterpreter extends Compatibility.PlayController {

  val log: Logger = Logger("uniform")

  def formToValidated[A](f: Form[A]): ValidatedData[A] =
    if (!f.hasErrors) f.value.map{_.valid}
    else Some(f.errors.head.message.invalid)

  type PlayStack = Fx.fx6[Reader[String, ?], Reader[Request[AnyContent], ?], State[DB, ?], State[List[String],?], Either[Result, ?], TimedFuture]

  implicit class PlayEffectOps[R, A](e: Eff[R, A]) {
    type _readStage[Q] = Reader[String,?] |= Q
    type _readRequest[Q] = Reader[Request[AnyContent],?] |= Q
    type _db[Q]  = State[DB,?] |= Q
    type _breadcrumbs[Q]  = State[List[String],?] |= Q
    type _timedFuture[Q]  = TimedFuture[?] |= Q
    type _either[Q] = Either[Result,?] |= Q

    private def pageLogic[S: _readRequest: _readStage: _db: _breadcrumbs: _timedFuture: _either,B](
      id: String,
      render: (String, Option[Validated[ValidationError, B]], Request[AnyContent]) => Html,
      decode: Encoded => B,
      encode: B => Encoded,
      form: Form[B]
    ): Eff[S,B] ={

      object ValidDecode {
        def unapply(in: Option[Encoded]): Option[B] = in.map(decode)
      }

      for {
        request <- ask[S, Request[AnyContent]]
        targetId <- ask[S, String]
        method = request.method.toLowerCase
        state <- get[S, DB]
        data = state.get(id)
        breadcrumbs <- get[S, List[String]]
        ret <- (method, data, targetId) match {

          case ("get", None, `id`) =>
            log.info("nothing in database, step in URI, render empty form")
            left[S, Result, B](Ok(render(id, None, request)))

          case ("get", ValidDecode(data), `id`) =>
            log.info("something in database, step in URI, user revisiting old page, render filled in form")
            left[S, Result, B](Ok(render(id, Some(data.valid[ValidationError]), request)))

          // TODO: Check validation too
          case ("get", ValidDecode(data), _) =>
            log.info("something in database, not step in URI, pass through")
            put[S, List[String]](id :: breadcrumbs) >>
              Eff.pure[S, B](data.asInstanceOf[B])

          case ("post", _, `id`) =>
            form
              .bindFromRequest()(request).fold(

                formWithErrors => {
                  log.info("form submitted, step in URI, validation failure")
                  left[S, Result, B](BadRequest(render(id, formToValidated(formWithErrors), request)))
                },

                formData => {
                  log.info("form submitted, step in URI, validation pass")
                  put[S, List[String]](id :: breadcrumbs) >>
                    put[S, DB](state + (id -> encode(formData))) >>
                    Eff.pure[S, B](formData)
                }
              )

          case ("post", Some(_), _) if breadcrumbs.contains(targetId) =>
            log.info("something in database, previous page submitted")
            put[S, List[String]](id :: breadcrumbs) >>
              left[S, Result, B](Redirect(s"./$id"))

          // TODO: Check validation too
          case ("post", ValidDecode(data), _) =>
            log.info("something in database, posting, not step in URI nor previous page -> pass through")
            put[S, List[String]](id :: breadcrumbs) >>
              Eff.pure[S, B](data.asInstanceOf[B])

          case ("post", _, _) | ("get", _, _) =>
            log.info("nothing else seems applicable. maybe this should be a 404?")
            left[S, Result, B](Redirect(s"./$id"))
        }
      } yield ret
    }


    def useSelectPage[C,U](
      wmFormC: WebMonadSelectPage[C]
    )(
      implicit member: Member.Aux[UniformSelect[C,?], R, U],
      readStage: _readStage[U],
      readRequest: _readRequest[U],
      dbM: _db[U],
      breadcrumbsM: _breadcrumbs[U],
      futureM: _timedFuture[U],
      eitherM: _either[U]
    ): Eff[U, A] = e.translate(
      new Translate[UniformSelect[C,?], U] {
        def apply[X](ax: UniformSelect[C,X]): Eff[U, X] = {
          val wmForm: WebMonadSelectPage[X] = wmFormC.imap(_.asInstanceOf[X])(_.asInstanceOf[C])

          val options: Set[X] = ax match {
            case a: UniformSelectOne[U,X] => a.options
            case a: UniformSelectMany[U,X] => a.options
          }

          (ax.key, ax.validation) match {
            case (id, validation) =>

              val i: Eff[U,X] = pageLogic[U,X](
                id,
                wmForm.render(_,options, _, _),
                wmForm.decode,
                wmForm.encode,
                wmForm.playForm(id, validation(_))
              )
              i
          }
        }
      }
    )


    def useForm[C, U](
      wmFormC: WebMonadForm[C]
    )(
      implicit member: Member.Aux[UniformAsk[C,?], R, U],
      readStage: _readStage[U],
      readRequest: _readRequest[U],
      dbM: _db[U],
      breadcrumbsM: _breadcrumbs[U],
      futureM: _timedFuture[U],
      eitherM: _either[U]
    ): Eff[U, A] = e.translate(
      new Translate[UniformAsk[C,?], U] {
        def apply[X](ax: UniformAsk[C,X]): Eff[U, X] = {
          val wmForm: WebMonadForm[X] = wmFormC.imap(_.asInstanceOf[X])(_.asInstanceOf[C])

          (ax.key, ax.validation) match {
            case (id, validation) =>

              val i: Eff[U,X] = pageLogic[U,X](
                id,
                wmForm.render,
                wmForm.decode,
                wmForm.encode,
                wmForm.playForm(id, validation(_))
              )
              i
          }
        }
      }
    )
  }

  implicit val scheduler = ExecutorServices.schedulerFromGlobalExecutionContext

  def runWeb[A](
    program: Eff[PlayStack, A],
    key: String,
    request: Request[AnyContent],
    persistence: Persistence,
    purgeJourneyOnCompletion: Boolean = true
  )(
    terminalFold: A => Future[Result]
  )(implicit ec: ExecutionContext): Future[Result] =
    persistence.dataGet >>= {
      data => program.runReader(key)
        .runReader(request)
        .runEither
        .runState(data)
        .runState(List.empty[String])
        .runSequential
    } >>= {
      _ match {
        case ((Left(result), db), _) =>
          persistence.dataPut(db).map(_ => result)
        case ((Right(a), db), _) =>
          val newDb: DB = if (purgeJourneyOnCompletion) (Monoid[DB].empty) else db
          persistence.dataPut(newDb) >> terminalFold(a)
      }
    }
}
