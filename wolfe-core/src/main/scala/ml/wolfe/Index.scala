package ml.wolfe

import gnu.trove.strategy.HashingStrategy
import gnu.trove.map.custom_hash.TObjectIntCustomHashMap
import scala.collection.mutable.ArrayBuffer
import scala.collection.{GenMap, mutable}
import gnu.trove.procedure.TObjectIntProcedure
import java.io.{ObjectInputStream, ObjectOutputStream}
import gnu.trove.map.hash.TObjectIntHashMap

/**
 * @author Sebastian Riedel
 */
trait Index {
  def size: Int
  def apply(key: Any): Int = index(key)
  def index(key: Any): Int
  def hasKey(key:Any) : Boolean
  def inverse(): GenMap[Int, Any]
  def key(index:Int):Any

}

@SerialVersionUID(100L)
class SimpleIndex extends Serializable with Index {

  private val map = new TObjectIntHashMap[Any]
  private val inversed = new ArrayBuffer[Any]

  def size = map.size()
  def index(args: Any): Int = {
    val oldSize = map.size()
    val current = map.adjustOrPutValue(args, 0, map.size)
    if (map.size > oldSize) inversed += args
    current
  }

  def ++=(that:Map[Any,Int]): Unit = {
    for ((k,v) <- that) {
      map.put(k,v)
    }
  }

  def toMap:Map[Any,Int] = {
    val result = new mutable.HashMap[Any, Int]
    map.forEachEntry(new TObjectIntProcedure[Any] {
      def execute(a: Any, b: Int) = { result(a) = b; true }
    })
    result.toMap

  }

  def key(index:Int) = inversed(index)

  def hasKey(key: Any) = map.containsKey(key)
  def inverse() = {
    val result = new mutable.HashMap[Int, Any]
    map.forEachEntry(new TObjectIntProcedure[Any] {
      def execute(a: Any, b: Int) = { result(b) = a; true }
    })
    result
  }

  def toVerboseString = {
    val result = new mutable.StringBuilder()
    map.forEachEntry(new TObjectIntProcedure[Any] {
      def execute(a: Any, b: Int) = { result.append("%40s -> %d\n".format(a, b)); true }
    })
    result.toString()
  }
  override def toString = "Index"

  def serialize(stream: ObjectOutputStream) {
    map.writeExternal(stream)
  }

  def deserialize(stream: ObjectInputStream): this.type = {
    val deserializedMap = new TObjectIntCustomHashMap[Any]
    deserializedMap.readExternal(stream)
    this.map.putAll(deserializedMap)
    this
  }


}


//trait FactorieVectorBuilder {
//  this: Index =>
//
//  private val sparseVectorCache         = new mutable.HashMap[Wolfe.Vector, FactorieVector]()
//  private val oneHotFactorieVectorCache = new mutable.HashMap[(Int, Double), FactorieVector]()
//
//  def toCachedFactorieOneHotVector(component: Any, value: Double) = {
//    val index = this.index(component)
//    val result = oneHotFactorieVectorCache.getOrElseUpdate(index -> value, new SingletonVector(1, index, value))
//    result
//  }
//
//  def toCachedFactorieSparseVector[T](vector: Wolfe.Vector, singletons: Boolean = false): FactorieVector = {
//    val result = sparseVectorCache.getOrElseUpdate(vector, toFreshFactorieSparseVector(vector, singletons))
//    result
//  }
//
//
//  def toFreshFactorieSparseVector[T](vector: Wolfe.Vector, singletons: Boolean = false): FactorieVector = {
//    if (singletons && vector.size == 1) {
//      val singleton = new SingletonVector(1, this.index(vector.head._1), vector.head._2)
//      singleton
//    } else {
//      val sparse = new SparseVector(vector.self.size)
//      for ((key, value) <- vector.self) sparse(this.index(key)) = value
//      sparse
//    }
//  }
//
//  def vectorToString(vector: FactorieVector, sep: String = "\n") = {
//    val inv = inverse()
//    val lines = for (i <- vector.activeDomain.toSeq; if vector(i) != 0.0) yield {
//      f"${ inv(i) }%20s ${ vector(i) }%5.2f"
//    }
//    lines.mkString(sep)
//  }
//}

//class SimpleIndexAndBuilder extends SimpleIndex with FactorieVectorBuilder

object Index {
  var toDebug: Option[Index] = None
}

