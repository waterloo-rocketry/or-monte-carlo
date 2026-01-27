package com.waterloorocketry.openrocket_monte_carlo.aspect;

import info.openrocket.core.unit.UnitGroup;
import info.openrocket.swing.gui.adaptors.DoubleModel;
import info.openrocket.swing.gui.components.UnitSelector;
import info.openrocket.swing.gui.simulation.CSVImportSettingsDialog;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

import static info.openrocket.core.util.Chars.DEGREE;

/**
 * info/openrocket/swing/gui/simulation/CSVImportSettingsDialog.java
 * This aspect is functionally unused, we do not support edits to the import structure for windDirStdDev right now.
 */
privileged aspect CSVImportSettingsDialogAspect {
    // Add fields to the target dialog
    private JTextField CSVImportSettingsDialog.windDirStdDevColumnField;
    private JSpinner CSVImportSettingsDialog.windDirStdDevColumnSpinner;
    private JPanel CSVImportSettingsDialog.windDirStdDevColumnPanel;
    private UnitSelector CSVImportSettingsDialog.windDirStdDevUnitSelector;

    // Initialize fields after any CSVImportSettingsDialog constructor finishes
    after(info.openrocket.swing.gui.simulation.CSVImportSettingsDialog dialog):
            execution(info.openrocket.swing.gui.simulation.CSVImportSettingsDialog+.new(..)) && this(dialog) {

        // this is the same as stdDeviationColumn (L211-234) but we don't support edits right now
        dialog.windDirStdDevColumnField = new JTextField(10);
        dialog.windDirStdDevColumnField.setText("windDirStdDev");
        dialog.windDirStdDevColumnField.setEnabled(false);

        // unused for now
        dialog.windDirStdDevColumnSpinner = new JSpinner(new SpinnerNumberModel(4, 0,
                Integer.MAX_VALUE, 1));
        dialog.windDirStdDevColumnSpinner.setEditor(new JSpinner.NumberEditor(dialog.windDirStdDevColumnSpinner, "#"));
        dialog.windDirStdDevColumnSpinner.setEnabled(false);

        dialog.windDirStdDevColumnPanel = new JPanel(new CardLayout());
        dialog.windDirStdDevColumnPanel.add(dialog.windDirStdDevColumnField, "text");
        dialog.windDirStdDevColumnPanel.add(dialog.windDirStdDevColumnSpinner, "spinner");

        DoubleModel windDirStdDevModel = new DoubleModel(0, UnitGroup.UNITS_ANGLE);
        dialog.windDirStdDevUnitSelector = new UnitSelector(windDirStdDevModel);
        dialog.windDirStdDevUnitSelector.setSelectedUnit(UnitGroup.UNITS_ANGLE.getUnit("" + DEGREE));
        dialog.windDirStdDevUnitSelector.setEnabled(false);

        // from scrollPane get mainPanel and then settingsPanel
        JPanel mainPanel = (JPanel) dialog.scrollPane.getViewport().getView();
        JPanel settingsPanel = (JPanel) mainPanel.getComponent(1); // settingsPanel is second component (L111)

        final int SETTINGS_PANEL_BOTTOM_OFFSET = 3; // ignore the JSepartor and field separator panel (L234)

        settingsPanel.add(new JLabel("Wind Direction StdDev"),
                settingsPanel.getComponentCount() - SETTINGS_PANEL_BOTTOM_OFFSET);
        settingsPanel.add(dialog.windDirStdDevColumnPanel, "growx",
                settingsPanel.getComponentCount() - SETTINGS_PANEL_BOTTOM_OFFSET);
        settingsPanel.add(dialog.windDirStdDevUnitSelector, "wrap",
                settingsPanel.getComponentCount() - SETTINGS_PANEL_BOTTOM_OFFSET);

        // updatePreview has not been updated to handle windDirStdDev, but we call it anyway to keep consistent
        dialog.windDirStdDevColumnField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                dialog.updatePreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                dialog.updatePreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                dialog.updatePreview();
            }
        });

        dialog.windDirStdDevColumnSpinner.addChangeListener(e -> {
            dialog.validateColumnIndices();
            dialog.updatePreview();
        });

        dialog.windDirStdDevUnitSelector.addItemListener(e -> dialog.updatePreview());
    }
}
