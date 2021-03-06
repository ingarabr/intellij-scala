package org.jetbrains.plugins.scala
package codeInsight
package intention

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createBlockExpressionWithoutBracesFromText
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
  * Jason Zaugg
  */
final class RemoveBracesIntention extends PsiElementBaseIntentionAction {

  import RemoveBracesIntention._

  def getFamilyName = "Remove braces"

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    check(project, editor, element).isDefined && IntentionAvailabilityChecker.checkIntention(this, element)

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    if (element == null || !element.isValid) return
    check(project, editor, element).foreach(_.apply())
  }

}

object RemoveBracesIntention {

  case class CommentsAroundElement(before: Seq[PsiElement], after: Seq[PsiElement])

  def collectComments(element: PsiElement, onElementLine: Boolean = false): CommentsAroundElement = {
    def hasLineBreaks(whiteSpace: PsiElement): Boolean = {
      if (!onElementLine) false
      else StringUtil.containsLineBreak(whiteSpace.getText)
    }

    def getElements(it: Iterator[PsiElement]) = {
      def acceptableElem(elem: PsiElement) = {
        (elem.isInstanceOf[PsiComment] || elem.isInstanceOf[PsiWhiteSpace]) && !hasLineBreaks(elem)
      }

      it.takeWhile { a => acceptableElem(a) }.filter(a => a.isInstanceOf[PsiComment]).toSeq
    }

    CommentsAroundElement(getElements(element.prevSiblings).reverse, getElements(element.nextSiblings).reverse)
  }

  def addComments(commentsAroundElement: CommentsAroundElement, parent: PsiElement, anchor: PsiElement): Unit = {
    if ((parent == null) || (anchor == null)) return

    val before = commentsAroundElement.before
    val after = commentsAroundElement.after

    before.foreach(c => CodeEditUtil.setNodeGenerated(c.getNode, true))
    after.foreach(c => CodeEditUtil.setNodeGenerated(c.getNode, true))

    after.foreach(c =>
      if (anchor.getNextSibling != null) parent.getNode.addChild(c.getNode, anchor.getNextSibling.getNode)
      else parent.getNode.addChild(c.getNode)
    )
    before.foreach(c => parent.getNode.addChild(c.getNode, anchor.getNode))
  }

  private def check(project: Project, editor: Editor, element: PsiElement): Option[() => Unit] = {
    val classes = Seq(classOf[ScPatternDefinition], classOf[ScIf], classOf[ScFunctionDefinition], classOf[ScTryBlock],
      classOf[ScFinallyBlock], classOf[ScWhile], classOf[ScDo], classOf[ScCaseClause])

    def isAncestorOfElement(ancestor: PsiElement) = PsiTreeUtil.isContextAncestor(ancestor, element, false)

    val expr: Option[ScExpression] = element.parentOfType(classes).flatMap {
      case ScPatternDefinition.expr(e) if isAncestorOfElement(e) => Some(e)
      case ifStmt: ScIf =>
        ifStmt.thenExpression.filter(isAncestorOfElement).orElse(ifStmt.elseExpression.filter(isAncestorOfElement))
      case funDef: ScFunctionDefinition if !funDef.hasUnitResultType =>
        funDef.body.filter(isAncestorOfElement)
      case tryBlock: ScTryBlock if tryBlock.hasRBrace =>
        def couldRemoveBraces(block: ScTryBlock): Boolean = {
          val blockStatements = block.statements
          blockStatements.length == 1 && (blockStatements.head match {
            case b: ScBlock => b.statements.length == 1
            case _ => true
          })
        }

        // special handling for try block, which itself is parent to the (optional) pair of braces.
        val lBrace = tryBlock.getNode.getChildren(TokenSet.create(ScalaTokenTypes.tLBRACE))
        val rBrace = tryBlock.getNode.getChildren(TokenSet.create(ScalaTokenTypes.tRBRACE))
        (lBrace, rBrace) match {
          case (Array(lBraceNode), Array(rBraceNode)) if couldRemoveBraces(tryBlock) =>
            val action = () => {
              Seq(lBraceNode, rBraceNode).foreach(tryBlock.getNode.removeChild)
              CodeEditUtil.markToReformat(tryBlock.getParent.getNode, true)
              // TODO clean up excess newlines.
            }
            return Some(action)
          case _ => None
        }
      case finallyBlock: ScFinallyBlock =>
        finallyBlock.expression.filter(isAncestorOfElement)
      case whileStmt: ScWhile =>
        whileStmt.expression.filter(isAncestorOfElement)
      case doStmt: ScDo =>
        doStmt.body.filter(isAncestorOfElement)
      case caseClause: ScCaseClause =>
        caseClause.expr match {
          case Some(x: ScBlockExpr) if isAncestorOfElement(x) =>
            // special handling for case clauses, which never _need_ braces.
            val action = () => {
              val Regex = """(?ms)\{(.+)\}""".r
              x.getText match {
                case Regex(code) =>
                  val replacement = createBlockExpressionWithoutBracesFromText(code)(element.getManager)
                  CodeEditUtil.replaceChild(x.getParent.getNode, x.getNode, replacement.getNode)
                  CodeEditUtil.markToReformat(caseClause.getNode, true)
                case _ =>
                  ()
              }
            }
            return Some(action)
          case _ =>
            None
        }
      case _ => None
    }

    // Everything other than case clauses is treated uniformly.

    // Is the expression a block containing a single expression?
    val oneLinerBlock: Option[(ScBlockExpr, ScExpression, CommentsAroundElement)] = expr.flatMap {
      case blk: ScBlockExpr =>
        blk.statements match {
          case Seq(x: ScExpression) =>
            val comments = collectComments(x, onElementLine = true)
            if (!hasOtherComments(blk, comments)) Some((blk, x, comments))
            else None
          case _ => None
        }
      case _ => None
    }

    // Create the action to unwrap that block.
    oneLinerBlock.map {
      case (blkExpr, onlyExpr, comments) =>
        () => {
          addComments(comments, blkExpr.getParent, blkExpr)
          CodeEditUtil.replaceChild(blkExpr.getParent.getNode, blkExpr.getNode, onlyExpr.getNode)
        }
    }
  }

  private[this] def hasOtherComments(element: PsiElement, commentsAroundElement: CommentsAroundElement): Boolean = {
    val allComments = PsiTreeUtil.getChildrenOfTypeAsList(element, classOf[PsiComment])
    allComments.size() > commentsAroundElement.before.size + commentsAroundElement.after.size
  }
}
