package com.waterloorocketry.openrocket_monte_carlo;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.RocketLoadException;
import info.openrocket.core.gui.util.SimpleFileFilter;
import info.openrocket.core.logging.ErrorSet;
import info.openrocket.core.logging.Markers;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.startup.Application;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.swing.gui.SpinnerEditor;
import info.openrocket.swing.gui.adaptors.DoubleModel;
import info.openrocket.swing.gui.components.UnitSelector;
import info.openrocket.swing.gui.dialogs.ErrorWarningDialog;
import info.openrocket.swing.gui.plot.SimulationPlotConfiguration;
import info.openrocket.swing.gui.plot.SimulationPlotDialog;
import info.openrocket.swing.gui.simulation.SimulationConfigDialog;
import info.openrocket.swing.gui.simulation.SimulationRunDialog;
import info.openrocket.swing.gui.theme.UITheme;
import info.openrocket.swing.gui.util.FileHelper;
import info.openrocket.swing.gui.util.Icons;
import info.openrocket.swing.gui.util.SwingPreferences;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

public class SimulationOptionsFrame extends JFrame {
    private final static Logger log = LoggerFactory.getLogger(SimulationOptionsFrame.class);

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final String
            THRUST_FILE_SET_EVENT = "thrustFileSet",
            ROCKET_FILE_SET_EVENT = "rocketFileSet",
            SIMULATIONS_CONFIGURED_EVENT = "simulationConfigured",
            SIMULATIONS_PROCESSED_EVENT = "simulationProcessed";

    private OpenRocketDocument document;

    private File openRocketFile, thrustCurveFile;

    private int numSimulations = 100;
    private double windDirStdDev = 0.0, tempStdDev = 0.0, pressureStdDev = 0.0;

    private SimulationEngine simulationEngine;

    static {
        initColors();
    }

    void setThrustCurveFile(File thrustCurveFile) {
        File old = this.thrustCurveFile;
        this.thrustCurveFile = thrustCurveFile;
        pcs.firePropertyChange(THRUST_FILE_SET_EVENT, old, this.thrustCurveFile);
    }

    void setOpenRocketFile(File openRocketFile) {
        File old = this.openRocketFile;
        this.openRocketFile = openRocketFile;
        pcs.firePropertyChange(ROCKET_FILE_SET_EVENT, old, this.openRocketFile);
    }

    private void setSimulationEngine(SimulationEngine simulationEngine) {
        SimulationEngine old = this.simulationEngine;
        this.simulationEngine = simulationEngine;
        if (this.simulationEngine != null)
            log.info("Simulations ready");

        pcs.firePropertyChange(SIMULATIONS_CONFIGURED_EVENT, old, this.simulationEngine);
    }

    public SimulationOptionsFrame() {
        super("Waterloo Rocketry Monte-Carlo Simulator");
        this.setMinimumSize(new Dimension(800, 600));
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        final JPanel contentPanel = new JPanel(new MigLayout("fill, insets 0, wrap 1"));

        contentPanel.add(addTopPanel(), "grow, push");
        contentPanel.add(addSimulationListPanel(), "grow");
        contentPanel.add(addBottomPanel(), "dock south");

        this.add(contentPanel, BorderLayout.CENTER);
    }

    private JPanel addTopPanel() {
        JPanel topPanel = new JPanel(new MigLayout("fill, wrap 2", "[grow 0]para[grow]"));

        JPanel leftPanel = new JPanel(new MigLayout("fill, wrap 1"));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Load File"));

        final JPanel thrustCurveFileSelectPanel = getThrustCurveFileSelectPanel();
        leftPanel.add(thrustCurveFileSelectPanel, "grow");
        final JPanel rocketFileSelectPanel = getRocketFileSelectPanel();
        leftPanel.add(rocketFileSelectPanel, "grow");
        topPanel.add(leftPanel, "span, grow, push");

        final JPanel monteCarloOptionsPanel = getMonteCarloOptionsPanel();
        topPanel.add(monteCarloOptionsPanel, "dock east");

        return topPanel;
    }

    private JPanel addBottomPanel() {
        final JPanel bottomPanel = new JPanel(new MigLayout("fill, insets 20"));

        final JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        bottomPanel.add(closeButton, "split 2, tag ok");

        final JButton runButton = getRunButton();
        bottomPanel.add(runButton, "tag ok");

        return bottomPanel;
    }

    private @NotNull JPanel getMonteCarloOptionsPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, ins 20 20 20 20, wrap 2", "[grow]", ""));
        panel.setBorder(BorderFactory.createTitledBorder("Generate Conditions"));

        panel.add(new JLabel("Number of simulations"), "align label, growx");
        final JFormattedTextField numSimTextField = getNumSimField();
        panel.add(numSimTextField);

        panel.add(new JLabel("Wind direction standard deviation"), "align label, growx");
        DoubleModel windDirStDevModel = new DoubleModel(windDirStdDev, UnitGroup.UNITS_ANGLE);
        JSpinner windDirStDevField = new JSpinner(windDirStDevModel.getSpinnerModel());
        windDirStDevField.setEditor(new SpinnerEditor(windDirStDevField));
        windDirStDevField.addChangeListener(
                evt -> windDirStdDev = windDirStDevModel.getValue());
        UnitSelector windDirStDevUnit = new UnitSelector(windDirStDevModel);
        panel.add(windDirStDevField, "split 2, grow");
        panel.add(windDirStDevUnit);

        panel.add(new JLabel("Temperature standard deviation"), "align label, growx");
        DoubleModel tempStdDevModel = new DoubleModel(
                UnitGroup.UNITS_TEMPERATURE.getDefaultUnit().fromUnit(tempStdDev), // do this so we get a reasonable default value
                UnitGroup.UNITS_TEMPERATURE);
        JSpinner tempStdDevField = new JSpinner(tempStdDevModel.getSpinnerModel());
        tempStdDevField.setEditor(new SpinnerEditor(tempStdDevField));
        tempStdDevField.addChangeListener(
                evt -> tempStdDev = tempStdDevModel.getValue());
        UnitSelector tempStdDevUnit = new UnitSelector(tempStdDevModel);
        panel.add(tempStdDevField, "split 2, grow");
        panel.add(tempStdDevUnit);

        panel.add(new JLabel("Pressure standard deviation"), "align label, growx");
        DoubleModel pressureStdDevModel = new DoubleModel(pressureStdDev, UnitGroup.UNITS_PRESSURE);
        JSpinner pressureStdDevField = new JSpinner(pressureStdDevModel.getSpinnerModel());
        pressureStdDevField.setEditor(new SpinnerEditor(pressureStdDevField));
        pressureStdDevField.addChangeListener(
                evt -> pressureStdDev = pressureStdDevModel.getValue());
        UnitSelector pressureStdDevUnit = new UnitSelector(pressureStdDevModel);
        panel.add(pressureStdDevField, "split 2, grow");
        panel.add(pressureStdDevUnit);

        final JButton configButton = getConfigButton();
        panel.add(configButton, "span, push, grow");
        panel.add(new JSeparator(JSeparator.HORIZONTAL), "span, grow, hmin 10, aligny, pushy");

        final JButton importDataButton = getImportDataButton();
        panel.add(importDataButton, "span, push, grow");

        return panel;
    }

    private @NotNull JPanel addSimulationListPanel() {
        JPanel simulationListPanel = new JPanel(new MigLayout("fill"));
        simulationListPanel.setBorder(BorderFactory.createTitledBorder("Simulations"));

        // Create table model and table
        String[] columnNames = {"Simulation Name", "Wind Speed", "Wind Direction", "Temperature", "Pressure", "Apogee", "Max Velocity", "Min Stability"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Disable editing for all cells
            }
        };
        JTable simulationTable = new JTable(tableModel);

        simulationListPanel.add(new JScrollPane(simulationTable), "grow, push");

        PropertyChangeListener tableChangeHandler = evt -> {
            if (simulationEngine == null) return;

            tableModel.setRowCount(0); // Clear existing rows
            for (SimulationData data : simulationEngine.getData()) {
                Simulation sim = data.getSimulation();
                String name = sim.getName();
                double temp = sim.getOptions().getLaunchTemperature();
                double pressure = sim.getOptions().getLaunchPressure();
                Optional<MultiLevelPinkNoiseWindModel.LevelWindModel> maxWindSpdLevel = sim.getOptions().getMultiLevelWindModel().getLevels().stream()
                        .max(Comparator.comparingDouble(MultiLevelPinkNoiseWindModel.LevelWindModel::getSpeed));
                double windSpeed = 0.0;
                double windDirection = 0.0;
                if (maxWindSpdLevel.isPresent()) {
                    windSpeed = maxWindSpdLevel.get().getSpeed();
                    windDirection = maxWindSpdLevel.get().getDirection();
                }

                double apogee = 0;
                double maxVelocity = 0;
                double minStability = 0;
                if (data.hasData()) {
                    apogee = data.getApogee();
                    maxVelocity = data.getMaxVelocity();
                    minStability = data.getMinStability();
                }

                tableModel.addRow(new Object[]{name, windSpeed, windDirection, temp, pressure,
                        apogee, maxVelocity, minStability});
            }
        };

        // Add listener to update table when simulations are configured
        pcs.addPropertyChangeListener(SIMULATIONS_CONFIGURED_EVENT, tableChangeHandler);

        pcs.addPropertyChangeListener(SIMULATIONS_PROCESSED_EVENT, tableChangeHandler);

        return simulationListPanel;
    }


    private @NotNull JPanel getThrustCurveFileSelectPanel() {
        JPanel thrustCurveFileSelectPanel = new JPanel(new MigLayout("fill, wrap 1, ins 0"));

        JLabel thrustCurveFileLabel = new JLabel("Thrust Curve File");
        thrustCurveFileLabel.setFont(new Font(thrustCurveFileLabel.getFont().getName(), Font.BOLD, thrustCurveFileLabel.getFont().getSize()));
        thrustCurveFileSelectPanel.add(thrustCurveFileLabel, "align label, growx");

        final JLabel thrustCurveFilePath = new JLabel();
        final JButton thrustCurveFileSelectButton = new JButton("Select File");
        thrustCurveFileSelectButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setMultiSelectionEnabled(false);
            chooser.setFileFilter(new SimpleFileFilter("Thrust Curve", false, ".rse"));
            chooser.setCurrentDirectory(((SwingPreferences) Application.getPreferences()).getDefaultDirectory());
            int option = chooser.showOpenDialog(this);
            if (option != JFileChooser.APPROVE_OPTION) {
                log.info(Markers.USER_MARKER, "Decided not to open thrust curve file, option={}", option);
                return;
            }
            ((SwingPreferences) Application.getPreferences()).setDefaultDirectory(chooser.getCurrentDirectory());

            setThrustCurveFile(chooser.getSelectedFile());
            log.info(Markers.USER_MARKER, "Opening thrust curve file {}", thrustCurveFile);

            // invalidate previous rocket file with updated thrust curves
            setOpenRocketFile(openRocketFile);

            thrustCurveFilePath.setText(thrustCurveFile.getName());

            Application.getPreferences().setUserThrustCurveFiles(Collections.singletonList(thrustCurveFile));
        });

        thrustCurveFileSelectPanel.add(thrustCurveFilePath, "push, split 2, align right");
        thrustCurveFileSelectPanel.add(thrustCurveFileSelectButton, "align left");
        return thrustCurveFileSelectPanel;
    }

    private @NotNull JPanel getRocketFileSelectPanel() {
        JPanel rocketFileSelectPanel = new JPanel(new MigLayout("fill, wrap 1, ins 0"));

        JLabel rocketFileLabel = new JLabel("Rocket File");
        rocketFileLabel.setFont(new Font(rocketFileLabel.getFont().getName(), Font.BOLD, rocketFileLabel.getFont().getSize()));
        rocketFileSelectPanel.add(rocketFileLabel, "align label, growx");

        final JLabel rocketFilePath = new JLabel();
        final JButton rocketFileSelectButton = new JButton("Select File");
        rocketFileSelectButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();

            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setMultiSelectionEnabled(false);
            chooser.addChoosableFileFilter(FileHelper.ALL_DESIGNS_FILTER);
            chooser.addChoosableFileFilter(FileHelper.OPENROCKET_DESIGN_FILTER);
            chooser.setFileFilter(FileHelper.OPENROCKET_DESIGN_FILTER);
            chooser.setCurrentDirectory(((SwingPreferences) Application.getPreferences()).getDefaultDirectory());
            int option = chooser.showOpenDialog(this);
            if (option != JFileChooser.APPROVE_OPTION) {
                log.info(Markers.USER_MARKER, "Decided not to open rocket file, option={}", option);
                return;
            }

            ((SwingPreferences) Application.getPreferences()).setDefaultDirectory(chooser.getCurrentDirectory());

            log.info(Markers.USER_MARKER, "Opening rocket file {}", openRocketFile);
            setOpenRocketFile(chooser.getSelectedFile());

            rocketFilePath.setText(openRocketFile.getName());

            try {
                document = new GeneralRocketLoader(openRocketFile).load();
            } catch (RocketLoadException ex) {
                log.error(Markers.USER_MARKER, "Error loading Rocket Document", ex);
            }
        });
        rocketFileSelectButton.setEnabled(false);
        pcs.addPropertyChangeListener(THRUST_FILE_SET_EVENT, event ->
                rocketFileSelectButton.setEnabled(event.getNewValue() != null));

        rocketFileSelectPanel.add(rocketFilePath, "push, split 2, align right");
        rocketFileSelectPanel.add(rocketFileSelectButton, "align left");
        return rocketFileSelectPanel;
    }

    private JFormattedTextField getNumSimField() {
        final JFormattedTextField numSimTextField = new JFormattedTextField(numSimulations);
        numSimTextField.addPropertyChangeListener("value",
                evt -> {
                    numSimulations = Integer.parseInt(numSimTextField.getText());
                    setSimulationEngine(null); // invalidate previous simulations
                });

        return numSimTextField;
    }
    
    private @NotNull JButton getImportDataButton() {
        final JButton importCSVButton = new JButton("Import CSV");
        importCSVButton.addActionListener(evt -> {
            JFileChooser chooser = new JFileChooser();

            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setMultiSelectionEnabled(false);
            chooser.setFileFilter(new SimpleFileFilter("CSV File", false, ".csv"));
            chooser.setCurrentDirectory(((SwingPreferences) Application.getPreferences()).getDefaultDirectory());
            int option = chooser.showOpenDialog(this);
            if (option != JFileChooser.APPROVE_OPTION) {
                log.info(Markers.USER_MARKER, "Decided not to open csv data file, option={}", option);
                return;
            }

            ((SwingPreferences) Application.getPreferences()).setDefaultDirectory(chooser.getCurrentDirectory());


            try {
                setSimulationEngine(new SimulationEngine(document,  chooser.getSelectedFile()));
            } catch (Exception e) {
                log.error("Failed to import CSV data", e);

                ErrorSet errors = new ErrorSet();
                errors.add(e.toString());
                ErrorWarningDialog.showErrorsAndWarnings(this,"Failed to load csv file", "CSV Parsing Error",
                        errors, new WarningSet());
            }
        });
        importCSVButton.setEnabled(false);
        pcs.addPropertyChangeListener(ROCKET_FILE_SET_EVENT,
                event -> importCSVButton.setEnabled(event.getNewValue() != null));
        return importCSVButton;
    }

    private @NotNull JButton getConfigButton() {
        final JButton configButton = new JButton("Set simulation options");
        configButton.addActionListener( e -> {
            log.info(Markers.USER_MARKER, "Creating simulation engine with options: {} simulations, " +
                    "{} wind direction stdev, {} temp stdev, {} pressure stdev", numSimulations, windDirStdDev, tempStdDev, pressureStdDev);
            SimulationEngine simulationEngine = new SimulationEngine(document, numSimulations,
                    windDirStdDev, tempStdDev, pressureStdDev);
            Simulation[] sims = simulationEngine.getSimulations();
            
            SimulationConfigDialog config = new SimulationConfigDialog(this, document, true, sims);
            WindowAdapter closeConfigListener = new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    log.info(Markers.USER_MARKER, "Simulation options accepted, creating simulations...");
                    simulationEngine.updateSimulationConditions();
                    setSimulationEngine(simulationEngine);
                    config.dispose();
                }
            };
            config.addWindowListener(closeConfigListener);

            try {
                Field okButtonField = config.getClass().getDeclaredField("okButton");
                okButtonField.setAccessible(true);
                JButton okButton = (JButton) okButtonField.get(config);

                okButton.removeActionListener(okButton.getActionListeners()[0]); // remove default action
                okButton.addActionListener(event -> config.dispose());
            } catch (Exception exception) {
                log.error("Failed to update simulation options ok button", exception);
            }

            config.setVisible(true);
        });
        configButton.setEnabled(false);
        pcs.addPropertyChangeListener(ROCKET_FILE_SET_EVENT, event ->
                configButton.setEnabled(event.getNewValue() != null));
        return configButton;
    }

    private @NotNull JButton getRunButton() {
        final JButton runButton = new JButton("Run", Icons.SIM_RUN);
        runButton.addActionListener(e -> {

            Simulation[] sims = simulationEngine.getSimulations();
            log.info("Options accepted, starting Monte Carlo Simulation");

            // run simulations
            JDialog runDialog = new SimulationRunDialog(this, document, sims);
            runDialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    log.info("Simulation done, processing data");
                    List<SimulationData> data = simulationEngine.processSimulationData();
                    pcs.firePropertyChange(SIMULATIONS_PROCESSED_EVENT, null, data);
                    if (data != null)
                        displaySimulation(data);
                }
            });
            runDialog.setVisible(true);
        });
        runButton.setEnabled(false);
        pcs.addPropertyChangeListener(SIMULATIONS_CONFIGURED_EVENT, event ->
                runButton.setEnabled(event.getNewValue() != null));
        return runButton;
    }

    /**
     * Displays data of a simulation
     */
    private void displaySimulation(List<SimulationData> data) {
        SimulationPlotConfiguration config = new SimulationPlotConfiguration("Low stability case");

        config.addPlotDataType(FlightDataType.TYPE_ALTITUDE, 0);
        config.addPlotDataType(FlightDataType.TYPE_VELOCITY_Z);
        config.addPlotDataType(FlightDataType.TYPE_ACCELERATION_Z);
        config.addPlotDataType(FlightDataType.TYPE_STABILITY);
        config.addPlotDataType(FlightDataType.TYPE_CG_LOCATION);
        config.addPlotDataType(FlightDataType.TYPE_CP_LOCATION);
        config.setEvent(FlightEvent.Type.IGNITION, true);
        config.setEvent(FlightEvent.Type.BURNOUT, true);
        config.setEvent(FlightEvent.Type.APOGEE, true);
        config.setEvent(FlightEvent.Type.RECOVERY_DEVICE_DEPLOYMENT, true);
        config.setEvent(FlightEvent.Type.STAGE_SEPARATION, true);
        config.setEvent(FlightEvent.Type.GROUND_HIT, true);
        config.setEvent(FlightEvent.Type.TUMBLE, true);
        config.setEvent(FlightEvent.Type.EXCEPTION, true);

        for (Simulation simulation : data.stream().map(SimulationData::getSimulation).toList()) {
            Dialog dialog = new Dialog(this);
            SimulationPlotDialog simDialog = SimulationPlotDialog.getPlot(dialog, simulation, config);
            simDialog.setSize(1000, 500);
            simDialog.setVisible(true);
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    dialog.dispose();
                }
            });
        }
    }

    private static void initColors() {
//        textColor = GUIUtil.getUITheme().getTextColor();
//        dimTextColor = GUIUtil.getUITheme().getDimTextColor();
        UITheme.Theme.addUIThemeChangeListener(SimulationConfigDialog::updateColors);
    }

}
