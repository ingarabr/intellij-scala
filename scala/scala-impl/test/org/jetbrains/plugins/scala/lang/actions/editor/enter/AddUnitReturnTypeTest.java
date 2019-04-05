package org.jetbrains.plugins.scala.lang.actions.editor.enter;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import junit.framework.Test;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

/**
 * User: Dmitry.Naydanov
 * Date: 09.07.14.
 */
@RunWith(AllTests.class)
public class AddUnitReturnTypeTest extends AbstractEnterActionTestBase {
  public AddUnitReturnTypeTest() {
    super("/actions/editor/enter/addunit");
  }

  @Override
  protected void setSettings() {
    super.setSettings();

    final CommonCodeStyleSettings settings = getCommonSettings();
    final ScalaCodeStyleSettings scalaSettings = settings.getRootSettings().getCustomSettings(ScalaCodeStyleSettings.class);
    scalaSettings.ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT = true;
  }

  public static Test suite() {
    return new AddUnitReturnTypeTest();
  }
}
