package com.waterloorocketry.openrocket_monte_carlo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;

public class Configurator {
    private static Configurator instance;
    private final static Logger log = LoggerFactory.getLogger(Configurator.class);
    private final static String CONFIG_FILE_PATH = "config.toml";

    private int batchSize = 30;
    private double launchLatitude = 47.965378;
    private double launchLongitude = -81.873536;
    private double launchAltitude = 420.0144;
    private double launchRodLength = 9.144;
    private boolean launchIntoWind = false;
    private double launchRodAngle = 0.0872665;
    private double launchRodDirection = 4.71239;
    private int maxSimulationTime = 2400;

    private Configurator() {
        try {
            java.util.Properties props = new java.util.Properties();
            java.io.FileInputStream fis = new java.io.FileInputStream(CONFIG_FILE_PATH);
            props.load(fis);

            batchSize = Integer.parseInt(props.getProperty("batch_size", String.valueOf(batchSize)));
            launchLatitude = Double.parseDouble(props.getProperty("launch_latitude", String.valueOf(launchLatitude)));
            launchLongitude = Double.parseDouble(props.getProperty("launch_longitude", String.valueOf(launchLongitude)));
            launchAltitude = Double.parseDouble(props.getProperty("launch_altitude", String.valueOf(launchAltitude)));
            launchRodLength = Double.parseDouble(props.getProperty("launch_rod_length", String.valueOf(launchRodLength)));
            launchIntoWind = Boolean.parseBoolean(props.getProperty("launch_into_wind", String.valueOf(launchIntoWind)));
            launchRodAngle = Double.parseDouble(props.getProperty("launch_rod_angle", String.valueOf(launchRodAngle)));
            launchRodDirection = Double.parseDouble(props.getProperty("launch_rod_direction", String.valueOf(launchRodDirection)));
            maxSimulationTime = Integer.parseInt(props.getProperty("max_simulation_time", String.valueOf(maxSimulationTime)));

            fis.close();
        } catch (FileNotFoundException ex) {
            log.info("No user configuration file found, using defaults.");
            log.debug(ex.getMessage());
            return;
        } catch (IOException ex) {
            log.warn("Error reading configuration file: " + CONFIG_FILE_PATH);
            log.debug(ex.getMessage());
            System.exit(-1); // exit since we don't want to run with partial config
        }
        log.info("User configuration loaded.");
    }


    public static Configurator getInstance() {
        if (instance == null) {
            instance = new Configurator();
        }
        return instance;
    }

    public int getBatchSize() {
        return batchSize;
    }
    public double getLaunchLatitude() {
        return launchLatitude;
    }
    public double getLaunchLongitude() {
        return launchLongitude;
    }
    public double getLaunchAltitude() {
        return launchAltitude;
    }
    public double getLaunchRodLength() {
        return launchRodLength;
    }
    public boolean isLaunchIntoWind() {
        return launchIntoWind;
    }
    public double getLaunchRodAngle() {
        return launchRodAngle;
    }
    public double getLaunchRodDirection() {
        return launchRodDirection;
    }
    public int getMaxSimulationTime() {
        return maxSimulationTime;
    }
}
