package org.sbtidea.test.util

import sbt._
import org.apache.commons.io.FileUtils.listFiles
import org.apache.commons.io.FilenameUtils.removeExtension
import scala.xml.Utility.trim
import xml._
import collection.JavaConverters._
import xml.transform.{RewriteRule, RuleTransformer}
import xml.Node
import org.sbtidea.SystemProps
import scala.language.implicitConversions
import scala.language.reflectiveCalls

abstract class AbstractScriptedTestBuild(projectName : String) extends Build {
  import XmlAttributesCopy._
  lazy val assertExpectedXmlFiles = TaskKey[Unit]("assert-expected-xml-files")

	lazy val scriptedTestSettings = Seq(assertExpectedXmlFiles := assertXmlsTask)

  private def assertXmlsTask() {
    val expectedFiles = listFiles(file("."), Array("expected"), true).asScala
    expectedFiles.map(assertExpectedXml).foldLeft[Option[String]](None) {
      (acc, fileResult) => if (acc.isDefined) acc else fileResult
    } foreach sys.error
  }

  private def assertExpectedXml(expectedFile: File):Option[String] = {
    val actualFile = new File(removeExtension(expectedFile.getAbsolutePath))
    if (actualFile.exists) assertExpectedXml(expectedFile, actualFile)
    else Some("Expected file " + actualFile.getAbsolutePath + " does not exist.")
  }

  private def assertExpectedXml(expectedFile: File, actualFile: File): Option[String] = {
    /* Make generated files OS independent and strip the suffix that is randomly generated from content url so that comparisons can work */
    val actualFileTransformers = Map("" -> Seq(WindowsPathRewriteRule), ".iml" -> Seq(TmpPathRewriteRule, IvyCachePathRewriteRule))
    /* Take current jdk version into consideration */
    val expectedFileTransformers = Map("misc.xml.expected" -> Seq(JDKVersionRewriteRule))

    def transformNode(fileName: String, transformerMap: Map[String, Seq[RewriteRule]], node: xml.Node): xml.Node = {
      val transformers = transformerMap.keys.foldLeft(Seq[RewriteRule]()) { (acc, key) =>
        if (fileName.endsWith(key)) acc ++ transformerMap(key) else acc
      }
      new RuleTransformer(transformers:_*).transform(node).head
    }

    def processActual(node: xml.Node): xml.Node = transformNode(actualFile.getName, actualFileTransformers, node)
    def processExpected(node: xml.Node): xml.Node = transformNode(expectedFile.getName, expectedFileTransformers, node)

    val actualXml = trim(processActual(XML.loadFile(actualFile)))
    val expectedXml = trim(processExpected(XML.loadFile(expectedFile)))
    if (actualXml != expectedXml) Some(formatErrorMessage(actualFile, actualXml, expectedXml)) else None
  }

  private def formatErrorMessage(actualFile: File, actualXml: Node, expectedXml: Node): String = {
    val pp = new PrettyPrinter(1000, 2)
    val msg = new StringBuilder
    msg.append("Xml file " + actualFile.getName + " does not equal expected:")
    msg.append("\n********** Expected **********\n ")
    pp.format(expectedXml, msg)
    msg.append("\n*********** Actual ***********\n ")
    pp.format(actualXml, msg)
    msg.toString
  }

  object XmlAttributesCopy {
    implicit def addGoodCopyToAttribute(attr: Attribute) = new {
      def goodcopy(key: String = attr.key, value: Any = attr.value): Attribute =
        Attribute(attr.pre, key, Text(value.toString), attr.next)
    }

    implicit def iterableToMetaData(items: Iterable[MetaData]): MetaData = items match {
      case Nil => Null
      case head :: tail => head.copy(next = iterableToMetaData(tail))
    }
  }

  object WindowsPathRewriteRule extends RewriteRule {
    override def transform(n: Node): Seq[Node] =
      n match {
        case e: Elem if e.attributes.asAttrMap.values.exists(_.contains("\\")) =>
          e.copy(attributes = for (attr <- e.attributes) yield attr match {
            case a@Attribute(_, v, _) if v.text.contains("\\") => a.goodcopy(value = v.text.replaceAll("\\\\", "/"))
            case other => other
          })
        case _ => n
      }
  }

  object JDKVersionRewriteRule extends RewriteRule {
    override def transform(n: Node): Seq[Node] =
      n match {
        case e: Elem if e.attributes.asAttrMap.values.exists(_ == "ProjectRootManager") =>
          e.copy(attributes = for (attr <- e.attributes) yield attr match {
            case a@Attribute(k, _, _) if k == "languageLevel" => a.goodcopy(value = SystemProps.languageLevel)
            case a@Attribute(k, _, _) if k == "project-jdk-name" => a.goodcopy(value = SystemProps.jdkName)
            case other => other
          })
        case _ => n
      }
  }

  object TmpPathRewriteRule extends RewriteRule {
    def elementMatches(e: Node): Boolean = {
      val url = (e \ "@url").text
      url.matches("file://.*/sbt_[a-f[0-9]]+/" + projectName + "$")
    }

    override def transform(n: Node): Seq[Node] = n match {
      case e: Elem if elementMatches(e) => {
        <content url={"file:///tmp/sbt_/" + projectName}>
          {e.child}
        </content>
      }
      case _ => n
    }
  }

  object IvyCachePathRewriteRule extends RewriteRule {
    override def transform(n: Node): Seq[Node] =
      n match {
        case e: Elem if e.attributes.asAttrMap.keys.exists(_ == "value") => {
          e.copy(attributes = for (attr <- e.attributes) yield attr match {
            case a@Attribute(k, Text(v), _) if k == "value" && v.contains("/.ivy2/") => a.goodcopy(value = "~" + v.substring(v.indexOf("/.ivy2/")))
            case other => other
          })
        }
        case _ => n
      }
  }
}