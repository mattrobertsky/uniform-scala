package ltbs.uniform
package common.web

import cats.implicits._
import shapeless._, shapeless.labelled._
import cats.Monoid
import com.github.ghik.silencer.silent

trait InferFormField[Html] {

  val mon: Monoid[Html]

  type FF[A] = FormField[A, Html]

  implicit def hnilField = new FF[HNil] {

    def decode(out: Input): Either[ErrorTree,HNil] = Right(HNil)
    def encode(in: HNil): Input = Input.empty

    def render(
      key: List[String],
      breadcrumbs: Breadcrumbs,
      data: Input,
      errors: ErrorTree,
      messages: UniformMessages[Html]
    ): Html = mon.empty
  }

  implicit def hConsField[K <: Symbol, H, T <: HList](
    implicit
      witness: Witness.Aux[K],
    hField: Lazy[FF[H]],
    tField: FF[T]
  ): FF[FieldType[K,H] :: T] = new FF[FieldType[K,H] :: T] {
    val fieldName: String = witness.value.name

    def decode(out: Input): Either[ErrorTree,FieldType[K,H] :: T] = {
      (
        hField.value.decode(out / fieldName),
        tField.decode(out)
      ) match {
        case (Right(h), Right(t)) => Right((field[K](h) :: t))
        case (Left(he), Left(te)) =>
          val l = he.prefixWith(fieldName)
          Left(l |+| te)
        case (_,        Left(te)) => Left(te)
        case (Left(he), _)        => Left(he.prefixWith(fieldName))
      }
    }

    def encode(a: FieldType[K,H] :: T): Input = {
      val tailData: Input = tField.encode(a.tail)
      val headData: Input = hField.value.encode(a.head)
      tailData |+| headData.prefixWith(fieldName)
    }

    def render(
      key: List[String],
      breadcrumbs: Breadcrumbs,
      data: Input,
      errors: ErrorTree,
      messages: UniformMessages[Html]
    ): Html = mon.combine(
      hField.value.render(key :+ fieldName, breadcrumbs, data / fieldName, errors / fieldName, messages),
      tField.render(key, breadcrumbs, data, errors, messages)
    )
  }


  implicit def listCodec[A](implicit @silent subcodec: Codec[A]) = new Codec[List[A]]{
    import cats.data.Validated
    def decode(out: Input): Either[ErrorTree,List[A]] = {
      val s: List[Validated[ErrorTree,A]] =
        out.listSubtrees.sorted map { key: String =>
          subcodec.decode(out / key).leftMap{_.prefixWith(key)}.toValidated
        }
      s.sequence.toEither
    }

    def encode(in: List[A]): Input = in.zipWithIndex.foldLeft(Input.empty) {
      case (acc,(value, index)) => acc ++ subcodec.encode(value).prefixWith(index.toString)
    }
  }

  implicit def genericField[A, H, T](implicit
    @silent generic: LabelledGeneric.Aux[A,T],
    hlistInstance: Lazy[FF[T]],
    @silent("never used") notAnIterable: A <:!< Iterable[_]
  ): FF[A] = new FF[A] {
    val hlist = hlistInstance.value
    def decode(in: Input): Either[ErrorTree,A] =
      hlist.decode(in).map(generic.from)

    def encode(a:A): Input =
      hlist.encode(generic.to(a))

    def render(
      key: List[String],
      breadcrumbs: Breadcrumbs,
      data: Input,
      errors: ErrorTree,
      messages: UniformMessages[Html]
    ): Html = hlist.render(key, breadcrumbs, data, errors, messages)
  }

  // COPRODUCTS
  def selectionOfFields(
    inner: List[(String, (List[String], Breadcrumbs, Input, ErrorTree, UniformMessages[Html]) => Html)]
  )(
    key: List[String],
    breadcrumbs: Breadcrumbs,
    values: Input,
    errors: ErrorTree,
    messages: UniformMessages[Html]
  ): Html

  trait CoproductFieldList[A]{
    def decode(out: Input): Either[ErrorTree,A]
    def encode(in: A): Input
    val inner: List[(String, (List[String], Breadcrumbs, Input, ErrorTree, UniformMessages[Html]) => Html)]
  }

  implicit val cnilField: CoproductFieldList[CNil] = new CoproductFieldList[CNil]{

    override def decode(in: Input): Either[ErrorTree,CNil] =
      Left(ErrorMsg("required").toTree)
    override def encode(a: CNil): Input = Input.empty
    override val inner = List.empty
  }

  implicit def coproductFieldList[K <: Symbol, H, T <: Coproduct](
    implicit
      witness: Witness.Aux[K],
    hField: FF[H],
    tFields: CoproductFieldList[T]
  ): CoproductFieldList[FieldType[K, H] :+: T] = new CoproductFieldList[FieldType[K, H] :+: T] {
    val fname = witness.value.name
    val inner = (fname, hField.render _) :: tFields.inner

    def decode(in: Input): Either[ErrorTree,FieldType[K, H] :+: T] = {
      if (in.valueAtRoot.headOption == Some(List(fname))) {
        hField.decode(
          in / fname
        ).map{x => Inl(field[K]{x})} match {
          case Left(e) => Left(e.prefixWith(fname))
          case r@Right(_) => r
        }
      } else {
        tFields.decode(in).map(x => Inr(x))
      }
    }

    def encode(a: FieldType[K, H] :+: T): Input = a match {
      case Inl(l) =>
        hField.encode(l).prefixWith(fname) ++ Map(Nil -> List(fname))
      case Inr(r) => tFields.encode(r)
    }
  }

  implicit def coproductField[A](implicit coproductFields: CoproductFieldList[A]) =
    new FormField[A, Html] {
      def render(key: List[String], breadcrumbs: Breadcrumbs, values: Input, errors: ErrorTree, messages: UniformMessages[Html]): Html =
        selectionOfFields(coproductFields.inner)(key,breadcrumbs, values,errors,messages)

      def decode(out: Input): Either[ErrorTree,A] = coproductFields.decode(out)
      def encode(in: A): Input = coproductFields.encode(in)
    }
}
