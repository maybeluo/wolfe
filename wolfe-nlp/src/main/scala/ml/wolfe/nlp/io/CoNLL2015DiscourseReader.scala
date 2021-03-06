package ml.wolfe.nlp.io

import ml.wolfe.nlp._
import ml.wolfe.nlp.discourse.{DiscourseRelation, DiscourseArgument}
import ml.wolfe.nlp.syntax.{Arc, DependencyTree, ConstituentTree}
import org.json4s.JsonAST.{JInt, JObject, JString}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import java.nio.charset.StandardCharsets

import scala.io.Source

/**
 * @author matko
 * @author rockt
 */
object CoNLL2015DiscourseReader {

  implicit val formats = DefaultFormats

  case class JSONDocument(sentences: List[JSONSentence])
  case class JSONSentence(dependencies: List[List[String]], parsetree: String, words: List[List[JValue]])
  case class JSONDiscourseRelation(Arg1: JSONDiscourseArgument, Arg2: JSONDiscourseArgument, Connective: JSONDiscourseArgument, DocID: String, ID: Int, Sense: List[String], Type: String)
  case class JSONDiscourseArgument(CharacterSpanList: List[List[Int]] , RawText: String, TokenList: List[List[Int]])

  // load relations and group them by documentID
  def loadDiscourseRelations(filename: String): Map[String, List[JSONDiscourseRelation]] = {
    val lines_data = Source.fromFile(filename).getLines()
    val data = lines_data.map{ line_data =>
      val json = parse(line_data)
      json.extract[JSONDiscourseRelation]
    }.toList
    data.groupBy(_.DocID)
  }



  def loadData(dataDirectory: String): Iterable[Document] = {
    val doc_relations = loadDiscourseRelations(dataDirectory + "pdtb-data.json")
    val lines_parses = Source.fromFile(dataDirectory + "pdtb-parses.json").getLines()

    val content = lines_parses.mkString
    val json_parses = parse(content)
    val parses = json_parses.extract[Map[String, JSONDocument]]



    val documents = parses.map { document =>
      val docID = document._1

      val sentences = document._2.sentences.map { sentence =>

        val tokens = sentence.words.map { word =>
          val JString(token) = word(0)
          val JObject(properties) = word(1)
          val JInt(offset_start) = properties(0)._2
          val JInt(offset_end) = properties(1)._2
          // LINKERS NOT USED
          //val JArray(linkers) = properties(2)._2
          val JString(posTag) = properties(3)._2
          val offsets = CharOffsets(offset_start.toInt, offset_end.toInt)
          Token(token, offsets, posTag)
        }.toIndexedSeq

        val arcs = sentence.dependencies.map { dependency =>
          val label = dependency(0)
          val child = dependency(1).splitAt(dependency(1).lastIndexOf("-")+1)._2.toInt - 1
          val head = dependency(2).splitAt(dependency(2).lastIndexOf("-")+1)._2.toInt - 1
          Arc(child, head, Some(label))
        }.filterNot(_.label.get == "root").toSeq

        val parse_tree = "(ROOT" + sentence.parsetree.drop(1).dropRight(2) + ")"
        val cons_tree = ConstituentTreeFactory.stringToTree(parse_tree).getOrElse(ConstituentTree.empty)
        val dep_tree = DependencyTree(tokens, arcs)

        Sentence(tokens, new SyntaxAnnotation(cons_tree, dep_tree))
      }

      val filename = dataDirectory + "raw/" + docID
      val text = Source.fromFile(filename, "ISO-8859-1").getLines().mkString("\n")

      val discourse = doc_relations.get(docID) match {
        case Some(relations) =>
          val rels = relations.map{ relation =>
            def toWolfeDiscourseArgument(x: JSONDiscourseArgument): DiscourseArgument = {
              val tokens = x.TokenList.map(tok => (tok(3), tok(4))).toSeq
              val offsets = x.CharacterSpanList.map(span => CharOffsets(span(0), span(1)))
              DiscourseArgument(x.RawText, offsets, tokens)
            }
            val arg1 = toWolfeDiscourseArgument(relation.Arg1)
            val arg2 = toWolfeDiscourseArgument(relation.Arg2)
            val connective = toWolfeDiscourseArgument(relation.Connective)
            DiscourseRelation(arg1, arg2, connective, relation.ID.toString, relation.Sense, relation.Type)
          }.toSeq
          DiscourseAnnotation(rels)
        case None => DiscourseAnnotation.empty
      }
      Document(text, sentences.toIndexedSeq, filename = Some(filename), id = Some(docID), discourse = discourse)
    }
    documents
  }


}

object CoNLL2015DiscourseWriter {
  case class JSONDiscourseRelation(
    Arg1: JSONDiscourseArgument,
    Arg2: JSONDiscourseArgument,
    Connective: JSONDiscourseArgument,
    DocID: String,
    //ID: Int,
    Sense: Seq[String],
    Type: String)
  case class JSONDiscourseArgument(
    //CharacterSpanList: List[List[Int]],
    //RawText: String,
    TokenList: Seq[Int]
  )

  def writeDocumentsToJSON(documents: Iterable[Document], filename: String) = {
    val sb = new StringBuilder
    documents.toIterator.foreach{ document =>
      val jSONDiscourseRelations = documentToJSONDiscourseRelations(document)
      import org.json4s.jackson.Serialization.write
      implicit val formats = org.json4s.jackson.Serialization.formats(NoTypeHints)
      jSONDiscourseRelations.foreach(relation => sb.append(write(relation) + "\n"))
    }
    scala.tools.nsc.io.File(filename).writeAll(sb.toString)
  }

  def documentToJSONDiscourseRelations(document: Document): Seq[JSONDiscourseRelation] = {
    def sentenceTokenIndiciesToDocTokenIndices(discourseTokens: Seq[(Int, Int)]): Seq[Int] = discourseTokens.map(t => {
      val (sIx, tIx) = t
      document.sentences.slice(0, sIx).map(_.tokens.length).sum + tIx
    })

    document.discourse.relations.map { relation =>
      val arg1 = JSONDiscourseArgument(sentenceTokenIndiciesToDocTokenIndices(relation.arg1.tokens))
      val arg2 = JSONDiscourseArgument(sentenceTokenIndiciesToDocTokenIndices(relation.arg2.tokens))
      val connective = JSONDiscourseArgument(sentenceTokenIndiciesToDocTokenIndices(relation.connective.tokens))
      JSONDiscourseRelation(arg1, arg2, connective, document.id.get, relation.sense, relation.typ)
    }
  }
}

object CoNLL2015TestReadWrite extends App {
  val useDev = true
  println("Initiating reader.")
  val path =
    if (useDev) "./data/conll15st-train-dev/conll15st_data/conll15-st-03-04-15-dev/"
    else "./data/conll15st-train-dev/conll15st_data/conll15-st-03-04-15-train/"

  val output = CoNLL2015DiscourseReader.loadData(path)
  println("Read " + output + " documents. Now writing...")
  CoNLL2015DiscourseWriter.writeDocumentsToJSON(output, "output.json")
  println("Writing DONE.")
  import sys.process._
  println("Running validator...")
  println(s"python conll15st/validator.py output.json".!)
  println("Running scorer...")
  println(s"python conll15st/scorer.py $path/pdtb-data.json output.json".!)
  println("Done!")
}
