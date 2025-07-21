package com.waterloorocketry.openrocket_monte_carlo;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.RocketLoadException;
import info.openrocket.core.gui.util.SimpleFileFilter;
import info.openrocket.core.logging.ErrorSet;
import info.openrocket.core.logging.Markers;
import info.openrocket.core.logging.WarningSet;
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
import java.awt.event.ActionListener;
import java.awt.event.WindowListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class SimulationOptionsFrame extends JFrame {
    private final static Logger log = LoggerFactory.getLogger(SimulationOptionsFrame.class);

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final String
            THRUST_FILE_SET_EVENT = "thrustFileSet",
            ROCKET_FILE_SET_EVENT = "rocketFileSet",
            SIMULATIONS_CONFIGURED_EVENT = "simulationConfigured", // fires when simulations are generated
            SIMULATIONS_PROCESSED_EVENT = "simulationProcessed", // fires whenever a batch is processed (updates table)
            SIMULATIONS_DONE_EVENT = "simulationDone"; // fires when all simulations are done (begin export)

    private final int BATCH_RUN_SIZE = 30;

    private OpenRocketDocument document;

    private File openRocketFile, thrustCurveFile;

    private int numSimulations = 100;
    private int batchCount = 100/30 + 1;
    private double windDirStdDev = 0.0, tempStdDev = 0.0, pressureStdDev = 0.0;

    private SimulationEngine simulationEngine;

    static {
        initColors();
    }

    void setThrustCurveFile(File thrustCurveFile) {
        File old = this.thrustCurveFile;
        this.thrustCurveFile = thrustCurveFile;
        if (old != null && !old.equals(this.thrustCurveFile))
            setOpenRocketFile(null);

        pcs.firePropertyChange(THRUST_FILE_SET_EVENT, old, this.thrustCurveFile);
    }

    void setOpenRocketFile(File openRocketFile) {
        File old = this.openRocketFile;
        this.openRocketFile = openRocketFile;
        if (old != null && !old.equals(this.openRocketFile))
            setSimulationEngine(null);
        pcs.firePropertyChange(ROCKET_FILE_SET_EVENT, old, this.openRocketFile);
    }

    private void setSimulationEngine(SimulationEngine simulationEngine) {
        SimulationEngine old = this.simulationEngine;
        this.simulationEngine = simulationEngine;
        if (this.simulationEngine != null) {
            log.info("Simulations ready");
            batchCount = (int) Math.ceil((double) simulationEngine.simulationCount / BATCH_RUN_SIZE);
        }

        pcs.firePropertyChange(SIMULATIONS_CONFIGURED_EVENT, old, this.simulationEngine);
    }

    public SimulationOptionsFrame() {
        super("Waterloo Rocketry Monte-Carlo Simulator");
        this.setMinimumSize(new Dimension(1000, 600));
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        final JPanel contentPanel = new JPanel(new MigLayout("fill, insets 0, wrap 1"));

        contentPanel.add(addTopPanel(), "grow, push");
        contentPanel.add(addSimulationListPanel(), "grow");
        contentPanel.add(addBottomPanel(), "dock south, growx");

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
        final JPanel bottomPanel = new JPanel(new MigLayout("fill"));

        final JButton exportButton = getExportButton();
        bottomPanel.add(exportButton, "alignx left");

        final JPanel statusDialog = getStatusPanel();
        bottomPanel.add(statusDialog, "alignx right, growx");

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
        DoubleModel tempStdDevModel = new DoubleModel(0);
        JSpinner tempStdDevField = new JSpinner(tempStdDevModel.getSpinnerModel());
        tempStdDevField.setEditor(new SpinnerEditor(tempStdDevField));
        tempStdDevField.addChangeListener(
                evt -> tempStdDev = tempStdDevModel.getValue());
        panel.add(tempStdDevField, "grow");

        panel.add(new JLabel("Pressure standard deviation"), "align label, growx");
        DoubleModel pressureStdDevModel = new DoubleModel(0);
        JSpinner pressureStdDevField = new JSpinner(pressureStdDevModel.getSpinnerModel());
        pressureStdDevField.setEditor(new SpinnerEditor(pressureStdDevField));
        pressureStdDevField.addChangeListener(
                evt -> pressureStdDev = pressureStdDevModel.getValue());
        panel.add(pressureStdDevField, "grow");

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
        String[] columnNames = {"Simulation Name", "Wind Speed(m/s)", "Wind Direction(°)", "Temperature(°C)", "Pressure(mbar)", "Apogee(ft)", "Max Velocity(m/s)", "Min Stability"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Disable editing for all cells
            }
        };
        JTable simulationTable = new JTable(tableModel);

        simulationListPanel.add(new JScrollPane(simulationTable), "grow, push");

        PropertyChangeListener tableChangeHandler = evt -> {
            if (simulationEngine == null) {
                tableModel.setRowCount(0);
                return;
            }

            tableModel.setRowCount(0); // Clear existing rows
            for (SimulationData data : simulationEngine.getData()) {
                String name = data.getName();

                double temp = data.getTemperatureInCelsius();
                double pressure = data.getPressureInMBar();

                double windSpeed = data.getMaxWindSpeed();
                double windDirection = data.getMaxWindDirection();

                double apogee = 0;
                double maxVelocity = 0;
                double minStability = 0;
                if (data.hasData()) {
                    apogee = data.getApogeeInFeet();
                    maxVelocity = data.getMaxVelocity();
                    minStability = data.getMinStability().get(0);
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
        thrustCurveFilePath.setToolTipText(thrustCurveFilePath.getText());
        final JButton thrustCurveFileSelectButton = new JButton("Select File");
        thrustCurveFileSelectButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            chooser.setMultiSelectionEnabled(false);
            chooser.setFileFilter(new SimpleFileFilter("Thrust Curve", true, ".rse"));
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

        thrustCurveFileSelectPanel.add(thrustCurveFilePath, "push, split 2, align right, wmax 70%");
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

            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
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
        // if we clear the rocket file, remove the file label
        pcs.addPropertyChangeListener(ROCKET_FILE_SET_EVENT, event -> {
            if (event.getNewValue() == null)
                rocketFilePath.setText("");
        });


        rocketFileSelectPanel.add(rocketFilePath, "push, split 2, align right, wmax 70%");
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

            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            chooser.setMultiSelectionEnabled(false);
            chooser.setFileFilter(FileHelper.CSV_FILTER);
            chooser.setCurrentDirectory(((SwingPreferences) Application.getPreferences()).getDefaultDirectory());
            int option = chooser.showOpenDialog(this);
            if (option != JFileChooser.APPROVE_OPTION) {
                log.info(Markers.USER_MARKER, "Decided not to open csv data file, option={}", option);
                return;
            }

            ((SwingPreferences) Application.getPreferences()).setDefaultDirectory(chooser.getCurrentDirectory());


            try {
                setSimulationEngine(new SimulationEngine(document, chooser.getSelectedFile()));
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
            // create two simulations to get base conditions.
            // only the first sim will have the set values, the second sim is to enable multi-sim edit
            Simulation[] sims = {simulationEngine.generateDefaultSimulation(), new Simulation(document, document.getRocket())};
            
            SimulationConfigDialog config = new SimulationConfigDialog(this, document, true, sims);

            try {
                Field okButtonField = config.getClass().getDeclaredField("okButton");
                okButtonField.setAccessible(true);
                JButton okButton = (JButton) okButtonField.get(config);

                // remove all current listeners
                for (ActionListener listener : okButton.getActionListeners())
                    okButton.removeActionListener(listener);
                for (WindowListener listener : config.getWindowListeners())
                    config.removeWindowListener(listener);

                okButton.addActionListener(event -> {
                        log.info(Markers.USER_MARKER, "Simulation options accepted, creating simulations...");
                        simulationEngine.createMonteCarloSimulationConditions(sims[0]);
                        setSimulationEngine(simulationEngine);
                        config.dispose();
                });

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

            log.info("Options accepted, starting Monte Carlo Simulation");

            // due to memory limitations, we run simulations in batches and process desired data
            // this allows us to remove the large OR Simulation object from memory
            log.info("Simulations: {} Batches: {}", simulationEngine.simulationCount, batchCount);

            for (int i = 0; i < batchCount; i++) {
                int start = i * BATCH_RUN_SIZE;
                List<Simulation> sims = simulationEngine.getSimulations(start, BATCH_RUN_SIZE);
                JDialog runDialog = getSimulationRunDialog(sims, i+1);
                runDialog.setVisible(true);
            }
            simulationEngine.processSimulationData(); // race condition between windowListener and this thread, safe to call again
            simulationEngine.summarizeSimulations();
            pcs.firePropertyChange(SIMULATIONS_DONE_EVENT, null, true);
        });
        runButton.setEnabled(false);
        pcs.addPropertyChangeListener(SIMULATIONS_CONFIGURED_EVENT, event ->
                runButton.setEnabled(event.getNewValue() != null));
        pcs.addPropertyChangeListener(SIMULATIONS_DONE_EVENT, event ->
                runButton.setEnabled(event.getNewValue() == null));
        return runButton;
    }

    private @NotNull JDialog getSimulationRunDialog(List<Simulation> sims, int batchNumber) {
        JDialog runDialog = new SimulationRunDialog(this, document, sims.toArray(new Simulation[0]));

        runDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                log.info("Batch done, processing data");
                simulationEngine.processSimulationData();
                pcs.firePropertyChange(SIMULATIONS_PROCESSED_EVENT, null, batchNumber);
                runDialog.dispose();
                Runtime.getRuntime().gc();
            }
        });
        return runDialog;
    }

    private @NotNull JButton getExportButton() {
        final JButton exportButton = new JButton("Export", Icons.EXPORT);
        exportButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();

            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            chooser.setMultiSelectionEnabled(false);
            chooser.setFileFilter(FileHelper.CSV_FILTER);
            chooser.setCurrentDirectory(((SwingPreferences) Application.getPreferences()).getDefaultDirectory());
            int option = chooser.showSaveDialog(this);
            if (option != JFileChooser.APPROVE_OPTION) {
                log.info(Markers.USER_MARKER, "Decided not to choose csv file to save, option={}", option);
                return;
            }
            ((SwingPreferences) Application.getPreferences()).setDefaultDirectory(chooser.getCurrentDirectory());
            File file = chooser.getSelectedFile();
            if (!file.getAbsolutePath().endsWith(".csv"))
                file = new File(file + ".csv");

            simulationEngine.exportToCSV(file);
        });
        exportButton.setEnabled(false);
        pcs.addPropertyChangeListener(SIMULATIONS_DONE_EVENT, event ->
                exportButton.setEnabled(event.getNewValue() != null));

        return exportButton;
    }

    private @NotNull JPanel getStatusPanel() {
        final JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new MigLayout("fill, align right"));

        final JLabel batchCountLabel = new JLabel();
        pcs.addPropertyChangeListener(SIMULATIONS_CONFIGURED_EVENT, event -> {
            if (this.simulationEngine != null)
                batchCountLabel.setText("Batch Size: " + BATCH_RUN_SIZE + " Batch Count: " + batchCount);
        });
        statusPanel.add(batchCountLabel, "alignx right");

        final JProgressBar progressBar = new JProgressBar();
        progressBar.setVisible(false);
        progressBar.setValue(0);
        pcs.addPropertyChangeListener(SIMULATIONS_CONFIGURED_EVENT, event -> {
            if (event.getNewValue() == null) progressBar.setVisible(false);
            progressBar.setMaximum(batchCount);
        });
        pcs.addPropertyChangeListener(SIMULATIONS_PROCESSED_EVENT, event -> {
            progressBar.setValue((int) event.getNewValue());
            progressBar.setVisible(true);
        });
        statusPanel.add(progressBar, "alignx right, split 2");

        final JLabel fractionLabel = new JLabel();
        statusPanel.add(fractionLabel);
        fractionLabel.setVisible(false);
        pcs.addPropertyChangeListener(SIMULATIONS_CONFIGURED_EVENT, event -> {
            if (event.getNewValue() == null) {
                progressBar.setVisible(false);
                fractionLabel.setVisible(false);
            }
            progressBar.setMaximum(batchCount);
        });
        pcs.addPropertyChangeListener(SIMULATIONS_PROCESSED_EVENT, event -> {
            progressBar.setValue((int) event.getNewValue());
            progressBar.setVisible(true);
            fractionLabel.setText(event.getNewValue() + "/" + batchCount);
            fractionLabel.setVisible(true);
        });

        statusPanel.setVisible(false);
        pcs.addPropertyChangeListener(SIMULATIONS_CONFIGURED_EVENT, event ->
                statusPanel.setVisible(event.getNewValue() != null));

        return statusPanel;
    }

    private static void initColors() {
        UITheme.Theme.addUIThemeChangeListener(SimulationConfigDialog::updateColors);
    }
    /**
     * Displays data of a simulation
     */
    @Deprecated
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

}
