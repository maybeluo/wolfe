package ml.wolfe.term

import ml.wolfe.term

/**
 * @author riedel
 */
class Tuple2Dom[D1 <: Dom, D2 <: Dom](val dom1: D1, val dom2: D2) extends Dom {
  dom =>
  type Value = (dom1.Value, dom2.Value)
  type Var = DomVar
  type Term = DomTerm
  type Marginals = (dom1.Marginals, dom2.Marginals)

  val lengths = dom1.lengths + dom2.lengths
  def toValue(setting: Setting, offsets: Offsets = Offsets()) = {
    val arg1 = dom1.toValue(setting, offsets)
    val arg2 = dom2.toValue(setting, offsets + dom1.lengths)
    (arg1, arg2)
  }

  def toMarginals(msg: Msgs, offsets: Offsets) = {
    val arg1 = dom1.toMarginals(msg, offsets)
    val arg2 = dom2.toMarginals(msg, offsets + dom1.lengths)
    (arg1, arg2)
  }
  def copyValue(value: Value, setting: Setting, offsets: Offsets = Offsets()): Unit = {
    dom1.copyValue(value._1, setting, offsets)
    dom2.copyValue(value._2, setting, offsets + dom1.lengths)
  }

  def copyMarginals(marginals: Marginals, msgs:Msgs, offsets: Offsets = Offsets()): Unit = {
    dom1.copyMarginals(marginals._1, msgs, offsets)
    dom2.copyMarginals(marginals._2, msgs, offsets + dom1.lengths)
  }

  def fillZeroMsgs(target: Msgs, offsets: Offsets) = {
    dom1.fillZeroMsgs(target, offsets)
    dom2.fillZeroMsgs(target, offsets + dom1.lengths)
  }

  def variable(name: String, offsets: Offsets, owner: term.Var[Dom]): DomVar =
    StaticTuple2Var(name, offsets, owner)

  def dynamic(name: => String, dynOffsets: => Offsets, owner: term.Var[Dom]): DomVar = new BaseVar(name, owner) with DomVar {
    def offsets = dynOffsets
    val var1: domain.dom1.Var = domain.dom1.dynamic(name + "._1", offsets, if (owner == null) this else owner)
    val var2: domain.dom2.Var = domain.dom2.dynamic(name + "._2", offsets + domain.dom1.lengths, if (owner == null) this else owner)
  }
  def one = (dom1.one, dom2.one)
  def zero = (dom1.zero, dom2.zero)

  def const(value: Value): DomTerm = new Tuple2DomTermImpl {
    val _1 = domain.dom1.const(value._1)
    val _2 = domain.dom2.const(value._2)
  }

  trait DomTerm extends super.DomTerm {
    def _1: domain.dom1.Term
    def _2: domain.dom2.Term
  }

  trait Tuple2DomTermImpl extends DomTerm with Composed[dom.type] {
    def arguments = IndexedSeq(_1, _2)

    def composer() = ???

    def differentiator(wrt: Seq[term.Var[Dom]]) = ???
  }

  trait DomVar extends super.DomVar with DomTerm {
    def offsets: Offsets
    def ranges = Ranges(offsets, offsets + domain.dom1.lengths + domain.dom2.lengths)
    def atoms = _1.atoms ++ _2.atoms

    def var1: domain.dom1.Var
    def var2: domain.dom2.Var

    def _1 = var1
    def _2 = var2

  }

  case class StaticTuple2Var(name: String,
                             offsets: Offsets,
                             owner: term.Var[Dom]) extends DomVar {
    override val ranges = super.ranges
    val var1 = domain.dom1.variable(name + "._1", offsets, if (owner == null) this else owner)
    val var2 = domain.dom2.variable(name + "._2", offsets + domain.dom1.lengths, if (owner == null) this else owner)
  }

}

trait ProductDom extends Dom {
  dom =>

  trait DomTermImpl extends super.DomTerm with Composed[dom.type] {
    def composer() = ???

    def differentiator(wrt: Seq[term.Var[Dom]]) = ???

  }
}