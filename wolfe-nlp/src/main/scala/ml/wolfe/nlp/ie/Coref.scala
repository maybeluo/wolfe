package ml.wolfe.nlp.ie

/**
 * Created by narad on 3/20/15.
 */

import ml.wolfe.nlp.{Document, Token}

import scala.collection.mutable.HashMap
/**
 * Class to represent coreference annotation
 * @param mentions sequence of CorefMention elements
 */
case class CorefAnnotation(mentions: Seq[CorefMention] = Seq.empty) {
  type BareMention = (Int,Int,Int)

  lazy val mentionToCluster: Map[BareMention, Int] = {
    mentions.map(m => (m.sentence, m.start, m.end) -> m.clusterID)
  }.toMap


  def clusterOf(s: Int, i: Int, j: Int): Option[Int] = {
    mentionToCluster.get((s,i,j))
  }

  def distanceInMentions(m1: CorefMention, m2: CorefMention): Int = {
    mentions.count(m => m1 < m && m < m2)
  }

  def hasMention(s: Int, i: Int, j: Int): Boolean = {
    mentionToCluster.contains((s,i,j))
  }

  def hasMention(m: BareMention): Boolean = {
    mentionToCluster.contains(m)
  }

  def mentionTokens(m: CorefMention, d: Document): IndexedSeq[Token] = {
    assert(m.sentence < d.sentences.size, "Document does not have a sentence at idx = %d.".format(m.sentence))
    assert(d.sentences(m.sentence).size >= m.end, "Sentence at idx = %d is of len %d (queried mention ends at %d).".format(m.sentence, m.end))
    d.sentences(m.sentence).tokens.slice(m.start, m.end)
  }

  def mentionTokens(m: BareMention, d: Document): IndexedSeq[Token] = {
    assert(m._1 < d.sentences.size, "Document does not have a sentence at idx = %d.".format(m._1))
    assert(d.sentences(m._1).size >= m._3, "Sentence at idx = %d is of len %d (queried mention ends at %d).".format(m._1, m._3))
    d.sentences(m._1).tokens.slice(m._2, m._3)
  }

  def shareCluster(m1: CorefMention, m2: CorefMention): Boolean = {
    shareCluster((m1.sentence, m1.start, m1.end), (m2.sentence, m2.start, m2.end))
  }

  def shareCluster(m1: BareMention, m2: BareMention): Boolean = {
    mentionToCluster(m1) == mentionToCluster(m2)
  }
}

object CorefAnnotation {
  val empty = CorefAnnotation()

}

/**
 * Class to represent coreference mention
 * @param clusterID ID of the cluster (chain) of connected mentions
 * @param sentence sentence index
 * @param start starting index
 * @param end ending index (points to the first token AFTER the mention)
 * @param head head of the coreference mention
 */
case class CorefMention(clusterID: Int, sentence: Int, start: Int, end: Int, head: Int = -1) extends Ordered[CorefMention] {

  // symmetric nest
  def areNested(that: CorefMention): Boolean = {
    this.nests(that) || that.nests(this)
  }

  // asymmetric -- If m1 nests m2
  def nests(that: CorefMention): Boolean = {
    this.sentence == that.sentence && this.start <= that.start && this.end >= that.end && this != that
  }

  def crosses(that: CorefMention): Boolean = {
    this.sentence == that.sentence &&
      ((this.start < that.start && this.end > that.start && this.end < that.end) ||
        (this.start > that.start && this.start < that.end && this.end > that.end))
  }

  override def compare(that: CorefMention): Int = {
    if (this.sentence < that.sentence) return -1
    else if (this.sentence > that.sentence) return 1
    else {
      if (this.start < that.start) return -1
      else if (this.start > that.start) return 1
      else {
        if (this.end < that.end) return -1
        else if (this.end > that.end) return 1
        else return 0
      }
    }
  }

  override def toString = "[Coref Mention: Sent: %d, Start: %d, End: %d, Cluster: %d]".format(sentence, start, end, clusterID)

  def width = end - start
}










/*
  override def toString = {
    "[COREF MENTION\n" +
    "  START: %d\n".format(start) +
    "  END: %d\n".format(end) +
      (if (head >= 0) "  HEAD: %d\n".format(head) else "") +
    "  SENTENCE: %d\n".format(sentence) +
    "  CLUSTER: %d]\n".format(clusterID)
  }
*/