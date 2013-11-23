package scalapplcodefest

import scala.language.implicitConversions
import scalapplcodefest.Math.UnitVec

/**
 * A set of methods to convert one term into another.
 *
 * @author Sebastian Riedel
 */
object TermConverter {

  import TermImplicits._

  trait Converter {
    def convert[T](term: Term[T]): Term[T]
  }

  /**
   * This method converts the term tree in a depth-first fashion. That is, when
   * encountering a term the term's children are converted, a new term with these converted children is created,
   * and then this new term is converted.
   * @param term the term to convert.
   * @param converter a converter that takes an individual term and converts it (no traversal).
   * @tparam T the type of term.
   * @return the converted term.
   */
  def convertDepthFirst[T](term: Term[T], converter: Converter): Term[T] = {
    implicit def cast(t: Term[Any]) = t.asInstanceOf[Term[T]]
    term match {
      case FunTermProxy(t) => converter.convert(FunTermProxy(convertDepthFirst(t, converter)))
      case TupleTerm2(a1, a2) => converter.convert(TupleTerm2(convertDepthFirst(a1, converter), convertDepthFirst(a2, converter)))
      case FunApp(f, a) => converter.convert(FunApp(FunTerm(convertDepthFirst(f, converter)), convertDepthFirst(a, converter)))
      case Conditioned(t, c) => converter.convert(Conditioned(convertDepthFirst(t, converter), c))
      case SeqTerm(args) => converter.convert(SeqTerm(args.map(convertDepthFirst(_, converter))))
      case ImageSeq1(f) => converter.convert(ImageSeq1(FunTerm(convertDepthFirst(f, converter))))
      case ImageSeq2(f) => converter.convert(ImageSeq2(FunTerm(convertDepthFirst(f, converter))))
      case LambdaAbstraction(Var(v, d), t) => converter.convert(LambdaAbstraction(Var(v, convertDepthFirst(d, converter)), convertDepthFirst(t, converter)))
      case Var(v, d) => converter.convert(Var(v, convertDepthFirst(d, converter)))
      case Reduce(o, a) => converter.convert(Reduce(FunTerm(convertDepthFirst(o, converter)).asInstanceOf[FunTerm[(T, T), T]], convertDepthFirst(a, converter)))
      case LinearModel(f, Var(w, d), b) => converter.convert(LinearModel(convertDepthFirst(f, converter), Var(w, convertDepthFirst(d, converter)), convertDepthFirst(b, converter)))
      case UnitVec(i, v) => converter.convert(UnitVec(convertDepthFirst(i, converter), convertDepthFirst(v, converter)))
      case _ => converter.convert(term)
    }
  }

  /**
   * Converts a term by replacing each occurrence of a sub-term with another term.
   * @param term the term in which the replacement should happen
   * @param toReplace the inner term that should be replaced.
   * @param replacement the new term to use instead.
   * @tparam A type parameter of root term to change.
   * @tparam B type parameter of term to replace.
   * @return the original term with all occurrences of `toReplace` replaced by `replacement`.
   */
  def substituteVariable[A, B](term: Term[A], toReplace: Term[B], replacement: Term[B]) =
    convertDepthFirst(term, new Converter {
      def convert[T](term: Term[T]) = if (term == toReplace) replacement.asInstanceOf[Term[T]] else term
    })

  def distConds[T](term: Term[T]): Term[T] = {
    implicit def cast(t: Term[Any]) = t.asInstanceOf[Term[T]]
    term match {
      case Conditioned(const@Constant(_), _) => const
      case Conditioned(LinearModel(feats, weights, base), c) => LinearModel(distConds(feats | c), weights, distConds(base | c))
      case Conditioned(Math.VecAdd.Reduced(args), c) => vsum(distConds(args | c))
      case Conditioned(Math.VecAdd.Applied2(arg1, arg2), c) => distConds(arg1 | c) + distConds(arg2 | c)
      case Conditioned(Math.Dot.Applied2(arg1, arg2), c) => distConds(arg1 | c) dot distConds(arg2 | c)
      case Conditioned(Math.DoubleAdd.Reduced(args), c) => dsum(distConds(args | c))
      case Conditioned(Math.DoubleAdd.Applied2(arg1, arg2), c) => distConds(arg1 | c) + distConds(arg2 | c)
      case Conditioned(SeqTerm(args), c) => SeqTerm(args.map(a => distConds(a | c)))
      case Conditioned(ImageSeq1(LambdaAbstraction(Var(v, d), arg)), c) => ImageSeq1(LambdaAbstraction(Var(v, distConds(d | c)), distConds(arg | c)))
      case _ => term
    }
  }

  def flattenDoubleSums(term: Term[Double]): Seq[Term[Double]] = {
    val raw = term match {
      case Math.DoubleAdd.Reduced(SeqTerm(args)) => args.flatMap(flattenDoubleSums)
      case Math.DoubleAdd.Applied2(arg1, arg2) => flattenDoubleSums(arg1) ++ flattenDoubleSums(arg2)
      case _ => Seq(term)
    }
    raw.filter(_ != Constant(0.0))
  }

  def flatten[T, O](term: Term[T], op: BinaryOperatorSameDomainAndRange[O]): Term[T] = convertDepthFirst(term, new Converter {
    def convert[A](arg: Term[A]) = {
      implicit def cast(t: Term[Any]) = t.asInstanceOf[Term[A]]
      arg match {
        case op.Reduced(SeqTerm(args)) =>
          val inner = args.collect({
            case op.Reduced(SeqTerm(innerArgs)) => innerArgs
            case t => Seq(t)
          })
          val flattened = inner.flatMap(identity)
          Reduce(op.Term, SeqTerm(flattened))
        case op.Applied2(op.Reduced(k@SeqTerm(args1)), op.Reduced(SeqTerm(args2))) =>
          Reduce(op.Term, SeqTerm(args1 ++ args2))
        case op.Applied2(op.Reduced(SeqTerm(args1)), arg2) =>
          Reduce(op.Term, SeqTerm(args1 :+ arg2))
        case op.Applied2(arg1, op.Reduced(SeqTerm(args2))) =>
          Reduce(op.Term, SeqTerm(arg1 +: args2))
        case op.Applied2(arg1, arg2) =>
          Reduce(op.Term, SeqTerm(Seq(arg1, arg2)))
        case _ => arg

      }
    }
  })


  def unrollLambdaAbstractions2[T](term: Term[Seq[T]]): Term[Seq[T]] = term match {
    case ImageSeq1(fun@LambdaAbstraction(v, arg)) => SeqTerm(State.allStates(List(v)).map(state => fun(arg | state)))
    case _ => term
  }

  def unrollLambdas[T](term: Term[T]): Term[T] = {
    implicit def cast(t: Term[Any]) = t.asInstanceOf[Term[T]]
    term match {
      case ImageSeq1(fun@LambdaAbstraction(v, arg)) => SeqTerm(State.allStates(List(v)).map(state => arg | state))
      case r@Reduce(op, args) => r.copy(op = unrollLambdas(op).asInstanceOf[FunTerm[(T, T), T]], arguments = unrollLambdas(args))
      case app@FunApp(f, a) => app.copy(function = FunTerm(unrollLambdas(f)), arg = unrollLambdas(a))
      case s@SeqTerm(args) => s.copy(seq = args.map(unrollLambdas)) //red in IDEA
      case t@TupleTerm2(arg1, arg2) => t.copy(a1 = unrollLambdas(arg1), a2 = unrollLambdas(arg2)) //red in IDEA
      case _ => term
    }
  }

  def distDots(term: Term[Double]): Term[Double] = {
    term match {
      case Conditioned(t, c) => Conditioned(distDots(t), c)
      case LinearModel(f, w, base) => distDots(f dot w) + base
      case Math.Dot.Applied2(Math.VecAdd.Applied2(f1, f2), w) => distDots(f1 dot w) + distDots(f2 dot w)
      case Math.Dot.Applied2(Math.VecAdd.Reduced(SeqTerm(args)), w) => dsum(SeqTerm(args.map(a => distDots(a dot w))))
      case Math.Dot.Applied2(Quantified.VecSum(ImageSeq1(LambdaAbstraction(v, arg))), w) =>
        dsum(LambdaAbstraction(v, distDots(arg dot w)))
      case _ => term
    }
  }

  def simplifyConds[T](term: Term[T]): Term[T] = {
    term match {
      case Conditioned(Conditioned(t, c1), c2) => Conditioned(t, c1 + c2)
      case app@FunApp(f, a) => app.copy(FunTerm(simplifyConds(f)), simplifyConds(a))
      case _ => term
    }
  }


  def groupLambdasDeep[T](term: Term[T]) = convertDepthFirst(term, new Converter {
    def convert[A](arg: Term[A]) = groupLambdas(arg)
  })

  def groupLambdas[T](term: Term[T]): Term[T] = {
    implicit def cast(t: Term[Any]) = t.asInstanceOf[Term[T]]

    //merge a term with one element in a list of previous terms if possible, otherwise prepend
    def mergeOneTerm[T](current: List[Term[T]], toMerge: Term[T], op: BinaryOperatorSameDomainAndRange[T]) = {
      val first = current.view.map(that => that -> mergeLambdas(that, toMerge, op)).find(_._2.isDefined)
      first match {
        case Some((orig, Some(mergedTerm))) => current.map(t => if (t == orig) mergedTerm else orig)
        case _ => toMerge :: current
      }
    }
    term match {
      case Math.VecAdd.Reduced(SeqTerm(args)) =>
        val merged = args.foldLeft(List.empty[Term[Vector]])(mergeOneTerm(_, _, Math.VecAdd))
        vsum(SeqTerm(merged))
      case Math.DoubleAdd.Reduced(SeqTerm(args)) =>
        val merged = args.foldLeft(List.empty[Term[Double]])(mergeOneTerm(_, _, Math.DoubleAdd))
        dsum(SeqTerm(merged))
      case _ => term
    }
  }

  def mergeLambdas[T](t1: Term[T], t2: Term[T],
                      operator: BinaryOperatorSameDomainAndRange[T],
                      condition: State = State.empty): Option[Term[T]] = {

    def mergeable(replaced1: Term[T], replaced2: Term[T], newCondition: State) = {
      val conditioned1 = replaced1 | newCondition
      val conditioned2 = replaced2 | newCondition
      val vars1 = conditioned1.variables
      val vars2 = conditioned2.variables
      vars1 == vars2
    }

    (t1, t2) match {
      case (operator.Reduced(ImageSeq1(LambdaAbstraction(v1, a1))), operator.Reduced(ImageSeq1(LambdaAbstraction(v2, a2)))) =>
        val default = v1.default
        val replaced2 = substituteVariable(a2, v2, v1)
        val newCondition = condition + SingletonState(v1, default)
        if (mergeable(a1, replaced2, newCondition)) {
          Some(Reduce(operator.Term, ImageSeq1(LambdaAbstraction(v1, FunApp(operator.Term, TupleTerm2(a1, replaced2))))))
        } else
          None
      case (operator.Reduced(ImageSeq2(LambdaAbstraction(v1, LambdaAbstraction(v1_2, a1)))), operator.Reduced(ImageSeq2(LambdaAbstraction(v2, LambdaAbstraction(v2_2, a2))))) =>
        val default = v1.default
        val default_2 = v1_2.default
        val replaced2 = substituteVariable(substituteVariable(a2, v2, v1), v2_2, v1_2)
        val newCondition = condition + State(Map(v1 -> default, v1_2 -> default_2))
        if (mergeable(a1, replaced2, newCondition)) {
          val inner = LambdaAbstraction(v1_2, FunApp(operator.Term, TupleTerm2(a1, replaced2)))
          val outer = LambdaAbstraction(v1, inner)
          Some(Reduce(operator.Term, ImageSeq2(outer)))
        } else
          None

    }


  }

  def main(args: Array[String]) {

    val r = 'r of (0 ~~ 2 |-> Doubles)
    val l1 = dsum(for (i <- 0 ~~ 2) yield r(i))
    val l2 = dsum(for (j <- 0 ~~ 2) yield r(j))

    val s = 's of ((0 ~~ 2) x (0 ~~ 2)) |-> Doubles

    val sum = dsum(SeqTerm(Seq(l1, l2)))

    val merged = mergeLambdas(l1, l2, Math.DoubleAdd)

    println(merged)

    val grouped = groupLambdas(sum)

    println(grouped)

    val f1 = dsum(for (i <- 0 ~~ 2; j <- 0 ~~ 2) yield s(i, j))
    val f2 = dsum(for (k <- 0 ~~ 2; l <- 0 ~~ 2) yield s(k, l))
    val grouped2 = groupLambdas(flatten(f1 + f2, Math.DoubleAdd))
    println(grouped2)


  }


}


