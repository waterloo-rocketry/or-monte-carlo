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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * The main class that is run
 */
public class Main {
    /**
     * File to load rocket from
     */
    private static final String ROCKET_FILE = "./rockets/c31a.ork";
    /**
     * File to load rocket thrust curve data from
     */
    private static final String THRUST_CURVE_FILE = "./rockets/Kismet_v4_C2-2.rse";
    /**
     * How many simulations we should run
     */
    private static final int SIMULATION_COUNT = 1000;

    private static final double FEET_METRES = 3.28084;

    /**
     * Entry for OpenRocket Monte Carlo
     * @param args Command line arguments
     */
    public static void main(String[] args) throws Exception {
        initializeOpenRocket();
        List<File> thrustCurveFiles = new ArrayList<>(getOpenRocketPreferences().getUserThrustCurveFiles());
        thrustCurveFiles.add(new File(THRUST_CURVE_FILE));
        getOpenRocketPreferences().setUserThrustCurveFiles(thrustCurveFiles);

        File file = new File(ROCKET_FILE);
        GeneralRocketLoader loader = new GeneralRocketLoader(file);

        OpenRocketDocument doc = loader.load();

        long startTime = System.currentTimeMillis();

        ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Callable<SimulationData>> callables = new ArrayList<>();
        for (int i = 1; i <= SIMULATION_COUNT; i++) {
            callables.add(() -> runSimulation(doc));
        }
        List<Future<SimulationData>> futures = service.invokeAll(callables);
        List<SimulationData> data = new ArrayList<>();
        for (Future<SimulationData> future : futures) {
            data.add(future.get());
        }
        service.shutdown();

        Statistics.Sample apogee = Statistics.calculateSample(
                data.stream().map(SimulationData::getApogee).map((v) -> v * FEET_METRES).collect(Collectors.toList()));
        double minStability = data.stream().mapToDouble(SimulationData::getMinStability).min().getAsDouble();
        double maxStability = data.stream().mapToDouble(SimulationData::getMaxStability).max().getAsDouble();
        Statistics.Sample initStability = Statistics.calculateSample(
                data.stream().map(SimulationData::getInitStability).collect(Collectors.toList()));
        double lowInitStabilityPercentage = (double) data.stream().mapToDouble(SimulationData::getInitStability)
                .filter((stability) -> stability < 1.5).count() / data.size();
        Statistics.Sample apogeeStability = Statistics.calculateSample(
                data.stream().map(SimulationData::getApogeeStability).collect(Collectors.toList()));
        Statistics.Sample maxMach = Statistics.calculateSample(
                data.stream().map(SimulationData::getMaxMachNumber).collect(Collectors.toList()));

        long calculationTime = System.currentTimeMillis() - startTime;

        System.out.println("Data over " + SIMULATION_COUNT + " runs:");
        System.out.println("Calculation took " + calculationTime + " ms");
        System.out.println("Apogee (ft): " + apogee);
        System.out.println("Max mach number: " + maxMach);
        System.out.println("Min stability: " + minStability);
        System.out.println("Max stability: " + maxStability);
        System.out.println("Apogee stability: " + apogeeStability);
        System.out.println("Initial stability: " + initStability);
        System.out.println("Percentage of initial stability less than 1.5: " + lowInitStabilityPercentage);
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
    private static SimulationData runSimulation(OpenRocketDocument doc) throws SimulationException {
        Simulation sim = new Simulation(doc, doc.getRocket());
        configureSimulationOptions(sim.getOptions());
        sim.simulate();
        return new SimulationData(sim.getSimulatedData());
    }

    /**
     * Set the options for the flight simulation
     * @param opts The options object
     */
    private static void configureSimulationOptions(SimulationOptions opts) {
        Random random = new Random();

        // Units are in m/s so conversion needed

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
