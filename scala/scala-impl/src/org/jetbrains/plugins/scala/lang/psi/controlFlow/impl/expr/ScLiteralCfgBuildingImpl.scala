package org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.expr

import org.jetbrains.plugins.scala.dfa.DfValue
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScIntLiteral, ScLiteral, ScNullLiteral, ScStringLiteral}
import org.jetbrains.plugins.scala.lang.psi.controlFlow.CfgBuilder

trait ScLiteralCfgBuildingImpl { this: ScLiteral =>

  override def buildActualExpressionControlFlow(withResult: Boolean)(implicit builder: CfgBuilder): Unit = {

    this match {
      case ScNullLiteral() =>
        builder.pushNull()

      case ScIntLiteral(int) =>
        builder.push(DfValue.int(int))

      case ScStringLiteral(string) =>
        builder.pushString(string)

      case _ =>
        // Some error. Just push any
        builder.pushAny()
    }

    if (!withResult) {
      builder.noop()
    }
  }
}
