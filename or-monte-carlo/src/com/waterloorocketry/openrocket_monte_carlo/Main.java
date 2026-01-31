package com.waterloorocketry.openrocket_monte_carlo;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import info.openrocket.core.database.Databases;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.preferences.ApplicationPreferences;
import info.openrocket.core.startup.Application;
import info.openrocket.swing.gui.theme.UITheme;
import info.openrocket.swing.gui.util.GUIUtil;
import info.openrocket.swing.startup.GuiModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private final static Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Starting OpenRocket Monte Carlo Simulation Options GUI...");

        initializeOpenRocket();
        SimulationOptionsFrame frame = new SimulationOptionsFrame();

        frame.setVisible(true);

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

}
