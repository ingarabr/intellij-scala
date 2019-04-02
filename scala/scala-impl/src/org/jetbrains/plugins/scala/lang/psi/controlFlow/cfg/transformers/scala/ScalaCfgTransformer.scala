package org.jetbrains.plugins.scala.lang.psi.controlFlow.cfg.transformers.scala

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScBlockStatement, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.api.{ConstructorInvocationLike, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.controlFlow.{CfgBuilder, ControlFlowGraph}
import org.jetbrains.plugins.scala.project.ProjectContext

class ScalaCfgTransformer(implicit val projectContext: ProjectContext)
    extends ScalaElementVisitor with Transformer with CallExprTransformer with PatternTransformer {

  override val builder = new CfgBuilder

  override def buildExpression(expr: ScExpression, needResult: Boolean): Unit = {
    expr.accept(new ScalaExpressionTransformer(this, builder, needResult))
  }

  // Build the statements.
  // Leave the last statement's result on the stack
  def buildStatements(stmts: Seq[ScBlockStatement], expectResult: Boolean): Unit = {
    if (stmts.isEmpty && expectResult) {
      // todo: only on error? otherwise push unit?
      builder.pushAny()
      return
    }

    val lastStmtIndex = stmts.size
    for ((stmt, idx) <- stmts.zipWithIndex) {
      val isLast = idx == lastStmtIndex

      visit(stmt)

      if (!(isLast && expectResult)) {
        // leave the last result on the stack (as result of the statement list
        builder.pop()
      }
    }
  }

  private def buildConstructorInvocation(constructorInvocation: ConstructorInvocationLike): Unit = {
    ???
  }

  def buildClassConstruction(extendsBlock: ScExtendsBlock): Unit = {
    // build super call
    extendsBlock
      .templateParents
      .flatMap(_.constructorInvocation)
      .foreach(buildConstructorInvocation)


    // build class body
    extendsBlock
      .templateBody
      .foreach(visit)
  }

  def visit(elem: PsiElement): Unit = {
    elem.accept(this)
  }

  def finish(): ControlFlowGraph = builder.build()

  // ---------------- Visitor methods ---------------- //
  override def visitElement(element: PsiElement): Unit =
    throw new NotImplementedError(s"No implementation to visit element '$element'")

  override def visitExpression(expr: ScExpression): Unit = {
    buildExpression(expr)
  }

  override def visitAssignmentStatement(stmt: ScAssignment): Unit = {
    visit(stmt.leftExpression)

    buildExpressionOrPushAny(stmt.rightExpression)

    //builder.assign()
    ???
  }

  def buildDefinition(patternList: ScPatternList, exprOpt: Option[ScExpression]): Unit = {
    val patterns = patternList.patterns.filter(!canIgnorePattern(_))
    buildExpressionOrPushAnyIfNeeded(exprOpt, needResult = patterns.nonEmpty)

    // if we have n patterns,
    // we need to duplicate the value on the stack (n-1) times
    // to have n values on the stack
    builder.dup(patterns.size - 1)
    patterns.foreach(buildPattern)
  }

  override def visitVariableDefinition(varDef: ScVariableDefinition): Unit = {
    buildDefinition(varDef.pList, varDef.expr)
  }

  override def visitPatternDefinition(pat: ScPatternDefinition): Unit = {
    buildDefinition(pat.pList, pat.expr)
  }
}

object ScalaCfgTransformer {
  /**
   * Creates the ControlFlowGraph for the primary constructor of a class
   *
   * @param clazz the primary constructor's class
   * @return the created ControlFlowGraph of the primary constructor
   */
  def createCfg(clazz: ScClass): ControlFlowGraph = {
    val transformer = new ScalaCfgTransformer()(clazz.projectContext)
    transformer.buildClassConstruction(clazz.extendsBlock)
    transformer.finish()
  }

  /**
   * Creates the ControlFlowGraph for an expression (also secondary constructors)
   *
   * @param expression the function for which the ControlFlowGraph should be created
   * @return the created ControlFlowGraph of the function
   */
  def createCfg(expression: ScExpression): ControlFlowGraph = {
    val transformer = new ScalaCfgTransformer()(expression.projectContext)
    transformer.visit(expression)
    transformer.finish()
  }
}