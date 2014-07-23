package org.sbtidea

import language.implicitConversions

/**
 * Date: 22/07/2014
 * Time: 18:54
 *
 * @author Dan Sanduleac
 */
object ValueClasses {
  case class LanguageLevel private(value: String) extends AnyVal

  /** A Jdk name such as "1.8" */
  class JdkName private(val version: String) extends AnyVal
  object JdkName {
    private[this] val Ver = """\d+\.\d+""".r
    def apply(v: String): JdkName = v match {
      case Ver() => new JdkName(v)
      case _     => sys.error("Attempted to create JdkName with malformed value: " + v)
    }
  }

  implicit def formatLanguageLevel(jdkName: JdkName) = LanguageLevel("JDK_" + jdkName.version.replace(".", "_"))
}
