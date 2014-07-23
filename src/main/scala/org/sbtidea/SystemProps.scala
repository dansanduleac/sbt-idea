package org.sbtidea

import org.sbtidea.ValueClasses.JdkName

object SystemProps {
  val jdkName = JdkName(System.getProperty("java.version").substring(0, 3))
  val languageLevel = ValueClasses.formatLanguageLevel(jdkName)
  val runsOnWindows = System.getProperty("os.name").contains("Windows")
  val userHome = {
    val system = System.getProperty("user.home")
    if (system.endsWith(java.io.File.separator)) system.dropRight(1) else system
  }
}