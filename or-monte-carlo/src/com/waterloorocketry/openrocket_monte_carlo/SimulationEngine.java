package com.waterloorocketry.openrocket_monte_carlo;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.simulation.SimulationOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * The main class that is run
 */
public class SimulationEngine {
    private final static Logger log = LoggerFactory.getLogger(SimulationEngine.class);
    /**
     * How many simulations we should run
     */
    private final int simulationCount;

    private static final double FEET_METRES = 3.28084;

    private final List<SimulationData> data = new ArrayList<>();

    SimulationEngine(OpenRocketDocument doc, int simulationCount) {
        this.simulationCount = simulationCount;
        for (int i = 0; i < simulationCount; i++) {
            Simulation sim = new Simulation(doc, doc.getRocket());
            SimulationConditions simulationConditions = configureSimulationOptions(sim.getOptions());
            data.add(new SimulationData(sim, simulationConditions));
        }
    }

    public Simulation[] getSimulations() {
        return data.stream().map(SimulationData::getSimulation).toArray(Simulation[]::new);
    }

    public List<SimulationData> processSimulationData() {
        // TODO: name each simulation
        for (SimulationData d : data) {
            try {
                d.processData();
            } catch (SimulationException e) {
                log.error(e.getMessage());
                return null;
            }
        }

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

        log.info("Data over " + simulationCount + " runs:");
        log.info("Apogee (ft): {}", apogee);
        log.info("Max mach number: {}", maxMach);
        log.info("Min stability: {}", minStability);
        log.info("Max stability: {}", maxStability);
        log.info("Apogee stability: {}", apogeeStability);
        log.info("Initial stability: {}", initStability);
        log.info("Percentage of initial stability less than 1.5: {}", lowInitStabilityPercentage);

        return data.stream().sorted(Comparator.comparing(SimulationData::getMinStability))
                .limit(5).toList();
    }

    /**
     * Set the options for the flight simulation
     * @param opts The options object
     */
    private static SimulationConditions configureSimulationOptions(SimulationOptions opts) {
        Random random = new Random();

        // Units are in m/s so conversion needed

        opts.setLaunchRodLength(260 * 2.54 / 100); // 260 inches (to cm) to m
        opts.setLaunchRodAngle(Math.toRadians(5)); // 5 +- 1 deg in Launch Angle

        double windSpeed = randomGauss(random, 8.449, 4.45);
        opts.getAverageWindModel().setAverage(windSpeed * 0.44707);  // 8.449 mph
        log.info("Cond: Avg WindSpeed: {}mph", windSpeed);
        opts.getAverageWindModel().setStandardDeviation(0.8449 * 0.44707);  // 4.450 mph Std.Dev of wind
        opts.getAverageWindModel().setTurbulenceIntensity(0.5);  // 10%
        double windDirection = randomGauss(random, 90, 30);
        opts.getAverageWindModel().setDirection(Math.toRadians(windDirection)); // 90+-30 deg
        log.info("Cond: windDirection: {}degrees", windDirection);
        opts.setLaunchIntoWind(true);  // 90+-30 deg
        log.info("Cond: Launch Into Wind");

        opts.setLaunchLongitude(-109); // -109E
        opts.setLaunchLatitude(32.9); // 32.9N
        opts.setLaunchAltitude(4848*0.3048); // 4848 ft

        opts.setISAAtmosphere(false);

        double temperature = randomGauss(random, 31.22, 10.51 * 5 / 9);
        opts.setLaunchTemperature(temperature + 273.15);  // 31.22 +- 1 Celcius (88.2 F) in Temperature
        log.info("Cond: Temperature: {}" + 32 + "F", temperature / 5 * 9);

        double pressure = randomGauss(random, 1008, 3.938);
        opts.setLaunchPressure(pressure * 100);  // 1008 mbar +- 1 in Pressure
        log.info("Cond: Pressure: {}mbar", pressure);

        return new SimulationConditions(windSpeed, windDirection, temperature / 5 * 9 + 32, pressure);
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
