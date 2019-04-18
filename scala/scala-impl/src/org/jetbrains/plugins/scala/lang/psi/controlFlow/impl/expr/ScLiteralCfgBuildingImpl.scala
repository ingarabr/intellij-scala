package org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.expr

import org.jetbrains.plugins.scala.dfa.DfValue
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.controlFlow.CfgBuilder
import org.jetbrains.plugins.scala.lang.psi.controlFlow.cfg.{ExprResult, ResultRequirement}

trait ScLiteralCfgBuildingImpl { this: ScLiteral =>

  protected override def buildActualExpressionControlFlow(rreq: ResultRequirement)
                                                         (implicit builder: CfgBuilder): ExprResult = {

    val lit = this match {
      case ScNullLiteral()          => builder.`null`
      case ScBooleanLiteral(value)  => builder.boolean(value)
      case ScIntLiteral(value)      => builder.int(value)
      case ScStringLiteral(value)   => builder.string(value)
      case _                        => builder.any
    }

    rreq.satisfy(lit, noop = true)
  }
}
