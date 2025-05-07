package com.waterloorocketry.openrocket_monte_carlo;

@Deprecated
public class SimulationConditions {
    private final double windspeedMph;
    private final double windDirection;
    private final double temperatureF;
    private final double pressureMbar;

    public SimulationConditions(double windspeedMph, double windDirection, double temperatureF, double pressureMbar) {
        this.windspeedMph = windspeedMph;
        this.windDirection = windDirection;
        this.temperatureF = temperatureF;
        this.pressureMbar = pressureMbar;
    }

    @Override
    public String toString() {
        return "Conditions: Windspeed: " + windspeedMph + " mph, Wind direction: " + windDirection + " degrees, Temperature: " + temperatureF + " F, Pressure: " + pressureMbar + " mbar";
    }
}
