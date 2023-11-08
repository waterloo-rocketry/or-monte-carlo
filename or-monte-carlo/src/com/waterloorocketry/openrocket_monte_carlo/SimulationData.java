package com.waterloorocketry.openrocket_monte_carlo;

import net.sf.openrocket.simulation.FlightData;
import net.sf.openrocket.simulation.FlightDataBranch;
import net.sf.openrocket.simulation.FlightDataType;
import net.sf.openrocket.simulation.exception.SimulationException;

import java.util.Collections;
import java.util.List;

/**
 * Relevant data collected from run one of a simulation
 */
public class SimulationData {
    private final double apogee;
    private final double minStability;
    private final double maxStability;
    private final double apogeeStability;
    private final double initStability;
    private final double maxVelocity;

    /**
     * Construct a SimulationData object from OpenRocket data
     * @param data Simulated data from OpenRocket
     */
    public SimulationData(FlightData data) throws SimulationException {
        apogee = data.getMaxAltitude();
        maxVelocity = data.getMaxVelocity();

        FlightDataBranch branch = data.getBranch(0);


        // the flight data consists of multiple lists of values calculated at each step of the simulation
        // so we look through all this data to get what we need

        List<Double> time = branch.get(FlightDataType.TYPE_TIME);
        double apogeeTime = data.getTimeToApogee();
        int apogeeIndex = Collections.binarySearch(time, apogeeTime);
        if (apogeeIndex < 0) {
            throw new SimulationException("Time to apogee does not correspond to a valid index");
        }

        List<Double> stability = branch.get(FlightDataType.TYPE_STABILITY);


        maxStability = branch.getMaximum(FlightDataType.TYPE_STABILITY);
        double minStability = Double.NaN;
        double initStability = Double.NaN;
        for (int i = 0; i < time.size(); i++) {
            Double s = stability.get(i);
            // as per the previous implementation, stop considering stability 2s before apogee
            // as well, stability will be NaN if the launch rod is not cleared or the forces are not
            if (time.get(i) + 2.0 <= apogeeTime && !s.isNaN()) {
                if (Double.isNaN(minStability) || s < minStability) {
                    minStability = s;
                }
            }
            if (Double.isNaN(initStability) && !s.isNaN()) {
                initStability = s;
            }
        }
        this.minStability = minStability;
        this.initStability = initStability;

        apogeeStability = stability.get(apogeeIndex);
    }

    public double getApogee() {
        return apogee;
    }

    public double getMinStability() {
        return minStability;
    }

    public double getMaxStability() {
        return maxStability;
    }

    public double getApogeeStability() {
        return apogeeStability;
    }

    public double getInitStability() {
        return initStability;
    }

    public double getMaxVelocity() {
        return maxVelocity;
    }
}
