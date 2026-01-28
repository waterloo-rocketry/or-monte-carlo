package com.waterloorocketry.openrocket_monte_carlo;

import com.opencsv.CSVParser;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel;
import info.openrocket.core.models.wind.WindModelType;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.simulation.extension.SimulationExtension;
import info.openrocket.core.unit.Unit;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.core.util.Chars;
import info.openrocket.core.util.GeodeticComputationStrategy;
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
    /**
     * How many simulations we should run
     */
    public final int simulationCount;
    private final Configurator config = Configurator.getInstance();
    private final boolean keepSimulationObject = config.isKeepSimulationObject();
    private final OpenRocketDocument document;
    private final List<SimulationData> data = new ArrayList<>();

    private double tempStdDev, pressureStdDev;

    /**
     * Creates a SimulationEngine with simulations specified by the given csvFile
     *
     * @param document OpenRocket document to be used with the simulation
     * @param csvFile  CSV file that specifies simulation conditions
     * @throws Exception On CSV parse fail
     * @see SimulationEngine#CSV_SIMULATION_UNITS
     * @see SimulationEngine#CSV_WIND_LEVEL_UNITS
     * @see SimulationEngine#CSV_ALTITUDE_UNIT
     * @see SimulationEngine#CSV_SIMULATION_COLUMN_COUNT
     * @see SimulationEngine#CSV_WIND_LEVEL_COLUMN_COUNT
     */
    SimulationEngine(OpenRocketDocument document, File csvFile) throws Exception {
        this.document = document;
        Simulation defaultSimulation = this.generateDefaultSimulation();
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            CSVParser parser = new CSVParser();
            String[] header = parser.parseLine(reader.readLine());

            List<Double> altitudes = new ArrayList<>();
            for (int i = CSV_SIMULATION_COLUMN_COUNT + 1; i < header.length; i += CSV_WIND_LEVEL_COLUMN_COUNT)
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
                    simData[i] = CSV_WIND_LEVEL_UNITS[(i - CSV_SIMULATION_COLUMN_COUNT) %
                            CSV_WIND_LEVEL_COLUMN_COUNT].fromUnit(simData[i]);

                log.info("Creating simulation {}", date);
                Simulation simulation = new Simulation(document, document.getRocket());
                simulation.copySimulationOptionsFrom(defaultSimulation.getOptions()); // copy default options
                simulation.setName(date);
                simulation.getOptions().setLaunchTemperature(simData[0]);
                simulation.getOptions().setLaunchPressure(simData[1]);

                MultiLevelPinkNoiseWindModel windModel = simulation.getOptions().getMultiLevelWindModel();
                for (int i = 0; i < altitudes.size(); i++) {
                    windModel.addWindLevel(altitudes.get(i),
                            simData[2 + i * CSV_WIND_LEVEL_COLUMN_COUNT],
                            simData[2 + i * CSV_WIND_LEVEL_COLUMN_COUNT + 2],
                            simData[2 + i * CSV_WIND_LEVEL_COLUMN_COUNT + 1]);
                }

                SimulationData simulationData = new SimulationData(simulation);
                log.debug(simulationData.toString());

                data.add(simulationData);
            }
        }
        this.simulationCount = data.size();
    }

    /**
     * Creates a SimulationEngine with the passed values. Does not create the simulation objects.
     * Must call createMonteCarloSimulations to finish initialization.
     *
     * @param document        OpenRocket document to be used with the simulation
     * @param simulationCount Number of simulations
     * @param tempStdDev      Temperature standard deviation
     * @param pressureStdDev  Pressure standard deviation
     * @see SimulationEngine#createMonteCarloSimulations(Simulation)
     */
    SimulationEngine(OpenRocketDocument document, int simulationCount, double tempStdDev, double pressureStdDev) {
        this.document = document;
        this.simulationCount = simulationCount;
        this.tempStdDev = tempStdDev;
        this.pressureStdDev = pressureStdDev;
    }

    /**
     * Creates a SimulationEngine with existing simulations in the document
     *
     * @param document OpenRocket document to be used with the simulation
     */
    SimulationEngine(OpenRocketDocument document) {
        this.document = document;

        List<Simulation> sims = document.getSimulations();
        this.simulationCount = sims.size();

        for (Simulation sim : sims) {
            data.add(new SimulationData(sim));
        }
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

    /**
     * Creates simulations with randomized conditions based on referenceSim and provided values at construct time
     *
     * @param referenceSim Reference simulation to copy base conditions, extensions from
     * @implNote Clears existing simulations
     * @see SimulationEngine#configureMonteCarloSimulationOptions(SimulationOptions)
     */
    public void createMonteCarloSimulations(Simulation referenceSim) {
        data.clear();
        for (int i = 0; i < simulationCount; i++) {
            Simulation sim = new Simulation(document, document.getRocket());
            sim.setName("Simulation " + i);
            log.info("Generating conditions for {}", sim.getName());

            sim.copySimulationOptionsFrom(referenceSim.getOptions());

            sim.getSimulationExtensions().clear();
            for (SimulationExtension c : referenceSim.getSimulationExtensions()) {
                sim.getSimulationExtensions().add(c.clone());
            }

            configureMonteCarloSimulationOptions(sim.getOptions());
            data.add(new SimulationData(sim));
        }
    }

    /**
     * Set the Monte-Carlo conditions for the flight simulation
     *
     * @param opts The SimulationOptions object of the simulation
     */
    private void configureMonteCarloSimulationOptions(SimulationOptions opts) {

        for (MultiLevelPinkNoiseWindModel.LevelWindModel windLevel : opts.getMultiLevelWindModel().getLevels()) {
            double windSpeed = randomGauss(windLevel.getSpeed(), windLevel.getStandardDeviation());
            windLevel.setSpeed(windSpeed);
            log.debug("Cond @ {}: Avg WindSpeed: {}m/s", windLevel.getAltitude(), windSpeed);
            windLevel.setStandardDeviation(0.0); // disable openRocket's own randomness

            double windDirection = randomGauss(windLevel.getDirection(), windLevel.getWindDirStdDev());
            windLevel.setDirection(Math.toRadians(windDirection));
            log.debug("Cond @ {}: windDirection: {}rad with stdDev: {}rad",
                    windLevel.getAltitude(), windDirection, windLevel.getWindDirStdDev());
        }

        double temperature = randomGauss(opts.getLaunchTemperature(), tempStdDev);
        opts.setLaunchTemperature(temperature);
        log.debug("Cond: Temperature: {}K", temperature);

        double pressure = randomGauss(opts.getLaunchPressure(), pressureStdDev);
        opts.setLaunchPressure(pressure);
        log.debug("Cond: Pressure: {}Pa", pressure);
    }

    /**
     * Generates a reference simulation with default values. Use createMonteCarloSimulations to create
     * Monte-Carlo simulations based on this reference simulation.
     *
     * @return Reference simulation with default values
     * @see SimulationEngine#createMonteCarloSimulations(Simulation)
     */
    public Simulation generateDefaultSimulation() {
        Simulation defaultSimulation = new Simulation(document, document.getRocket());
        defaultSimulation.setName("Monte-Carlo Simulation");
        SimulationOptions opts = defaultSimulation.getOptions();

        opts.setLaunchLatitude(config.getLaunchLatitude());
        opts.setLaunchLongitude(config.getLaunchLongitude());
        opts.setLaunchAltitude(config.getLaunchAltitude());

        opts.setISAAtmosphere(false);
        opts.setWindModelType(WindModelType.MULTI_LEVEL);
        opts.getMultiLevelWindModel().clearLevels();

        opts.setLaunchRodLength(config.getLaunchRodLength());
        opts.setLaunchIntoWind(config.isLaunchIntoWind());
        opts.setLaunchRodAngle(config.getLaunchRodAngle());
        opts.setLaunchRodDirection(config.getLaunchRodDirection());

        opts.setGeodeticComputation(GeodeticComputationStrategy.WGS84);
        opts.setMaxSimulationTime(config.getMaxSimulationTime());


        return defaultSimulation;
    }

    /**
     * Gets a range of simulations
     *
     * @param start starting index
     * @param size  number of simulations following the start to return
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
                    d.processData(keepSimulationObject);
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }

    public void exportToCSV(File csvFile) {
        if (data.isEmpty()) {
            log.warn("No data has been generated, ignoring CSV export");
            return;
        }
        // Write all simulation data to CSV
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {
            // Write comprehensive header
            StringBuilder header = new StringBuilder(
                    "Simulation,Max Windspeed (mph),Wind Direction (deg),Temperature (°C),Pressure (mbar),Apogee (ft),Max Mach");

            // add branch-specific headers
            String[] branchHeaders =
                    {"Initial Stability", "Min Stability", "Max Stability", "Apogee Stability",
                            "Landing Latitude (deg N)",
                            "Landing Longitude (deg E)", "Position East of Launch (ft)",
                            "Position North of Launch (ft)",
                            "Lateral Velocity at Apogee (m/s)"};
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
                row.append(simData.getMaxWindSpeedInMPH()).append(",");
                row.append(simData.getMaxWindDirectionInDegrees()).append(",");
                row.append(simData.getTemperatureInCelsius()).append(",");
                row.append(simData.getPressureInMBar()).append(",");
                row.append(simData.getApogeeInFeet()).append(",");
                row.append(simData.getMaxMachNumber()).append(",");

                for (int i = 0; i < branches; i++) { // branch-specific data
                    row.append(simData.getInitStability().get(i)).append(",");
                    row.append(simData.getMinStability().get(i)).append(",");
                    row.append(simData.getMaxStability().get(i)).append(",");
                    row.append(simData.getApogeeStability().get(i)).append(",");
                    row.append(simData.getLandingLatitude().get(i)).append(",");
                    row.append(simData.getLandingLongitude().get(i)).append(",");
                    row.append(simData.getEastPostLandingInFeet().get(i)).append(",");
                    row.append(simData.getNorthPostLandingInFeet().get(i)).append(",");
                    row.append(simData.getApogeeLateralVelocity().get(i)).append(",");
                }
                row.append("\n");
                writer.write(row.toString());
            }
        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
        }
    }
}
