package org.jetbrains.plugins.scala.testingSupport.test

import java.util
import java.util.regex.{Pattern, PatternSyntaxException}

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.searches.AllClassesSearch
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.TestKind
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

import scala.annotation.tailrec
import scala.beans.BeanProperty
import scala.collection.JavaConverters._
import scala.collection.mutable

class RegexpTestData(config: AbstractTestRunConfiguration) extends TestConfigurationData(config) {

  @BeanProperty var classRegexps: Array[String] = Array.empty
  @BeanProperty var testRegexps: Array[String]  = Array.empty
  @BeanProperty var testsBuf: java.util.Map[String, java.util.Set[String]] = new util.HashMap()

  protected[test] def zippedRegexps: Array[(String, String)] = classRegexps.zipAll(testRegexps, "", "")

  private def checkRegexps(compileException: (PatternSyntaxException, String) => Exception, noPatternException: Exception): Unit = {
    val patterns = zippedRegexps
    if (patterns.isEmpty) throw noPatternException
    for ((classString, testString) <- patterns) {
      try {
        Pattern.compile(classString)
      } catch {
        case e: PatternSyntaxException => throw compileException(e, classString)
      }
      try {
        Pattern.compile(testString)
      } catch {
        case e: PatternSyntaxException => throw compileException(e, classString)
      }
    }
  }

  private def findTestsByFqnCondition(classCondition: String => Boolean, testCondition: String => Boolean,
                                      classToTests: mutable.Map[String, Set[String]]): Unit = {
    val suiteClasses = AllClassesSearch
      .search(config.getSearchScope.intersectWith(GlobalSearchScopesCore.projectTestScope(getProject)), getProject)
      .asScala
      .filter(c => classCondition(c.qualifiedName)).filterNot(config.isInvalidSuite)

    //we don't care about linearization here, so can process in arbitrary order
    @tailrec
    def getTestNames(classesToVisit: List[ScTypeDefinition], visited: Set[ScTypeDefinition] = Set.empty,
                     res: Set[String] = Set.empty): Set[String] = {
      if (classesToVisit.isEmpty) res
      else if (visited.contains(classesToVisit.head)) getTestNames(classesToVisit.tail, visited, res)
      else {
        getTestNames(classesToVisit.head.supers.toList.filter(_.isInstanceOf[ScTypeDefinition]).
          map(_.asInstanceOf[ScTypeDefinition]).filter(!visited.contains(_)) ++ classesToVisit.tail,
          visited + classesToVisit.head,
          res ++ TestNodeProvider.getTestNames(classesToVisit.head, config.configurationProducer))
      }
    }

    suiteClasses.map {
      case aSuite: ScTypeDefinition =>
        val tests = getTestNames(List(aSuite))
        classToTests += (aSuite.qualifiedName -> tests.filter(testCondition))
      case _ => None
    }
  }

  override def checkSuiteAndTestName(): Unit = {
    checkModule()
    checkRegexps((_, p) => new RuntimeConfigurationException(s"Failed to compile pattern $p"), new RuntimeConfigurationException("No patterns detected"))
  }

  override def getTestMap: Map[String, Set[String]] = {
    val patterns = zippedRegexps
    val classToTests = mutable.Map[String, Set[String]]()
    if (isDumb) {
      if (testsBuf.isEmpty) throw new ExecutionException("Can't run while indexing: no class names memorized from previous iterations.")
      return testsBuf.asScala.map { case (k,v) => k -> v.asScala.toSet }.toMap
    }

    def getCondition(patternString: String): String => Boolean = {
      try {
        val pattern = Pattern.compile(patternString)
        (input: String) =>
          input != null && (pattern.matcher(input).matches ||
            input.startsWith("_root_.") && pattern.matcher(input.substring("_root_.".length)).matches)
      } catch {
        case e: PatternSyntaxException =>
          throw new ExecutionException(s"Failed to compile pattern $patternString", e)
      }
    }

    patterns foreach {
      case ("", "") => //do nothing, empty patterns are ignored
      case ("", testPatternString) => //run all tests with names matching the pattern
        findTestsByFqnCondition(_ => true, getCondition(testPatternString), classToTests)
      case (classPatternString, "") => //run all tests for all classes matching the pattern
        findTestsByFqnCondition(getCondition(classPatternString), _ => true, classToTests)
      case (classPatternString, testPatternString) => //the usual case
        findTestsByFqnCondition(getCondition(classPatternString), getCondition(testPatternString), classToTests)
    }
    val res = classToTests.toMap.filter(_._2.nonEmpty)
    testsBuf = res.map { case (k, v) => k -> v.asJava }.asJava
    res
  }

  override def getKind: TestKind = TestKind.REGEXP

  override def apply(form: TestRunConfigurationForm): Unit = {
    super.apply(form)
    classRegexps = form.getClassRegexps
    testRegexps = form.getTestRegexps
  }
}

object RegexpTestData {
  def apply(config: AbstractTestRunConfiguration, classRegexps: Array[String], testRegexps: Array[String]): RegexpTestData = {
    val res = new RegexpTestData(config)
    res.classRegexps = classRegexps
    res.testRegexps = testRegexps
    res
  }
}
