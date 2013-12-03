package scalapplcodefest

/**
 * An inference made by some algorithm. This interfaces supports both marginal inference
 * and MAP inference.
 * @author Sebastian Riedel
 */
trait Inference {
  /**
   * The state corresponding to this inference. For example, this can be the most likely state, or the
   * state maximizing the per-variable marginal probabilities.
   * @return the state corresponding to the inference made.
   */
  def state(): State

  /**
   * An objective associated with the inference. Can be the log-linear score, or a log partition function etc.
   * @return objective associated with inference.
   */
  def obj(): Double

  /**
   * A feature vector associated with the inference. Can be the expectation of the feature function
   * under the model, or the feature vector of the argmax state.
   * @return feature representation associated with the inference.
   */
  def feats(): Vector

}

trait MutableInference extends Inference {
  /**
   * Change the weights and update the inference.
   * @param newWeights new weight vector to use.
   */
  def updateResult(newWeights: DenseVector)
}

object Inference {


  def exhaustiveArgmax(term:Term[Double]):Inference = {
    val argmaxState = State.allStates(term.variables.toList).view.maxBy(term.eval(_).get)
    val featTerm = term match {
      case Linear(feats,_,_) => feats
      case Conditioned(Linear(feats,_,_),_) => feats
      case _ => Constant(new SparseVector(0))
    }
    new Inference {
      def state() = argmaxState
      def obj() = term.eval(argmaxState).get
      def feats() = featTerm.eval(argmaxState).get
    }
  }
  

  def maxProductArgmax(maxIterations:Int,
                        hiddenVarHint:Set[Variable[Any]] = AllVariables)(term:Term[Double]):Inference = {

    import TermConverter._

    //find a linear model in the term and identify the weight vector variable
    val (weightVar,inner, weights) = term match {
      case Conditioned(l@Linear(_,w,_),s) if s.domain(w) => (w,l,s(w))
      case Conditioned(withInstance@Conditioned(Linear(_,w,_),_),s) if s.domain(w) => (w,withInstance,s(w))
      case _ => (null,term,null)
    }

    //bring the linear model into a flat and grouped form
    val normalized = normalizeLinearModel(inner,hiddenVarHint)

    //push down conditions and dot products
    val pushed = pushDownDotProducts(pushDownConditions(normalized))

    //unroll lambda abstractions and bracket them
    val unrolled = unrollLambdaImages(pushed,t => Bracketed(t))

    //flatten to get simple sum of double terms
    val flat = flatten(unrolled,Math.DoubleAdd)

    //now remove brackets around unrolled terms
    val unbracketed = unbracket(flat)


    println(unbracketed)

    val aligned = MessagePassingGraphBuilder.build(unbracketed, weightVar)
    aligned.graph.weights = weights.asInstanceOf[DenseVector]
    //println(aligned.graph.toVerboseString(new aligned.FGPrinter(ChunkingExample.key)))

    MaxProduct.run(aligned.graph, maxIterations)

    val argmaxState = aligned.argmaxState()
    val argmaxFeats = new SparseVector(10)
    val argmaxScore = MaxProduct.featureExpectationsAndObjective(aligned.graph,argmaxFeats)

    new Inference {
      def state() = argmaxState
      def obj() = argmaxScore
      def feats() = argmaxFeats
    }

  }


}