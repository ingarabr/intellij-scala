package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.containers.ComparatorUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

/**
 * @author Pavel Fatin
 */
public class ScalaCompileServerForm implements Configurable {
    private JPanel myCompilationServerPanel;
    private RawCommandLineEditor myCompilationServerJvmParameters;
    private JTextField myCompilationServerMaximumHeapSize;
    private JCheckBox myEnableCompileServer;
    private JPanel myContentPanel;
    private JdkComboBox myCompilationServerSdk;
    private MultiLineLabel myNote;
    private JPanel mySdkPanel;
    private JCheckBox myProjectHomeChb;
    private MultiLineLabel myProjectHomeNote;
    private JCheckBox myShutdownServerCheckBox;
    private JSpinner myShutdownDelay;
    private ScalaCompileServerSettings mySettings;
    private ProjectSdksModelWithDefault sdkModel;

    public ScalaCompileServerForm(ScalaCompileServerSettings settings) {
        mySettings = settings;

        myEnableCompileServer.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updateCompilationServerSettingsPanel();
            }
        });

        sdkModel = new ProjectSdksModelWithDefault();
        sdkModel.reset(null);

        myCompilationServerSdk = new JdkComboBox(sdkModel);
        myCompilationServerSdk.insertItemAt(new JdkComboBox.NoneJdkComboBoxItem(), 0);

        mySdkPanel.add(myCompilationServerSdk, BorderLayout.CENTER);

        myNote.setForeground(JBColor.GRAY);
        myProjectHomeNote.setForeground(JBColor.GRAY);

        myShutdownDelay.setModel(new SpinnerNumberModel(settings.COMPILE_SERVER_SHUTDOWN_DELAY, 0, 24 * 60, 1));

        updateCompilationServerSettingsPanel();
    }

    private void updateCompilationServerSettingsPanel() {
        setDescendantsEnabledIn(myCompilationServerPanel, myEnableCompileServer.isSelected());
        myNote.setEnabled(true);
    }

    private static void setDescendantsEnabledIn(JComponent root, boolean b) {
        for (Component child : root.getComponents()) {
            child.setEnabled(b);
            if (child instanceof JComponent) {
                setDescendantsEnabledIn((JComponent) child, b);
            }
        }
    }

    @Nls
    public String getDisplayName() {
        return "Scala Compile Server";
    }

    @Nullable
    public String getHelpTopic() {
        return null;
    }

    @Nullable
    public JComponent createComponent() {
        return myContentPanel;
    }

    public boolean isModified() {
        Sdk sdk = myCompilationServerSdk.getSelectedJdk();
        String sdkName = sdk == null ? null : sdk.getName();

        return !(myEnableCompileServer.isSelected() == mySettings.COMPILE_SERVER_ENABLED &&
                sdkModel.isDefault(sdk) == mySettings.USE_DEFAULT_SDK &&
                ComparatorUtil.equalsNullable(sdkName, mySettings.COMPILE_SERVER_SDK) &&
                myCompilationServerMaximumHeapSize.getText().equals(mySettings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE) &&
                myCompilationServerJvmParameters.getText().equals(mySettings.COMPILE_SERVER_JVM_PARAMETERS) &&
                myProjectHomeChb.isSelected() == mySettings.USE_PROJECT_HOME_AS_WORKING_DIR &&
                myShutdownServerCheckBox.isSelected() == mySettings.COMPILE_SERVER_SHUTDOWN_IDLE &&
                (Integer) (myShutdownDelay.getModel().getValue()) == mySettings.COMPILE_SERVER_SHUTDOWN_DELAY
        );
    }

    public void apply() throws ConfigurationException {
        mySettings.COMPILE_SERVER_ENABLED = myEnableCompileServer.isSelected();

        Sdk sdk = myCompilationServerSdk.getSelectedJdk();
        mySettings.USE_DEFAULT_SDK = sdkModel.isDefault(sdk);
        mySettings.COMPILE_SERVER_SDK = sdk == null ? null : sdk.getName();

        mySettings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE = myCompilationServerMaximumHeapSize.getText();
        mySettings.COMPILE_SERVER_JVM_PARAMETERS = myCompilationServerJvmParameters.getText();

        mySettings.USE_PROJECT_HOME_AS_WORKING_DIR = myProjectHomeChb.isSelected();

        mySettings.COMPILE_SERVER_SHUTDOWN_IDLE = myShutdownServerCheckBox.isSelected();
        mySettings.COMPILE_SERVER_SHUTDOWN_DELAY = (Integer) (myShutdownDelay.getModel().getValue());

        // TODO
//    boolean externalCompiler = CompilerWorkspaceConfiguration.getInstance(myProject).USE_COMPILE_SERVER;
//
//    if (!externalCompiler || !myEnableCompileServer.isSelected()) {
//      myProject.getComponent(CompileServerLauncher.class).stop();
//    }
//    myProject.getComponent(CompileServerManager.class).configureWidget();
    }

    public void reset() {
        myEnableCompileServer.setSelected(mySettings.COMPILE_SERVER_ENABLED);

        Sdk sdk = sdkModel.sdkFrom(mySettings);
        myCompilationServerSdk.setSelectedJdk(sdk);

        myCompilationServerMaximumHeapSize.setText(mySettings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE);
        myCompilationServerJvmParameters.setText(mySettings.COMPILE_SERVER_JVM_PARAMETERS);

        myShutdownServerCheckBox.setSelected(mySettings.COMPILE_SERVER_SHUTDOWN_IDLE);
        myShutdownDelay.getModel().setValue(mySettings.COMPILE_SERVER_SHUTDOWN_DELAY);

        myProjectHomeChb.setSelected(mySettings.USE_PROJECT_HOME_AS_WORKING_DIR);
    }

    public void disposeUIResources() {
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        myContentPanel = new JPanel();
        myContentPanel.setLayout(new GridLayoutManager(8, 4, new Insets(0, 0, 0, 0), -1, -1));
        myCompilationServerPanel = new JPanel();
        myCompilationServerPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
        myContentPanel.add(myCompilationServerPanel, new GridConstraints(1, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 1, false));
        final JLabel label1 = new JLabel();
        label1.setEnabled(true);
        label1.setText("JVM options:");
        label1.setDisplayedMnemonic('O');
        label1.setDisplayedMnemonicIndex(4);
        myCompilationServerPanel.add(label1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myCompilationServerJvmParameters = new RawCommandLineEditor();
        myCompilationServerJvmParameters.setDialogCaption("Compile server JVM command line parameters");
        myCompilationServerJvmParameters.setEnabled(true);
        myCompilationServerPanel.add(myCompilationServerJvmParameters, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, new Dimension(250, -1), new Dimension(544, 27), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setEnabled(true);
        label2.setText("JVM maximum heap size, MB:");
        label2.setDisplayedMnemonic('H');
        label2.setDisplayedMnemonicIndex(12);
        myCompilationServerPanel.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myCompilationServerMaximumHeapSize = new JTextField();
        myCompilationServerMaximumHeapSize.setColumns(5);
        myCompilationServerMaximumHeapSize.setEnabled(true);
        myCompilationServerPanel.add(myCompilationServerMaximumHeapSize, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myNote = new MultiLineLabel();
        myNote.setText("The compile server is application-wide (there is a single instance for all projects).\nThe JDK is used to instantiate the compile server and to invoke \nan in-process Java compiler (when JDK and module SDK match).");
        myCompilationServerPanel.add(myNote, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("JDK:");
        label3.setDisplayedMnemonic('J');
        label3.setDisplayedMnemonicIndex(0);
        myCompilationServerPanel.add(label3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        mySdkPanel = new JPanel();
        mySdkPanel.setLayout(new BorderLayout(0, 0));
        mySdkPanel.setEnabled(false);
        myCompilationServerPanel.add(mySdkPanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myEnableCompileServer = new JCheckBox();
        myEnableCompileServer.setText("Use compile server for Scala");
        myEnableCompileServer.setMnemonic('S');
        myEnableCompileServer.setDisplayedMnemonicIndex(12);
        myContentPanel.add(myEnableCompileServer, new GridConstraints(0, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        myContentPanel.add(panel1, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 1, false));
        myProjectHomeNote = new MultiLineLabel();
        myProjectHomeNote.setText("Compile server will be restarted on each compilation of a new project.");
        panel1.add(myProjectHomeNote, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        myContentPanel.add(spacer1, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JSeparator separator1 = new JSeparator();
        myContentPanel.add(separator1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myProjectHomeChb = new JCheckBox();
        myProjectHomeChb.setText("Use project home as compile server working directory");
        myProjectHomeChb.setMnemonic('W');
        myProjectHomeChb.setDisplayedMnemonicIndex(35);
        myContentPanel.add(myProjectHomeChb, new GridConstraints(5, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        Font label4Font = this.$$$getFont$$$(null, -1, -1, label4.getFont());
        if (label4Font != null) label4.setFont(label4Font);
        label4.setText("Advanced settings");
        myContentPanel.add(label4, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        myContentPanel.add(panel2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myShutdownServerCheckBox = new JCheckBox();
        myShutdownServerCheckBox.setText("Shutdown server if it is idle for");
        panel2.add(myShutdownServerCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel2.add(spacer2, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        myShutdownDelay = new JSpinner();
        panel2.add(myShutdownDelay, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(100, -1), new Dimension(100, -1), 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("minutes");
        panel2.add(label5, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        return new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myContentPanel;
    }

}
