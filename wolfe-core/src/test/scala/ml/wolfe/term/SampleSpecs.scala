package ml.wolfe.term

import ml.wolfe.WolfeSpec

import scala.util.Random

/**
 * @author riedel
 */
class SampleSpecs extends WolfeSpec {

  import ml.wolfe.term.TermImplicits._

  implicit val random = new Random(0)

  "A stochastic term" should {
    "generate sequential values" in {
      val r = sampleSequential(0 until 3)
      val e = r.evaluator()
      e.eval() should be (0)
      e.eval() should be (1)
      e.eval() should be (2)
      e.eval() should be (0)
    }

    "generate different values in different positions" in {
      val r = sampleUniform(0 until 100000)
      val t = r === r
      val e = t.evaluator()
      e.eval() should be (false)
    }

    "support memoization" in {
      val r = mem(sampleUniform(0 until 100000))
      val t = r === r
      val e = t.evaluator()
      e.eval() should be (true)
    }

    "sample from a sequence" in {
      val seq = fixedLengthSeq(Seq(false,true)).sampleSequential
      val e = seq.evaluator()
      e.eval() should be (false)
      e.eval() should be (true)
      e.eval() should be (false)
    }

    "sample to query a user value" in {
      def myValue(iTerm:IntTerm) = for (i <- iTerm) yield i % 2 == 0
      val r = myValue(sampleSequential(0 until 3))
      val e = r.evaluator()
      e.eval() should be (true)
      e.eval() should be (false)
      e.eval() should be (true)
    }

    "allow calculating stochastic gradients" in {
      val n = 2
      val x = Doubles.Var
      val w = fixedLengthSeq(0 until n)
      val i = sampleSequential(0 until n)
      val t = x * x * w(i)
      val d = t.differentiator(x)
      d.differentiate(2.0) should be (0.0)
      d.differentiate(2.0) should be (4.0)
      d.differentiate(2.0) should be (0.0)
    }

    "combine with first order sums" in {
      val n = 4
      implicit val I = Seqs(Ints,0,n)
      def indices(iTerm:IntTerm):I.Term = iTerm map (i => IndexedSeq() ++ (0 until i))
      val t = sum(indices(sampleSequential(1 until n))) { i => i:DoubleTerm}
      val e = t.evaluator()
      e.eval() should be (0.0)
      e.eval() should be (1.0)
      e.eval() should be (3.0)
      e.eval() should be (0.0)



    }

  }

}
