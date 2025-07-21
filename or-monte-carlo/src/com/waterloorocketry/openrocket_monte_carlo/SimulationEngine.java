package com.waterloorocketry.openrocket_monte_carlo;

import com.opencsv.CSVParser;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.simulation.extension.SimulationExtension;
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
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * The main class that is run
 */
public class SimulationEngine {
    private final static Random random = new Random();
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

    private final OpenRocketDocument document;
    /**
     * How many simulations we should run
     */
    public final int simulationCount;

    private static final double FEET_METRES = 3.28084;

    private final List<SimulationData> data = new ArrayList<>();

    private double windDirStdDev, tempStdDev, pressureStdDev;

    SimulationEngine(OpenRocketDocument document, File csvFile) throws Exception {
        this.document = document;
        Simulation defaultSimulation = this.generateDefaultSimulation();
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
                Simulation simulation = new Simulation(document, document.getRocket());
                simulation.copySimulationOptionsFrom(defaultSimulation.getOptions()); // copy default options
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

    SimulationEngine(OpenRocketDocument document, int simulationCount,
                     double windDirStdDev, double tempStdDev, double pressureStdDev) {
        this.document = document;
        this.simulationCount = simulationCount;
        this.windDirStdDev = windDirStdDev;
        this.tempStdDev = tempStdDev;
        this.pressureStdDev = pressureStdDev;
    }

    /**
     * Gets a range of simulations
     * @param start starting index
     * @param size number of simulations following the start to return
     * @return list of simulations
     */
    public List<Simulation> getSimulations(int start, int size) {
        return data.stream().skip(start).limit(size).map(SimulationData::getSimulation).toList();
    }

    public List<SimulationData> getData() {
        return data;
    }

    public void processSimulationData() {
        for (SimulationData d : data) {
            try {
                if (!d.hasData() && d.getSimulation().hasSimulationData()) // only process unprocessed simulations
                    d.processData();
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }

    public void summarizeSimulations() {
        Statistics.Sample apogee = Statistics.calculateSample(
                data.stream().map(SimulationData::getApogee).map((v) -> v * FEET_METRES).collect(Collectors.toList()));
        double minStability = data.stream().mapToDouble(x -> x.getMinStability().get(0)).min().orElseThrow();
        double maxStability = data.stream().mapToDouble(x -> x.getMaxStability().get(0)).max().orElseThrow();
        Statistics.Sample initStability = Statistics.calculateSample(
                data.stream().map(x -> x.getInitStability().get(0)).collect(Collectors.toList()));
        double lowInitStabilityPercentage = (double) data.stream().mapToDouble(x -> x.getInitStability().get(0))
                .filter((stability) -> stability < 1.5).count() / data.size();
        Statistics.Sample apogeeStability = Statistics.calculateSample(
                data.stream().map(x -> x.getApogeeStability().get(0)).collect(Collectors.toList()));
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

//        return data.stream().sorted(Comparator.comparing(x -> x.getMinStability().get(0)))
//                .limit(5).toList();
    }

    public void exportToCSV(File csvFile) {
        if (data.isEmpty()) {
            log.warn("No data has been generated, ignoring CSV export");
            return;
        }
        // Write all simulation data to CSV
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {
            // Write comprehensive header
            StringBuilder header = new StringBuilder("Simulation,Max Windspeed (m/s),Wind Direction (deg),Temperature (°C),Pressure (mbar),Apogee (ft),Max Mach");

            String[] branchHeaders =
                    {"Initial Stability", "Min Stability", "Max Stability", "Apogee Stability", "Landing Latitude (°N)",
                            "Landing Longitude (°E)", "Position East of Launch (ft)", "Position North of Launch (ft)"};
            int branches = data.get(0).getBranchName().size();
            for (int i = 0; i < branches; i++) {
                String branchName = data.get(0).getBranchName().get(i);
                StringBuilder branchHeader = new StringBuilder();
                for (String branchHeaderLabel : branchHeaders) {
                    branchHeader.append(",").append(branchName).append(" ").append(branchHeaderLabel);
                }
                header.append(branchHeader);
            }
            header.append("\n");

            writer.write(header.toString());

            // Write data for each simulation
            for (SimulationData simData : data) {
                StringBuilder row = new StringBuilder();
                row.append(simData.getName()).append(",");
                row.append(simData.getMaxWindSpeed()).append(",");
                row.append(simData.getMaxWindDirection()).append(",");
                row.append(simData.getTemperatureInCelsius()).append(",");
                row.append(simData.getPressureInMBar()).append(",");
                row.append(simData.getApogeeInFeet()).append(",");
                row.append(simData.getMaxMachNumber()).append(",");

                for (int i = 0; i < branches; i++) {
                    row.append(simData.getInitStability().get(i)).append(",");
                    row.append(simData.getMinStability().get(i)).append(",");
                    row.append(simData.getMaxStability().get(i)).append(",");
                    row.append(simData.getApogeeStability().get(i)).append(",");
                    row.append(simData.getLandingLatitude().get(i)).append(",");
                    row.append(simData.getLandingLongitude().get(i)).append(",");
                    row.append(simData.getEastPostLandingInFeet().get(i)).append(",");
                    row.append(simData.getNorthPostLandingInFeet().get(i)).append(",");
                }
                row.append("\n");
                writer.write(row.toString());
            }
        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
        }
    }

    public void createMonteCarloSimulationConditions(Simulation referenceSim) {
        for (int i = 0; i < simulationCount; i++) {
            Simulation sim = new Simulation(document, document.getRocket());
            sim.setName("Simulation " + i);
            log.info("Generating conditions for {}", sim.getName());

            sim.copySimulationOptionsFrom(referenceSim.getOptions());

            sim.getSimulationExtensions().clear();
            for (SimulationExtension c : referenceSim.getSimulationExtensions()) {
                sim.getSimulationExtensions().add(c.clone());
            }

            configureSimulationOptions(sim.getOptions());
            data.add(new SimulationData(sim));
        }
    }

    /**
     * Set the options for the flight simulation
     * @param opts The options object
     */
    private void configureSimulationOptions(SimulationOptions opts) {

        for (MultiLevelPinkNoiseWindModel.LevelWindModel windLevel : opts.getMultiLevelWindModel().getLevels()) {
            double windSpeed = randomGauss(windLevel.getSpeed(), windLevel.getStandardDeviation());
            windLevel.setSpeed(windSpeed);
            log.debug("Cond @ {}: Avg WindSpeed: {}m/s", windLevel.getAltitude(), windSpeed);

            double windDirection = randomGauss(windLevel.getDirection(), windDirStdDev);
            windLevel.setDirection(Math.toRadians(windDirection));
            log.debug("Cond @ {}: windDirection: {}degrees", windLevel.getAltitude(), windDirection);
        }

        double temperature = randomGauss(opts.getLaunchTemperature(), tempStdDev);
        opts.setLaunchTemperature(temperature);
        log.debug("Cond: Temperature: {}K", temperature);

        double pressure = randomGauss(opts.getLaunchPressure(), pressureStdDev);
        opts.setLaunchPressure(pressure);
        log.debug("Cond: Pressure: {}Pa", pressure);
    }

    public Simulation generateDefaultSimulation() {
        Simulation defaultSimulation = new Simulation(document, document.getRocket());
        defaultSimulation.setName("Monte-Carlo Simulation");
        SimulationOptions opts = defaultSimulation.getOptions();

        opts.setLaunchLatitude(47.58);
        opts.setLaunchLongitude(-81.87);
        opts.setLaunchAltitude(420.0144); // 1378ft

        opts.setLaunchRodLength(11.2776); // 444in
        opts.setLaunchIntoWind(false);
        opts.setLaunchRodAngle(0.0872665);
        opts.setLaunchRodDirection(1.62316);

        opts.setMaxSimulationTime(2400); // double sim time

        return defaultSimulation;
    }

    /**
     * Choose a random number from a Gaussian distribution with a given mean and standard deviation
     *
     * @param mu    Mean
     * @param sigma Standard deviation
     */
    private static double randomGauss(double mu, double sigma) {
        return SimulationEngine.random.nextGaussian() * sigma + mu;
    }
}
