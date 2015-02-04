package ml.wolfe

import ml.wolfe.fg20.{Domain, IndexedSeqDomain, AtomicDomain}

/**
 * @author riedel
 */
package object term {
  type DoubleVar = DoubleDom#DomVar
  type VectorVar = VectorDom#DomVar
  type MatrixVar = MatrixDom#DomVar
  type DiscVar[T] = DiscreteDom[T]#DomVar
  type Constant[D <: Dom] = D#Constant

  type TypedDom[T] = Dom { type Value = T}
  type DomWithTerm[T] = Dom { type Term = T}
  type DoubleTerm = Term[DoubleDom]
  type VectorTerm = Term[VectorDom]
  type MatrixTerm = Term[MatrixDom]
  type BoolDom = DiscreteDom[Boolean]
  type BoolTerm = Term[BoolDom]
  type DiscreteTerm[T] = Term[DiscreteDom[T]]
//  type Potential2[D1<: Dom, D2 <:Dom] = Potential[Tuple2Domain[D1,D2]]
  type DoubleSeqDom = SeqDom[DoubleDom]
//  type Potential[P] = Fun[P,DoubleDom]
//  type ComposedPotential = Composed[Double,AtomicSearchSpace.Cont]

}
