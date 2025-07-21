package com.waterloorocketry.openrocket_monte_carlo;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.unit.Unit;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.core.util.Chars;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Relevant data collected from run one of a simulation
 */
public class SimulationData {
    private final static Logger log = LoggerFactory.getLogger(SimulationData.class);

    private Simulation simulation;
    private final String name;
    private double apogee;
    private final List<String> branchName = new ArrayList<>();
    private final List<Double> minStability = new ArrayList<>();
    private final List<Double> maxStability = new ArrayList<>();
    private final List<Double> apogeeStability = new ArrayList<>();
    private final List<Double> initStability = new ArrayList<>();
    private final List<Double> landingLatitude = new ArrayList<>();
    private final List<Double> landingLongitude = new ArrayList<>();
    private final List<Double> eastPosLanding = new ArrayList<>();
    private final List<Double> northPosLanding = new ArrayList<>();
    private double maxVelocity;
    private double maxMachNumber;
    private double maxWindSpeed;
    private double maxWindDirection;
    private final double temperature;
    private final double pressure;
    private boolean hasData = false;

    public SimulationData(Simulation simulation)  {
        this.simulation = simulation;
        this.name = simulation.getName();

        Optional<MultiLevelPinkNoiseWindModel.LevelWindModel> maxWindSpdLevel = simulation.getOptions().getMultiLevelWindModel().getLevels().stream()
                .max(Comparator.comparingDouble(MultiLevelPinkNoiseWindModel.LevelWindModel::getSpeed));
        maxWindSpeed = 0;
        maxWindDirection = 0;
        if (maxWindSpdLevel.isPresent()) {
            maxWindSpeed = maxWindSpdLevel.get().getSpeed();
            maxWindDirection = maxWindSpdLevel.get().getDirection();
        }

        this.temperature = simulation.getOptions().getLaunchTemperature();
        this.pressure = simulation.getOptions().getLaunchPressure();
    }

    // process simulated data, and removes the underlying simulation object to save memory
    public void processData() throws SimulationException {
        if (!simulation.hasSimulationData()) throw new SimulationException("No simulation data recorded. Run a simulation first");
        log.info("Processing data for simulation {}",  simulation.getName());

        FlightData data = simulation.getSimulatedData();

        apogee = data.getMaxAltitude();
        maxVelocity = data.getMaxVelocity();
        maxMachNumber = data.getMaxMachNumber();

        List<FlightDataBranch> flightDataBranches = data.getBranches();

        for (FlightDataBranch branch : flightDataBranches) {
            // the flight data consists of multiple lists of values calculated at each step of the simulation
            // so we look through all this data to get what we need

            List<Double> time = branch.get(FlightDataType.TYPE_TIME);
            List<Double> lat = branch.get(FlightDataType.TYPE_LATITUDE);
            List<Double> lng = branch.get(FlightDataType.TYPE_LONGITUDE);
            List<Double> eastPos = branch.get(FlightDataType.TYPE_POSITION_X);
            List<Double> northPos = branch.get(FlightDataType.TYPE_POSITION_Y);

            double landingTime = branch.getEvents().stream()
                    .filter(e -> e.getType() == FlightEvent.Type.GROUND_HIT).findFirst()
                    .orElseThrow().getTime();
            double apogeeTime = data.getTimeToApogee();

            int apogeeIndex = Collections.binarySearch(time, apogeeTime);
            int landingIndex = Collections.binarySearch(time, landingTime);
            if (apogeeIndex < 0) {
                throw new SimulationException("Time to apogee does not correspond to a valid index");
            }
            if (landingIndex < 0) {
                throw new SimulationException("Time to landing does not correspond to a valid index");
            }

            List<Double> stability = branch.get(FlightDataType.TYPE_STABILITY);

            double minStability = Double.NaN;
            double initStability = Double.NaN;
            for (int i = 0; i < time.size(); i++) {
                Double s = stability.get(i);
                // as per the previous implementation, stop considering stability 2s before apogee
                // as well, stability will be NaN if the launch rod is not cleared or the forces are not
                if (time.get(i) + 10 <= apogeeTime && !s.isNaN()) {
                    if (Double.isNaN(minStability) || s < minStability) {
                        minStability = s;
                    }
                }
                if (Double.isNaN(initStability) && !s.isNaN()) {
                    initStability = s;
                }

                if (!Double.isNaN(initStability) && time.get(i) > apogeeTime) break;
            }
            this.branchName.add(branch.getName());
            this.minStability.add(minStability);
            this.maxStability.add(branch.getMaximum(FlightDataType.TYPE_STABILITY));
            this.initStability.add(initStability);

            this.apogeeStability.add(stability.get(apogeeIndex));
            this.landingLatitude.add(lat.get(landingIndex));
            this.landingLongitude.add(lng.get(landingIndex));
            this.eastPosLanding.add(eastPos.get(landingIndex));
            this.northPosLanding.add(northPos.get(landingIndex));
        }

        this.hasData = true;
        this.simulation = null; // remove the simulation object to save space
    }

    /**
     * @return Underlying OpenRocket simulation object
     * Should not be used after process call
     */
    public Simulation getSimulation() {
        return simulation;
    }

    public boolean hasData() {
        return hasData;
    }

    // branch dependent values
    public List<String> getBranchName() {
        return branchName;
    }
    public List<Double> getMinStability() {
        return minStability;
    }
    public List<Double> getMaxStability() {
        return maxStability;
    }
    public List<Double> getApogeeStability() {
        return apogeeStability;
    }
    public List<Double> getInitStability() {
        return initStability;
    }
    public List<Double> getLandingLatitude() {
        return landingLatitude;
    }
    public List<Double> getLandingLongitude() {
        return landingLongitude;
    }
    public List<Double> getEastPosLanding(){
        return eastPosLanding;
    }
    public List<Double> getNorthPosLanding(){
        return northPosLanding;
    }


    // global values
    public String getName() {
        return name;
    }
    public double getApogee() {
        return apogee;
    }
    public double getMaxVelocity() {
        return maxVelocity;
    }
    public double getMaxMachNumber() {
        return maxMachNumber;
    }
    public double getMaxWindSpeed() {
        return maxWindSpeed;
    }
    public double getMaxWindDirection() {
        return maxWindDirection;
    }
    public double getTemperature() {
        return temperature;
    }
    public double getPressure() {
        return pressure;
    }

    // converted values
    public List<Double> getEastPostLandingInFeet() {
        Unit ftUnit = UnitGroup.UNITS_LENGTH.getUnit("ft");
        return this.getEastPosLanding().stream().map(ftUnit::toUnit).collect(Collectors.toList());
    }
    public List<Double> getNorthPostLandingInFeet() {
        Unit ftUnit = UnitGroup.UNITS_LENGTH.getUnit("ft");
        return this.getNorthPosLanding().stream().map(ftUnit::toUnit).collect(Collectors.toList());
    }

    public double getApogeeInFeet() {
        return UnitGroup.UNITS_LENGTH.getUnit("ft")
                .toUnit(this.getApogee());
    }
    public double getTemperatureInCelsius() {
        return UnitGroup.UNITS_TEMPERATURE.getUnit(Chars.DEGREE + "C")
                .toUnit(this.getTemperature());
    }
    public double getPressureInMBar() {
        return UnitGroup.UNITS_PRESSURE.getUnit("mbar")
                .toUnit(this.getPressure());
    }
}
