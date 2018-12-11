package ltbs.uniform.interpreters.playframework

import cats.Monoid
import cats.data._
import cats.implicits._
import org.atnos.eff._
import org.atnos.eff.all.{none => _, _}
import org.atnos.eff.syntax.all._
import play.api.data.Form
import play.twirl.api.Html
import scala.concurrent.{ ExecutionContext, Future }
import ltbs.uniform._
import play.api._
import play.api.mvc._
import org.atnos.eff.syntax.future._
import ltbs.uniform.datapipeline._

trait PlayInterpreter extends Compatibility.PlayController {

  implicit def convertMessages(implicit input: i18n.Messages): Messages = new Messages{
    def apply(key: List[String],args: Any*): String = input(key, args)
    def apply(key: String,args: Any*): String = input(key, args)
    def get(key: String,args: Any*): Option[String] = if (input.isDefinedAt(key))
      input.messages(key, args:_*).some
    else
      none[String]

    def list(key: String,args: Any*): List[String] = {
      @annotation.tailrec
      def inner(cnt: Int = 2, acc: List[String] = Nil): List[String] =
        get(s"$key.$cnt", args:_*) match {
          case Some(m) => inner(cnt+1, m :: acc)
          case None    => acc
        }

      List(key, s"$key.1").map(get(_, args)).flatten ++ inner().reverse
    }
  }

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
      render: (String, Input, ErrorTree, List[String]) => Html,
      decode: Encoded => Either[ErrorTree,B],
      encode: B => Encoded,
      validation: B => Either[ErrorTree,B],
      toTree: B => Input,
      bind: Input => Either[ErrorTree,B],
      unbind: B => Input
    ): Eff[S,B] ={

      for {
        request <- ask[S, Request[AnyContent]]
        targetId <- ask[S, String]
        method = request.method.toLowerCase
        state <- get[S, DB]
        dbrecord = state.get(id).map(decode(_).flatMap(validation))
        breadcrumbs <- get[S, List[String]]
        ret <- (method, dbrecord, targetId) match {
          case ("get", None, `id`) =>
            log.info("nothing in database, step in URI, render empty form")
            left[S, Result, B](Ok(render(id, Tree.empty, Tree.empty, breadcrumbs)))

          case ("get", Some(Right(o)), `id`) =>
            log.info("something in database, step in URI, user revisiting old page, render filled in form")
            log.info(s"Data: ${toTree(o)}")
            left[S, Result, B](Ok(render(id, Tree(Nil, Map(id -> toTree(o))), Tree.empty, breadcrumbs)))

          case ("get", Some(Right(data)), _) =>
            log.info("something in database, not step in URI, pass through")
            put[S, List[String]](id :: breadcrumbs) >>
              Eff.pure[S, B](data.asInstanceOf[B])

          case ("post", _, `id`) =>

            val inputText = encodeUrlString(
              request.body.asFormUrlEncoded.getOrElse(Map.empty)
            )

            val input: Input = formToInput(decodeUrlString(inputText))
            val data: Either[ErrorTree, B] = bind(input.children.getOrElse(targetId, Tree.empty)).flatMap(validation)

            data match {
              case Left(errors) =>
                log.info("form submitted, step in URI, validation failure")
                left[S, Result, B](BadRequest(render(id, input, errors, breadcrumbs)))
              case Right(o) => 
                log.info("form submitted, step in URI, validation pass")
                put[S, List[String]](id :: breadcrumbs) >>
                  put[S, DB](state + (id -> encode(o))) >>
                  Eff.pure[S, B](o)

            }
          case ("post", Some(_), _) if breadcrumbs.contains(targetId) =>
            log.info("something in database, previous page submitted")
            put[S, List[String]](id :: breadcrumbs) >>
              left[S, Result, B](Redirect(s"./$id"))

          case ("post", Some(Right(data)), _) =>
            log.info("something in database, posting, not step in URI nor previous page -> pass through")
            put[S, List[String]](id :: breadcrumbs) >>
              Eff.pure[S, B](data.asInstanceOf[B])

          case ("post", _, _) | ("get", _, _) =>
            log.info("nothing else seems applicable. maybe this should be a 404?")
            left[S, Result, B](Redirect(s"./$id"))
        }
      } yield ret
    }


    // def useSelectPage[C,U](
    //   wmFormC: WebMonadSelectPage[C]
    // )(
    //   implicit member: Member.Aux[UniformSelect[C,?], R, U],
    //   readStage: _readStage[U],
    //   readRequest: _readRequest[U],
    //   dbM: _db[U],
    //   breadcrumbsM: _breadcrumbs[U],
    //   futureM: _timedFuture[U],
    //   eitherM: _either[U]
    // ): Eff[U, A] = e.translate(
    //   new Translate[UniformSelect[C,?], U] {
    //     def apply[X](ax: UniformSelect[C,X]): Eff[U, X] = {
    //       val wmForm: WebMonadSelectPage[X] = wmFormC.imap(_.asInstanceOf[X])(_.asInstanceOf[C])

    //       ax match {
    //         case a: UniformSelectOne[U,X] =>
    //           val i: Eff[U,X] = pageLogic[U,X](
    //             a.key,
    //             wmForm.renderOne(_,a.options, _, _),
    //             wmForm.decode,
    //             wmForm.encode,
    //             wmForm.playFormOne(a.key, a.validation(_))
    //           )
    //           i

    //         case a: UniformSelectMany[U,X] =>
    //           val i: Eff[U,Set[X]] = pageLogic[U,Set[X]](
    //             a.key,
    //             wmForm.renderMany(_,a.options, _, _),
    //             (x => x.split(",").filter(_.nonEmpty).map(wmForm.decode).toSet),
    //             (x => x.map(wmForm.encode).mkString(",")),
    //             wmForm.playFormMany(a.key, a.validation(_))
    //           )
    //           i
    //       }
    //     }
    //   }
    // )


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
                validation.map{_.toEither.leftMap(Tree(_))},
                wmForm.toTree,
                wmForm.bind,
                wmForm.unbind
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