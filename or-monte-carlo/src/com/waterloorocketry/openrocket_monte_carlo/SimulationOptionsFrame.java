package com.waterloorocketry.openrocket_monte_carlo;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.RocketLoadException;
import info.openrocket.core.gui.util.SimpleFileFilter;
import info.openrocket.core.logging.Markers;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.simulation.extension.SimulationExtension;
import info.openrocket.core.startup.Application;
import info.openrocket.swing.gui.plot.SimulationPlotConfiguration;
import info.openrocket.swing.gui.plot.SimulationPlotDialog;
import info.openrocket.swing.gui.simulation.SimulationConfigDialog;
import info.openrocket.swing.gui.simulation.SimulationRunDialog;
import info.openrocket.swing.gui.theme.UITheme;
import info.openrocket.swing.gui.util.FileHelper;
import info.openrocket.swing.gui.util.SwingPreferences;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class SimulationOptionsFrame extends JFrame {
    private final static Logger log = LoggerFactory.getLogger(SimulationOptionsFrame.class);

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final String
            THRUST_FILE_SET_EVENT = "thrustFileSet",
            ROCKET_FILE_SET_EVENT = "rocketFileSet",
            SIMULATIONS_CONFIGURED_EVENT = "simulationConfigured";

    private OpenRocketDocument document;

    private File openRocketFile, thrustCurveFile;

    private int numSimulations = 100;

    private SimulationEngine simulationEngine;

    static {
        initColors();
    }

    void setThrustCurveFile(File thrustCurveFile) {
        pcs.firePropertyChange(THRUST_FILE_SET_EVENT, this.thrustCurveFile, thrustCurveFile);
        this.thrustCurveFile = thrustCurveFile;
    }

    void setOpenRocketFile(File openRocketFile) {
        pcs.firePropertyChange(ROCKET_FILE_SET_EVENT, this.openRocketFile, openRocketFile);
        this.openRocketFile = openRocketFile;
    }

    private void setSimulationEngine(SimulationEngine simulationEngine) {
        pcs.firePropertyChange(SIMULATIONS_CONFIGURED_EVENT, this.simulationEngine, simulationEngine);
        this.simulationEngine = simulationEngine;
    }

    public SimulationOptionsFrame() {
        super("Waterloo Rocketry Monte-Carlo Simulator");
        this.setSize(800, 600);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        final JPanel contentPanel = new JPanel(new MigLayout("fill, insets 0, wrap 1"));

        contentPanel.add(addTopPanel(), "grow, push");
        contentPanel.add(addBottomPanel(), "dock south");

        this.add(contentPanel, BorderLayout.CENTER);
    }

    private JPanel addTopPanel() {
        JPanel topPanel = new JPanel(new MigLayout("fill, wrap 2", "[grow 0]para[grow]"));

        topPanel.add(new JLabel("Thrust Curve File"), "align label, growx");
        final JPanel thrustCurveFileSelectButton = getThrustCurveFileSelectPanel();
        topPanel.add(thrustCurveFileSelectButton, "align right");

        topPanel.add(new JLabel("Rocket File"), "align label, growx");
        final JPanel rocketFileSelectButton = getRocketFileSelectPanel();
        topPanel.add(rocketFileSelectButton, "align right");

        topPanel.add(new JLabel("Number of simulations"), "align label");
        final JFormattedTextField numSimTextField = getNumSimField();
        topPanel.add(numSimTextField, "growx");

        final JButton configButton = getConfigButton();
        topPanel.add(configButton, "span, growx");

        return topPanel;
    }

    private JFormattedTextField getNumSimField() {
        final JFormattedTextField numSimTextField = new JFormattedTextField(numSimulations);
        numSimTextField.addPropertyChangeListener("value",
                evt -> {
                    numSimulations = Integer.parseInt(numSimTextField.getText());
                    pcs.firePropertyChange(SIMULATIONS_CONFIGURED_EVENT, this.simulationEngine, null);
                    this.simulationEngine = null; // invalidate outdated simulations
                });

        return numSimTextField;
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


    private @NotNull JPanel getThrustCurveFileSelectPanel() {
        JPanel thrustCurveFileSelectPanel = new JPanel(new MigLayout("fill, wrap 2, ins 0"));

        final JLabel thrustCurveFilePath = new JLabel();
        final JButton thrustCurveFileSelectButton = new JButton("Select File");
        thrustCurveFileSelectButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setMultiSelectionEnabled(false);
            // open thrust curve file
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

        thrustCurveFileSelectPanel.add(thrustCurveFilePath, "growx, align right");
        thrustCurveFileSelectPanel.add(thrustCurveFileSelectButton, "align right");
        return thrustCurveFileSelectPanel;
    }

    private @NotNull JPanel getRocketFileSelectPanel() {
        JPanel rocketFileSelectPanel = new JPanel(new MigLayout("fill, wrap 2, ins 0"));

        final JLabel rocketFilePath = new JLabel();
        final JButton rocketFileSelectButton = new JButton("Select File");
        rocketFileSelectButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();

            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setMultiSelectionEnabled(false);
            // open OpenRocket file
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

        rocketFileSelectPanel.add(rocketFilePath, "growx, align right");
        rocketFileSelectPanel.add(rocketFileSelectButton, "align right");
        return rocketFileSelectPanel;
    }

    private @NotNull JButton getConfigButton() {
        final JButton configButton = new JButton("Set simulation options");
        configButton.addActionListener( e -> {
            setSimulationEngine(new SimulationEngine(document, numSimulations));
            Simulation[] sims = simulationEngine.getSimulations();
            SimulationConfigDialog config = new SimulationConfigDialog(this, document, true, sims);
            WindowAdapter closeConfigListener = new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
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
        final JButton runButton = new JButton("Run");
        runButton.addActionListener(e -> {

            Simulation[] sims = simulationEngine.getSimulations();
            log.info("Options accepted, starting Monte Carlo Simulation");
            for (int i = 1; i < sims.length; i++) {
                sims[i].getSimulationExtensions().clear();
                for (SimulationExtension ext : sims[0].getSimulationExtensions()) {
                    sims[i].getSimulationExtensions().add(ext.clone());
                }
            }

            // run simulations
            JDialog runDialog = new SimulationRunDialog(this, document, sims);
            runDialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    log.info("Simulation done, processing data");
                    List<SimulationData> data = simulationEngine.processSimulationData();
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
