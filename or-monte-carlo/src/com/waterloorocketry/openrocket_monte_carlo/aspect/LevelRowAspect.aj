package com.waterloorocketry.openrocket_monte_carlo.aspect;

import info.openrocket.core.unit.Unit;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.swing.gui.adaptors.DoubleModel;
import info.openrocket.swing.gui.simulation.MultiLevelWindTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.lang.reflect.Field;

/**
 * info/openrocket/swing/gui/simulation/MultiLevelWindTable$LevelRow.java
 */
privileged aspect LevelRowAspect {
    private final static Logger logger = LoggerFactory.getLogger(LevelRowAspect.class);
    // add field to LevelRow to hold wind direction std deviation value
    private DoubleModel MultiLevelWindTable.LevelRow.dmWindDirStdDev;

    // initialize our new field after LevelRow constructor (L734)
    after(info.openrocket.swing.gui.simulation.MultiLevelWindTable.LevelRow row):
            execution(info.openrocket.swing.gui.simulation.MultiLevelWindTable.LevelRow+.new(..)) && this(row) {
        MultiLevelWindTable table = getEnclosingMultiLevelWindTable(row);
        if (table == null) {
            logger.warn("Could not find enclosing MultiLevelWindTable for LevelRow");
            return;
        }

        // L738 - create the model
        row.dmWindDirStdDev = new DoubleModel(row.level, "WindDirStdDev", UnitGroup.UNITS_ANGLE, 0.0, 1.0);

        // L745 - initialize the model's current unit from the table's unit selector
        if (table.windDirStdDevUnitSelector != null) {
            row.dmWindDirStdDev.setCurrentUnit(table.windDirStdDevUnitSelector.getSelectedUnit());
        }
        // L778 - add change listener to update table when value changes
        row.dmWindDirStdDev.addChangeListener(e -> table.fireChangeEvent());

        // L789 - create UI component
        JPanel windDirStdDevGroup = row.createSpinnerOnly(row.dmWindDirStdDev, MultiLevelWindTable.COLUMNS[4].width);

        // L813 - add to the row
        ((JPanel) row).add(table.createVerticalSeparator());
        ((JPanel) row).add(windDirStdDevGroup);
    }

    public void info.openrocket.swing.gui.simulation.MultiLevelWindTable.LevelRow.setWindDirStdDeviationUnit(
            Unit unit) {
        this.dmWindDirStdDev.setCurrentUnit(unit);
    }

    // this is a hack to get the parent MultiLevelWindTable from the inner LevelRow class
    private MultiLevelWindTable getEnclosingMultiLevelWindTable(Object inner) {
        if (inner == null) return null;
        // common synthetic field name for non-static inner classes
        try {
            Field f = inner.getClass().getDeclaredField("this$0");
            f.setAccessible(true);
            Object outer = f.get(inner);
            if (outer instanceof MultiLevelWindTable) return (MultiLevelWindTable) outer;
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }

        // fallback: scan declared fields for an outer of the expected type
        for (Field f : inner.getClass().getDeclaredFields()) {
            if (MultiLevelWindTable.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    Object outer = f.get(inner);
                    if (outer instanceof MultiLevelWindTable) return (MultiLevelWindTable) outer;
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        logger.error("Could not find enclosing MultiLevelWindTable for LevelRow via reflection");
        return null;
    }
}