package com.waterloorocketry.openrocket_monte_carlo.aspect;


import info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel;
import info.openrocket.core.models.wind.PinkNoiseWindModel;
import info.openrocket.core.unit.Unit;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.core.util.TextLineReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static info.openrocket.core.util.Chars.DEGREE;


privileged aspect PinkNoiseWindModelAspect {
    private final static Logger logger = LoggerFactory.getLogger(PinkNoiseWindModelAspect.class);

    // add new field to PinkNoiseWindModel
    private double PinkNoiseWindModel.windDirStdDev = 0.0;

    public double info.openrocket.core.models.wind.PinkNoiseWindModel.getWindDirStdDev() {
        return this.windDirStdDev;
    }

    public void info.openrocket.core.models.wind.PinkNoiseWindModel.setWindDirStdDev(double value) {
        this.windDirStdDev = value;
    }

    // ImportLevelsFromCSV (L204)
    // This is mostly copied from the original method, with additions to handle the new windDirStdDev column
    // changes are marked with INJECT
    void around(MultiLevelPinkNoiseWindModel model, File file, String fieldSeparator,
                String altitudeColumn, String speedColumn,
                String directionColumn, String stdDeviationColumn,
                Unit altitudeUnit, Unit speedUnit,
                Unit directionUnit, Unit stdDeviationUnit,
                boolean hasHeaders):
            execution(* info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel.importLevelsFromCSV(..)) &&
                    args(file, fieldSeparator, altitudeColumn, speedColumn,
                            directionColumn, stdDeviationColumn,
                            altitudeUnit, speedUnit,
                            directionUnit, stdDeviationUnit,
                            hasHeaders) &&
                    target(model) {
        logger.debug("Importing levels from CSV with Wind Direction StdDev");

        String line;

        // Clear the current levels
        model.clearLevels();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            TextLineReader textLineReader = new TextLineReader(reader);
            // Map column indices
            // INJECT: add windDirStdDevIndex
            int altIndex, speedIndex, dirIndex, stddevIndex = -1, windDirStdDevIndex = -1;

            if (hasHeaders) {
                // Read the first line as a header
                try {
                    line = textLineReader.next();
                } catch (NoSuchElementException e) {
                    throw new IllegalArgumentException(MultiLevelPinkNoiseWindModel.trans.get(
                            "MultiLevelPinkNoiseWindModel.msg.importLevelsError.EmptyFile"));
                }

                String[] headers = line.split(fieldSeparator, -1);  // -1 to keep empty trailing fields

                // Find column indices by name
                List<String> headersList = Arrays.asList(headers);
                altIndex = model.findColumnIndex(headersList, altitudeColumn, "altitude", true);
                speedIndex = model.findColumnIndex(headersList, speedColumn, "speed", true);
                dirIndex = model.findColumnIndex(headersList, directionColumn, "direction", true);
                // Standard deviation is optional
                if (!stdDeviationColumn.isEmpty()) {
                    stddevIndex = model.findColumnIndex(headersList, stdDeviationColumn, "standard deviation", false);
                }

                // INJECT: find windDirStdDev column index
                windDirStdDevIndex =
                        model.findColumnIndex(headersList, "windDirStdDev", "wind direction standard deviation", false);
            } else {
                // No headers, parse column indices directly
                try {
                    altIndex = Integer.parseInt(altitudeColumn);
                    speedIndex = Integer.parseInt(speedColumn);
                    dirIndex = Integer.parseInt(directionColumn);
                    stddevIndex = stdDeviationColumn.isEmpty() ? -1 : Integer.parseInt(stdDeviationColumn);
                    windDirStdDevIndex = 4; // INJECT: fixed index for windDirStdDev
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(MultiLevelPinkNoiseWindModel.trans.get(
                            "MultiLevelPinkNoiseWindModel.msg.importLevelsError.InvalidColumnIndex"));
                }
            }

            // Read data rows
            int lineNumber = hasHeaders ? 1 : 0;
            try {
                while (true) {
                    line = textLineReader.next();
                    lineNumber++;

                    // Skip empty lines
                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    String[] values = line.split(fieldSeparator, -1);  // -1 to keep empty trailing fields

                    // Check if we have enough columns
                    int maxColumnIndex = Math.max(Math.max(altIndex, speedIndex),
                            Math.max(dirIndex, Math.max(stddevIndex, Math.max(windDirStdDevIndex, 0))));
                    if (maxColumnIndex >= values.length) {
                        throw new IllegalArgumentException(String.format(
                                MultiLevelPinkNoiseWindModel.trans.get(
                                        "MultiLevelPinkNoiseWindModel.msg.importLevelsError.NotEnoughColumnsInLine"),
                                lineNumber));
                    }

                    // Extract and convert values
                    double altitude = model.extractDoubleAndConvert(values, altIndex, "altitude", altitudeUnit);
                    double speed = model.extractDoubleAndConvert(values, speedIndex, "speed", speedUnit);
                    double direction = model.extractDoubleAndConvert(values, dirIndex, "direction", directionUnit);

                    // Standard deviation is optional
                    Double stddev = null;
                    if (stddevIndex >= 0 && stddevIndex < values.length && !values[stddevIndex].trim().isEmpty()) {
                        stddev = model.extractDoubleAndConvert(values, stddevIndex, "standard deviation",
                                stdDeviationUnit);
                    }

                    // INJECT: extract wind direction standard deviation
                    Double windDirStdDev = null;
                    if (windDirStdDevIndex >= 0 && windDirStdDevIndex < values.length &&
                            !values[windDirStdDevIndex].trim().isEmpty()) {
                        windDirStdDev = model.extractDoubleAndConvert(values, windDirStdDevIndex,
                                "wind direction standard deviation", UnitGroup.UNITS_ANGLE.getUnit("" + DEGREE));
                    }

                    // Add the wind level
                    // INJECT: use our custom overloaded addWindLevel method
                    model.addWindLevel(altitude, speed, direction, stddev, windDirStdDev);
                }
            } catch (NoSuchElementException ignore) {
                // Nothing to do here, just means we reached the end of the file
            }

            // Sort levels by altitude
            model.sortLevels();

            // Check if we have at least one level
            if (model.getLevels().isEmpty()) {
                throw new IllegalArgumentException(MultiLevelPinkNoiseWindModel.trans.get(
                        "MultiLevelPinkNoiseWindModel.msg.importLevelsError.NoValidData"));
            }

        } catch (IOException e) {
            throw new IllegalArgumentException(MultiLevelPinkNoiseWindModel.trans.get(
                    "MultiLevelPinkNoiseWindModel.msg.importLevelsError.CouldNotLoadFile") + " '"
                    + file.getName() + "'");
        }
    }

    // New overloaded method to add wind level with wind direction stddev (L45)
    public void info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel.addWindLevel(double altitude,
                                                                                           double speed,
                                                                                           double direction,
                                                                                           Double standardDeviation,
                                                                                           Double windDirStdDev) {
        // Delegate to the existing 4-arg implementation to insert the level
        this.addWindLevel(altitude, speed, direction, standardDeviation);

        logger.debug("Adding wind level with Wind Direction StdDev: {}", windDirStdDev);

        for (info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel.LevelWindModel lvl : this.levels) {
            // original method guarantees unique altitudes
            if (Double.compare(lvl.getAltitude(), altitude) == 0) {
                lvl.setWindDirStdDev(windDirStdDev);
                break;
            }
        }

        fireChangeEvent();
    }

}

