package ml.wolfe.apps.factorization

import java.io.{OutputStream, File}

import cc.factorie.la.DenseTensor1
import cc.factorie.optimize._
import cc.factorie.util.{Logger, FastLogging}
import ml.wolfe.FactorGraph.Edge
import ml.wolfe.apps.{ImplNeg, Impl, TensorKB}
import ml.wolfe.apps.factorization.io.{EvaluateNAACL, LoadNAACL, WriteNAACL}
import ml.wolfe.fg.L2Regularization
import ml.wolfe.fg._
import ml.wolfe.util.{ProgressLogging, ProgressBar, Timer}
import ml.wolfe.{DenseVector, FactorieVector, GradientBasedOptimizer, Wolfe}

import scala.util.Random

/**
 * @author Sebastian Riedel
 */
object MatrixFactorization extends App {
  //implicit val conf = ConfigFactory.parseFile(new File("conf/epl.conf"))

  //todo: all of this should go into a config file
  val outputPath = "data/out/"
  val fileName = "predict.txt"

  val k = 3
  val lambda = 0.01
  val alpha = 0.1
  val maxIter = 200

  val debug = true
  val formulae = true
  val print = true

  val db = if (debug) {
    val tmp = new TensorKB(k)
    tmp.sampleTensor(10, 10, 0, 0.1) //samples a matrix
    if (formulae) {
      tmp += Impl("r3", "r4")
      tmp += ImplNeg("r8", "r6")
    }
    tmp
  } else LoadNAACL(k)

  val rand = new Random(0l)

  val fg = db.toFactorGraph
  val data = rand.shuffle(db.trainCells)
  val V = db.ix1ToNodeMap //cols
  val A = db.ix2ToNodeMap //rows


  //initialize embeddings
  //def nextInit() = (rand.nextDouble() - 0.5) * 0.1
  def nextInit() = rand.nextGaussian() * 0.1
  (V.values.view ++ A.values.view).foreach(n =>
    n.variable.asVector.b = new DenseVector((0 until k).map(i => nextInit()).toArray))

  //println(V.values.head.variable.asVector.b)

  for (d <- data) {
    //colIx: relation
    //rowIx: entity
    val (colIx, rowIx, _) = d.key
    val a = A(rowIx)
    val v = V(colIx)

    //create positive fact factor
    fg.buildFactor(Seq(a, v))(_ map (_ => new VectorMsgs)) { 
      e => new CellLogisticLoss(e(0), e(1), 1.0, lambda) with L2Regularization
      //e => new CellLogisticLoss(e(0), e(1), 0.9, lambda) with L2Regularization
    }

    //also create a sampled stochastic negative factor in the same column
    fg.buildStochasticFactor(Seq(v, db.sampleNode(colIx)))(_ map (_ => new VectorMsgs)) {
      e => new CellLogisticLoss(e(0), e(1), 0.0, lambda) with L2Regularization
      //e => new CellLogisticLoss(e(0), e(1), 0.1, lambda) with L2Regularization
    }

    //create formulae factors
    for (formula <- db.formulaeByPredicate(colIx)) {
      val cNode = v
      if (formula.isFormula2) {
        val Seq(p1, p2) = formula.predicates
        val p1Node = db.node1(p1).get
        val p2Node = db.node1(p2).get

        formula match {
          case Impl(_, _, target) =>
            fg.buildFactor(Seq(cNode, p1Node, p2Node))(_ map (_ => new VectorMsgs)) {
              e => new ImplPotential(e(0), e(1), e(2), target, lambda) with L2Regularization
            }
            //also inject the formula for a constant it hasn't been observed with
            fg.buildStochasticFactor(Seq(db.sampleNode(colIx), p1Node, p2Node))(_ map (_ => new VectorMsgs)) {
              e => new ImplPotential(e(0), e(1), e(2), target, lambda) with L2Regularization
            }
          case ImplNeg(_, _, target) =>
            fg.buildFactor(Seq(cNode, p1Node, p2Node))(_ map (_ => new VectorMsgs)) {
              e => new ImplNegPotential(e(0), e(1), e(2), target, lambda) with L2Regularization
            }
            //also inject the formula for a constant it hasn't been observed with
            fg.buildStochasticFactor(Seq(db.sampleNode(colIx), p1Node, p2Node))(_ map (_ => new VectorMsgs)) {
              e => new ImplNegPotential(e(0), e(1), e(2), target, lambda) with L2Regularization
            }
        }
      } else {
        ???
      }
    }
  }

  fg.build()

  println(s"""Config:
    |λ:        $lambda
    |k:        $k
    |α:        $alpha
    |maxIter:  $maxIter""".stripMargin)

  println(db.toInfoString)

  println("Optimizing...")
  Timer.time("optimization") {
    //BatchTrainer
    //GradientBasedOptimizer(fg, new BatchTrainer(_, new AdaGrad(rate = α), maxIter) with ProgressLogging)
    //GradientBasedOptimizer(fg, new BatchTrainer(_, new ConstantLearningRate(baseRate = α), maxIter) with ProgressLogging)

    //OnlineTrainer
    //GradientBasedOptimizer(fg, new OnlineTrainer(_, new ConstantLearningRate(baseRate = α), maxIter, fg.factors.size - 1) with ProgressLogging)
    GradientBasedOptimizer(fg, new OnlineTrainer(_, new AdaGrad(rate = alpha), maxIter, fg.factors.size - 1) with ProgressLogging) //best
    //GradientBasedOptimizer(fg, new OnlineTrainer(_, new AdaMira(rate = α), maxIter, fg.factors.size - 1) with ProgressLogging)

    //GradientBasedOptimizer(fg, new BatchTrainer(_, new LBFGS(Double.MaxValue, Int.MaxValue), maxIter))
  }
  println("Done after " + Timer.reportedVerbose("optimization"))

  if (debug && print) {
    println("train:")
    println(db.toVerboseString(showTrain = true))
    println()

    println("predicted:")
    println(db.toVerboseString())

  } else {
    WriteNAACL(db, outputPath + fileName)
    EvaluateNAACL.main(Array("./conf/eval.conf", outputPath + fileName))

    import scala.sys.process._
    Process("pdflatex -interaction nonstopmode -shell-escape table.tex", new File(outputPath)).!!
  }
}

object WolfeStyleMF extends App {

  import ml.wolfe.Wolfe._
  import ml.wolfe.macros.OptimizedOperators._
  case class Data(rel:String, arg1:String, arg2:String, target:Double)

  case class Model(relationVectors:Map[String,Seq[Double]], entityPairVectors:Map[(String,String),Seq[Double]])

  def dot(a1:Seq[Double],a2:Seq[Double]) = ???

  val rels = Seq("profAt")
  val ents = Seq("Luke" -> "MIT")


  def searchSpace(k:Int) = all(Model)(maps(rels,fvectors(k)) x maps(ents,fvectors(k)))

  def fvectors(k:Int) = Wolfe.seqsOfLength(k,Wolfe.doubles)



  //@Potential(???) //cell logistic potential
  def logisticLoss(target:Double, arg1:Seq[Double], arg2:Seq[Double]) =
  //todo: sigmoid
    sum(0 until arg1.length) { i => arg1(i) * arg2(i) }

  //@Stochastic(String => (String, String)) //samples a non-observed pair efficiently from data; not for now
  //creates as many stochastic factors as the integer before the sum
  @Stochastic
  def negativeDataLoss(data: Seq[Data])(model: Model) = {
    val r = data.head.rel
    val numObserved = data.size //function of r
    val numUnobserved = ents.size - numObserved

    //there needs to be a default implementation that takes the filtered domain (ents) and samples from it
    numObserved * sum(ents filter { pair => !data.exists(d => pair == (d.arg1, d.arg2)) }){ pair =>
      logisticLoss(0.0, model.entityPairVectors(pair), model.relationVectors(r)) * (numUnobserved / numObserved.toDouble)
    }
  }

  def objective(data:Seq[Data])(model:Model) = {
    sum(data) { d => logisticLoss(d.target,model.entityPairVectors(d.arg1 -> d.arg2), model.relationVectors(d.rel)) } +
    sum(rels) { r => negativeDataLoss(data.filter(_.rel == r))(model) }
  }

  println("It compiles, yay! :)")
}