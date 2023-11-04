package com.waterloorocketry.openrocket_monte_carlo;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import net.sf.openrocket.database.Databases;
import net.sf.openrocket.document.OpenRocketDocument;
import net.sf.openrocket.document.Simulation;
import net.sf.openrocket.file.GeneralRocketLoader;
import net.sf.openrocket.plugin.PluginModule;
import net.sf.openrocket.startup.Application;
import net.sf.openrocket.startup.GuiModule;

import java.io.File;

public class Main {
    /**
     * Entry for OpenRocket Monte Carlo
     * @param args Command line arguments
     */
    public static void main(String[] args) throws Exception {
        initializeOpenRocket();

        File file = new File("./orhelper/examples/simple.ork");
        GeneralRocketLoader loader = new GeneralRocketLoader(file);

        OpenRocketDocument doc = loader.load();

        System.out.println(doc.getSimulationCount());

        Simulation sim = doc.getSimulation(0);

        sim.simulate();
        System.out.println(sim.getSimulatedData().getMaxAcceleration());
    }

    /**
     * Inject required dependencies for OpenRocket, allowing us to run simulations
     * programmatically.
     */
    private static void initializeOpenRocket() {
        GuiModule guiModule = new GuiModule();
        Module pluginModule = new PluginModule();
        Injector injector = Guice.createInjector(guiModule, pluginModule);
        Application.setInjector(injector);
        guiModule.startLoader();
        Databases.fakeMethod();
    }
}
