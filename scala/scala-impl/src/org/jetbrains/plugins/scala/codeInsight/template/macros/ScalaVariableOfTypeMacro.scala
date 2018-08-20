package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.codeInsight.template._
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, ResolveState}
import org.jetbrains.plugins.scala.codeInsight.template.util.VariablesCompletionProcessor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}

/**
  * User: Alexander Podkhalyuzin
  * Date: 30.01.2009
  */

/**
  * This class provides macros for live templates. Return elements
  * of given class type (or class types).
  */
class ScalaVariableOfTypeMacro extends ScalaVariableOfTypeMacroBase("macro.variable.of.type") {

  override def arrayIsValid(array: Array[_]): Boolean = array.nonEmpty
}

/**
  * @author Roman.Shein
  * @since 24.09.2015.
  */
class ScalaArrayVariableMacro extends ScalaVariableOfTypeMacroBase("macro.array.variable") {

  private val expressions = Array("scala.Array")

  override protected def typeText(expressions: Array[String], `type`: ScType): Option[String] =
    super.typeText(this.expressions, `type`)

  override protected def typeText(expressions: Array[Expression], `type`: ScType)
                                 (implicit context: ExpressionContext): Boolean =
    super.typeText(this.expressions.map(new TextExpression(_)), `type`)
}

/**
  * @author Roman.Shein
  * @since 24.09.2015.
  */
class ScalaIterableVariableMacro extends ScalaVariableOfTypeMacroBase("macro.iterable.variable") {

  private val expressions = Array(ScalaVariableOfTypeMacroBase.IterableId)

  override protected def typeText(expressions: Array[String], `type`: ScType): Option[String] =
    super.typeText(this.expressions, `type`)

  override protected def typeText(expressions: Array[Expression], `type`: ScType)
                                 (implicit context: ExpressionContext): Boolean =
    super.typeText(this.expressions.map(new TextExpression(_)), `type`)
}

abstract class ScalaVariableOfTypeMacroBase(nameKey: String) extends ScalaMacro(nameKey) {

  import ScalaVariableOfTypeMacroBase._

  override def calculateLookupItems(expressions: Array[Expression], context: ExpressionContext): Array[LookupElement] = expressions match {
    case _ if arrayIsValid(expressions) =>
      implicit val c: ExpressionContext = context
      calculateLookups(expressions.map(calculate))
    case _ => null
  }

  def calculateResult(expressions: Array[Expression], context: ExpressionContext): Result = expressions match {
    case _ if arrayIsValid(expressions) =>
      implicit val c: ExpressionContext = context
      val maybeResult = findDefinitions.collectFirst {
        case (typed, scType) if typeText(expressions, scType) => new TextResult(typed.name)
      }

      maybeResult.orNull
    case _ => null
  }

  def calculateLookups(expressions: Array[String],
                       showOne: Boolean = false)
                      (implicit context: ExpressionContext): Array[LookupElement] = {
    val elements = for {
      (typed, scType) <- findDefinitions
      typeText <- this.typeText(expressions, scType)
    } yield LookupElementBuilder.create(typed, typed.name)
      .withTypeText(typeText)

    elements match {
      case Nil | _ :: Nil if !showOne => null
      case _ => elements.toArray
    }
  }

  override def calculateQuickResult(p1: Array[Expression], p2: ExpressionContext): Result = null

  def getDescription: String = CodeInsightBundle.message("macro.variable.of.type")

  override def getDefaultValue: String = "x"

  def arrayIsValid(array: Array[_]): Boolean = array.isEmpty

  protected def typeText(expressions: Array[Expression], `type`: ScType)
                        (implicit context: ExpressionContext): Boolean =
    typeText(
      expressions.map(calculate),
      `type`
    ).isDefined

  protected def typeText(expressions: Array[String], `type`: ScType): Option[String] = expressions match {
    case Array("", _*) => Some(`type`.presentableText)
    case Array(IterableId, _*) =>
      val flag = `type`.canonicalText.startsWith("_root_.scala.Array") ||
        isIterable(`type`)

      if (flag) Some(null) else None
    case array if array.contains(`type`.extractClass.fold("")(_.qualifiedName)) =>
      Some(null)
    case _ => None
  }
}

object ScalaVariableOfTypeMacroBase {

  val IterableId = "foreach"

  private[macros] def isIterable(`type`: ScType) = `type`.extractClass.exists {
    case definition: ScTypeDefinition => definition.functionsByName(IterableId).nonEmpty
    case _ => false
  }

  private def findDefinitions(implicit context: ExpressionContext) = findElementAtOffset match {
    case Some(element) =>
      variablesForScope(element).collect {
        case ScalaResolveResult(definition: ScTypeDefinition, _) if isFromScala(definition) => definition
      }.collect {
        case definition@Typeable(scType) => (definition, scType)
      }
    case _ => Nil
  }

  /**
    * @param element from which position we look at locals
    * @return visible variables and values from element position
    */
  private[this] def variablesForScope(element: PsiElement) = {
    val processor = new VariablesCompletionProcessor(StdKinds.valuesRef)(element)
    PsiTreeUtil.treeWalkUp(processor, element, null, ResolveState.initial)
    processor.candidates.toList
  }

  private[this] def isFromScala(definition: ScTypeDefinition) =
    PsiTreeUtil.getParentOfType(definition, classOf[PsiClass]) match {
      case ClassQualifiedName("scala.Predef" | "scala") => false
      case _ => true
    }

  private[macros] def calculate(expression: Expression)
                               (implicit context: ExpressionContext): String =
    expression.calculateResult(context).toString
}