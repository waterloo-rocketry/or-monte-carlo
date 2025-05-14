package com.waterloorocketry.openrocket_monte_carlo;

import com.opencsv.CSVParser;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.unit.Unit;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.core.util.Chars;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * The main class that is run
 */
public class SimulationEngine {
    private final static Logger log = LoggerFactory.getLogger(SimulationEngine.class);

    private final static Unit[] CSV_SIMULATION_UNITS = {
            UnitGroup.UNITS_TEMPERATURE.getUnit(Chars.DEGREE + "C"), // temp
            UnitGroup.UNITS_PRESSURE.getUnit("mbar")}; // pressure
    private final static Unit[] CSV_WIND_LEVEL_UNITS = {
            UnitGroup.UNITS_VELOCITY.getUnit("mph"), // speed
            UnitGroup.UNITS_VELOCITY.getUnit("mph"), // stdev
            UnitGroup.UNITS_ANGLE.getUnit(String.valueOf(Chars.DEGREE))}; // direction
    private final static Unit CSV_ALTITUDE_UNIT = UnitGroup.UNITS_LENGTH.getUnit("m");
    private final static int CSV_SIMULATION_COLUMN_COUNT = 2; // skip the date column
    private final static int CSV_WIND_LEVEL_COLUMN_COUNT = 3;
    /**
     * How many simulations we should run
     */
    private final int simulationCount;

    private static final double FEET_METRES = 3.28084;

    private final List<SimulationData> data = new ArrayList<>();

    private double windDirStdDev, tempStdDev, pressureStdDev;

    @Deprecated
    SimulationEngine(OpenRocketDocument doc, int simulationCount) {
        this.simulationCount = simulationCount;
        for (int i = 0; i < simulationCount; i++) {
            Simulation sim = new Simulation(doc, doc.getRocket());
            sim.setName("Simulation " + i);
            data.add(new SimulationData(sim));
        }
    }

    SimulationEngine(OpenRocketDocument doc, File csvFile) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            CSVParser parser = new CSVParser();
            String[] header = parser.parseLine(reader.readLine());

            List<Double> altitudes = new ArrayList<>();
            for (int i = CSV_SIMULATION_COLUMN_COUNT + 1; i < header.length; i+= CSV_WIND_LEVEL_COLUMN_COUNT)
                altitudes.add(CSV_ALTITUDE_UNIT.fromUnit(Double.parseDouble(header[i])));
            log.info("Loaded wind level altitudes: {}", altitudes);

            String row;
            while ((row = reader.readLine()) != null) {
                String[] rawData = parser.parseLine(row);

                String date = rawData[0];
                double[] simData = Arrays.stream(rawData).skip(1) // skip date
                        .mapToDouble(Double::parseDouble).toArray();

                // convert to OR internal units (SI units)
                for (int i = 0; i < CSV_SIMULATION_COLUMN_COUNT; i++)
                    simData[i] = CSV_SIMULATION_UNITS[i].fromUnit(simData[i]);

                for (int i = CSV_SIMULATION_COLUMN_COUNT; i < simData.length; i++)
                    simData[i] = CSV_WIND_LEVEL_UNITS[i % CSV_WIND_LEVEL_COLUMN_COUNT].fromUnit(simData[i]);

                log.debug("Creating simulation {}", date);
                Simulation simulation = new Simulation(doc, doc.getRocket());
                simulation.setName(date);
                simulation.getOptions().setLaunchTemperature(simData[0]);
                simulation.getOptions().setLaunchPressure(simData[1]);

                MultiLevelPinkNoiseWindModel windModel = simulation.getOptions().getMultiLevelWindModel();
                for (int i = 0; i < altitudes.size(); i++) {
                    windModel.addWindLevel(altitudes.get(i),
                            simData[i * CSV_WIND_LEVEL_COLUMN_COUNT],
                            simData[i * CSV_SIMULATION_COLUMN_COUNT + 2],
                            simData[i * CSV_SIMULATION_COLUMN_COUNT + 1]);
                }

                data.add(new SimulationData(simulation));
            }
        }
        this.simulationCount = data.size();
    }

    SimulationEngine(OpenRocketDocument doc, int simulationCount,
                     double windDirStdDev, double tempStdDev, double pressureStdDev) {
        this.simulationCount = simulationCount;
        this.windDirStdDev = windDirStdDev;
        this.tempStdDev = tempStdDev;
        this.pressureStdDev = pressureStdDev;

        for (int i = 0; i < simulationCount; i++) {
            Simulation sim = new Simulation(doc, doc.getRocket());
            sim.setName("Simulation " + i);
            data.add(new SimulationData(sim));
        }
    }

    public Simulation[] getSimulations() {
        return data.stream().map(SimulationData::getSimulation).toArray(Simulation[]::new);
    }

    public List<SimulationData> getData() {
        return data;
    }

    public List<SimulationData> processSimulationData() {
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

        log.info("Data over {} runs:", simulationCount);
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

    public void exportToCSV(File csvFile) {
        // Write all simulation data to CSV
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {
            // Write comprehensive header
            writer.write("Simulation,Initial Stability,Min Stability,Max Stability,Apogee Stability,Apogee (m),Max Mach,Windspeed (mph),Wind Direction (deg),Temperature (F),Pressure (mbar)\n");

            // Write data for each simulation
            for (int i = 0; i < data.size(); i++) {
                SimulationData simData = data.get(i);

                writer.write(String.format("%d,%f,%f,%f,%f,%f,%f\n",
                        i+1,
                        simData.getInitStability(),
                        simData.getMinStability(),
                        simData.getMaxStability(),
                        simData.getApogeeStability(),
                        simData.getApogee(),
                        simData.getMaxMachNumber()));
            }
        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
        }
    }

    public void generateMonteCarloSimulationConditions() {
        for (Simulation sim : getSimulations())
            configureSimulationOptions(sim.getOptions());
    }

    /**
     * Set the options for the flight simulation
     * @param opts The options object
     */
    private void configureSimulationOptions(SimulationOptions opts) {
        Random random = new Random();

        for (MultiLevelPinkNoiseWindModel.LevelWindModel windLevel : opts.getMultiLevelWindModel().getLevels()) {
            double windSpeed = randomGauss(random, windLevel.getSpeed(), windLevel.getStandardDeviation());
            windLevel.setSpeed(windSpeed);
            log.debug("Cond @ {}: Avg WindSpeed: {}mph", windLevel.getAltitude(), windSpeed);

            double windDirection = randomGauss(random, windLevel.getDirection(), windDirStdDev);
            opts.getAverageWindModel().setDirection(Math.toRadians(windDirection));
            log.debug("Cond @ {}: windDirection: {}degrees", windLevel.getAltitude(), windDirection);
        }

        double temperature = randomGauss(random, opts.getLaunchTemperature(), tempStdDev);
        opts.setLaunchTemperature(temperature);
        log.debug("Cond: Temperature: {}F", temperature / 5 * 9);

        double pressure = randomGauss(random, opts.getLaunchPressure(), pressureStdDev);
        opts.setLaunchPressure(pressure);
        log.debug("Cond: Pressure: {}mbar", pressure);
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
