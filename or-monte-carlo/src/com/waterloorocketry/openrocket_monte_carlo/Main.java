package com.waterloorocketry.openrocket_monte_carlo;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import net.sf.openrocket.database.Databases;
import net.sf.openrocket.document.OpenRocketDocument;
import net.sf.openrocket.document.Simulation;
import net.sf.openrocket.file.GeneralRocketLoader;
import net.sf.openrocket.gui.util.SwingPreferences;
import net.sf.openrocket.plugin.PluginModule;
import net.sf.openrocket.simulation.SimulationOptions;
import net.sf.openrocket.simulation.exception.SimulationException;
import net.sf.openrocket.startup.Application;
import net.sf.openrocket.startup.GuiModule;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main {
    /**
     * Entry for OpenRocket Monte Carlo
     * @param args Command line arguments
     */
    public static void main(String[] args) throws Exception {
        initializeOpenRocket();
        List<File> thrustCurveFiles = new ArrayList<>(getOpenRocketPreferences().getUserThrustCurveFiles());
        thrustCurveFiles.add(new File("./rockets/Kismet_v4_C2-2.rse"));
        getOpenRocketPreferences().setUserThrustCurveFiles(thrustCurveFiles);

        File file = new File("./rockets/c31a.ork");
        GeneralRocketLoader loader = new GeneralRocketLoader(file);

        OpenRocketDocument doc = loader.load();
        for (int i = 1; i <= 100; i++) {
            runSimulation(doc);
        }
    }

    /**
     * Inject required dependencies for OpenRocket, allowing us to run simulations
     * programmatically.
     *
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
    }

    /**
     * Get the preferences of OpenRocket Swing
     * @return Preferences object
     */
    private static SwingPreferences getOpenRocketPreferences() {
        return (SwingPreferences) Application.getPreferences();
    }

    /**
     * Run the OR simulation
     * @param doc OpenRocket document
     * @throws SimulationException If the simulation failed
     */
    private static void runSimulation(OpenRocketDocument doc) throws SimulationException {
        Simulation sim = new Simulation(doc, doc.getRocket());

        Random random = new Random();

        // Set simulation options
        // Units are in m/s so conversion needed
        SimulationOptions opts = sim.getOptions();
        opts.setLaunchRodLength(260 * 2.54 / 100); // 260 inches (to cm) to m
        opts.setLaunchRodAngle(Math.toRadians(5)); // 5 +- 1 deg in Launch Angle

        double windspeed = randomGauss(random, 8.449, 4.45);
        opts.setWindSpeedAverage(windspeed * 0.44707);  // 8.449 mph
        System.out.println("Cond: Avg WindSpeed: " + windspeed + "mph");
        opts.setWindSpeedDeviation(0.8449 * 0.44707);  // 4.450 mph Std.Dev of wind
        opts.setWindTurbulenceIntensity(0.5);  // 10%
        double winddirection = randomGauss(random, 90, 30);
        opts.setWindDirection(Math.toRadians(winddirection)); // 90+-30 deg
        System.out.println("Cond: windDirection: " + winddirection + "degrees");
        opts.setLaunchIntoWind(true);  // 90+-30 deg
        System.out.println("Cond: Launch Into Wind");

        opts.setLaunchLongitude(-109); // -109E
        opts.setLaunchLatitude(32.9); // 32.9N
        opts.setLaunchAltitude(4848*0.3048); // 4848 ft

        opts.setISAAtmosphere(false);

        double temperature = randomGauss(random, 31.22, 10.51 * 5 / 9);
        opts.setLaunchTemperature(temperature + 273.15);  // 31.22 +- 1 Celcius (88.2 F) in Temperature
        System.out.println("Cond: Temperature: " + temperature / 5 * 9 + 32 + "F");

        double pressure = randomGauss(random, 1008, 3.938);
        opts.setLaunchPressure(pressure * 100);  // 1008 mbar +- 1 in Pressure
        System.out.println("Cond: Pressure: " + pressure + "mbar");

        sim.simulate();

        double maxAltitude = sim.getSimulatedData().getMaxAltitude();
        double maxMach = sim.getSimulatedData().getMaxMachNumber();

        System.out.println("Simulation " + ": apogee " + maxAltitude + " mach " + maxMach);
    }

    /**
     * Choose a random number from a Gaussian distribution with a given mean and standard deviation
     * @param random Random number generator
     * @param mu Mean
     * @param sigma Standard deviation
     */
    private static double randomGauss(Random random, double mu, double sigma) {
        return random.nextGaussian() * sigma + mu;
    }
}
