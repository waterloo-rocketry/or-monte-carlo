package com.waterloorocketry.openrocket_monte_carlo;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import info.openrocket.core.database.Databases;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.RocketLoadException;
import info.openrocket.core.gui.util.SimpleFileFilter;
import info.openrocket.core.logging.Markers;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.preferences.ApplicationPreferences;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.simulation.extension.SimulationExtension;
import info.openrocket.core.startup.Application;
import info.openrocket.swing.gui.main.BasicFrame;
import info.openrocket.swing.gui.plot.SimulationPlotConfiguration;
import info.openrocket.swing.gui.plot.SimulationPlotDialog;
import info.openrocket.swing.gui.simulation.SimulationConfigDialog;
import info.openrocket.swing.gui.simulation.SimulationRunDialog;
import info.openrocket.swing.gui.theme.UITheme;
import info.openrocket.swing.gui.util.FileHelper;
import info.openrocket.swing.gui.util.GUIUtil;
import info.openrocket.swing.gui.util.SwingPreferences;
import info.openrocket.swing.startup.GuiModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;

public class Main {
    private final static Logger log = LoggerFactory.getLogger(Main.class);

    private static Frame frame = new Frame("Waterloo Rocketry Monte-Carlo Simulator");

    public static void main(String[] args) throws Exception {

        initializeOpenRocket();
        frame.setVisible(true);

        OpenRocketDocument doc = configureDocument();

        SimulationEngine simulationEngine = new SimulationEngine(doc);
        Simulation[] sims = simulationEngine.getSimulations();

        SimulationConfigDialog config = new SimulationConfigDialog(frame, doc, true, sims);
        WindowAdapter closeConfigListener = new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                config.dispose();
                frame.dispose();
            }
        };
        config.addWindowListener(closeConfigListener);

        Field okButtonField = config.getClass().getDeclaredField("okButton");
        okButtonField.setAccessible(true);
        JButton okButton = (JButton) okButtonField.get(config);

        okButton.removeActionListener(okButton.getActionListeners()[0]); // remove default action
        okButton.addActionListener(e -> {
            log.info("Options accepted, start Monte Carlo Simulation");
            for (int i = 1; i < sims.length; i++) {
                sims[i].getSimulationExtensions().clear();
                for (SimulationExtension ext : sims[0].getSimulationExtensions()) {
                    sims[i].getSimulationExtensions().add(ext.clone());
                }
            }

            config.removeWindowListener(closeConfigListener);
            config.dispose();

            // run simulations
            JDialog runDialog = new SimulationRunDialog(frame, doc, sims);
            runDialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    log.info("Simulation done, processing data");
                    List<SimulationData> data = simulationEngine.processSimulationData();
                    if (data != null)
                        displaySimulation(frame, data);
                }
            });
        });

        config.setVisible(true);

//         run simulations
//        new SimulationRunDialog(frame, doc, sims).setVisible(true);


    }


    /**
     * Inject required dependencies for OpenRocket, allowing us to run simulations
     * programmatically.
     * This runs the same code as for starting up a GUI version of OpenRocket, making it easier to make manual
     * simulation runs automatic.
     */
    private static void initializeOpenRocket() {
        GuiModule guiModule = new GuiModule();
        Module pluginModule = new PluginModule();
        Injector injector = Guice.createInjector(guiModule, pluginModule);
        Application.setInjector(injector);
        guiModule.startLoader();
        Databases.fakeMethod();
        String cmdLAF = System.getProperty("openrocket.laf");
        if (cmdLAF != null) {
            ApplicationPreferences prefs = Application.getPreferences();
            prefs.setUITheme(UITheme.Themes.valueOf(cmdLAF));
        }
        GUIUtil.applyLAF();
    }

    /**
     * Get the preferences of OpenRocket Swing
     * @return Preferences object
     */
    private static SwingPreferences getOpenRocketPreferences() {
        return (SwingPreferences) Application.getPreferences();
    }

    private static OpenRocketDocument configureDocument() throws RocketLoadException {
        JFileChooser chooser = new JFileChooser();

        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(false);

        // open OpenRocket file
        chooser.addChoosableFileFilter(FileHelper.ALL_DESIGNS_FILTER);
        chooser.addChoosableFileFilter(FileHelper.OPENROCKET_DESIGN_FILTER);
        chooser.setFileFilter(FileHelper.OPENROCKET_DESIGN_FILTER);
        chooser.setCurrentDirectory(((SwingPreferences) Application.getPreferences()).getDefaultDirectory());
        int option = chooser.showOpenDialog(frame);
        if (option != JFileChooser.APPROVE_OPTION) {
            log.info(Markers.USER_MARKER, "Decided not to open files, option={}", option);
            System.exit(0);
        }

        ((SwingPreferences) Application.getPreferences()).setDefaultDirectory(chooser.getCurrentDirectory());

        File rocketFile = chooser.getSelectedFile();

        log.info(Markers.USER_MARKER, "Opening rocket file {}", rocketFile);

        // get thrust curve file
        chooser.removeChoosableFileFilter(FileHelper.ALL_DESIGNS_FILTER);
        chooser.removeChoosableFileFilter(FileHelper.OPENROCKET_DESIGN_FILTER);
        chooser.setFileFilter(new SimpleFileFilter("Thrust Curve", false, ".rse"));

        chooser.setCurrentDirectory(((SwingPreferences) Application.getPreferences()).getDefaultDirectory());
        option = chooser.showOpenDialog(frame);
        if (option != JFileChooser.APPROVE_OPTION) {
            log.info(Markers.USER_MARKER, "Decided not to open files, option={}", option);
            System.exit(0);
        }

        ((SwingPreferences) Application.getPreferences()).setDefaultDirectory(chooser.getCurrentDirectory());

        File thrustCurveFile = chooser.getSelectedFile();

        log.info(Markers.USER_MARKER, "Opening thrust curve file {}", thrustCurveFile);

        OpenRocketDocument doc = new GeneralRocketLoader(rocketFile).load();

        getOpenRocketPreferences().setUserThrustCurveFiles(Collections.singletonList(thrustCurveFile));

        return doc;
    }

    /**
     * Displays data of a simulation
     */
    private static void displaySimulation(Window parent, List<SimulationData> data) {
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

        final int[] plots = {data.size()};

        for (Simulation simulation : data.stream().map(SimulationData::getSimulation).toList()) {
            Dialog dialog = new Dialog(frame);
            SimulationPlotDialog simDialog = SimulationPlotDialog.getPlot(dialog, simulation, config);
            simDialog.setSize(1000, 500);
            simDialog.setVisible(true);
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    dialog.dispose();
                    plots[0]--;
                    if (plots[0] == 0) parent.dispose();
                }
            });
        }
    }

}
