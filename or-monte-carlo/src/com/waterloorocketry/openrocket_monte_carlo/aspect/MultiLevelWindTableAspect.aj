package com.waterloorocketry.openrocket_monte_carlo.aspect;

import info.openrocket.core.l10n.Translator;
import info.openrocket.core.startup.Application;
import info.openrocket.core.unit.Unit;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.swing.gui.adaptors.DoubleModel;
import info.openrocket.swing.gui.components.UnitSelector;
import info.openrocket.swing.gui.simulation.MultiLevelWindTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * info/openrocket/swing/gui/simulation/MultiLevelWindTable.java
 */
privileged aspect MultiLevelWindTableAspect {
    private final static Logger logger = LoggerFactory.getLogger(MultiLevelWindTableAspect.class);
    private static final Translator trans = Application.getTranslator();

    // override column definition array to add new column (L64)
    // we use around here as this is a static final array which is not modifiable when aspectJ weaves
    Object around(): get(* info.openrocket.swing.gui.simulation.MultiLevelWindTable.COLUMNS) {
        logger.debug("Injecting Wind Direction StdDev column");
        return new MultiLevelWindTable.ColumnDefinition[]{
                new MultiLevelWindTable.ColumnDefinition(trans.get("MultiLevelWindTable.col.AltitudeMSL"),
                        trans.get("MultiLevelWindTable.col.AltitudeMSL.ttip"), 100, UnitGroup.UNITS_DISTANCE),
                new MultiLevelWindTable.ColumnDefinition(trans.get("MultiLevelWindTable.col.Speed"),
                        100, UnitGroup.UNITS_WINDSPEED),
                new MultiLevelWindTable.ColumnDefinition(trans.get("MultiLevelWindTable.col.Direction"),
                        trans.get("MultiLevelWindTable.col.Direction.ttip"), 90, UnitGroup.UNITS_ANGLE),
                new MultiLevelWindTable.ColumnDefinition(trans.get("MultiLevelWindTable.col.StandardDeviation"),
                        100, UnitGroup.UNITS_WINDSPEED),
                new MultiLevelWindTable.ColumnDefinition(trans.get("MultiLevelWindTable.col.Turbulence"),
                        90, UnitGroup.UNITS_RELATIVE),
                new MultiLevelWindTable.ColumnDefinition(trans.get("MultiLevelWindTable.col.Intensity"), 85, null),
                new MultiLevelWindTable.ColumnDefinition(trans.get("MultiLevelWindTable.col.Delete"), 60, null),
                new MultiLevelWindTable.ColumnDefinition("Wind Direction StdDev", 100, UnitGroup.UNITS_ANGLE)
                // new field
        };
    }

    // declare fields to hold model and selector (L92-104)
    private final DoubleModel MultiLevelWindTable.unitWindDirStdDevModel = new DoubleModel(1.0, UnitGroup.UNITS_ANGLE);
    private UnitSelector MultiLevelWindTable.windDirStdDevUnitSelector;

    // update unit selector creation to handle new column (L233)
    UnitSelector around(info.openrocket.swing.gui.simulation.MultiLevelWindTable instance, int columnIndex,
                        UnitGroup unitGroup):
            execution(* MultiLevelWindTable.createUnitSelector(int, UnitGroup)) && args(columnIndex, unitGroup) && target(instance) {
        if (columnIndex == 7) { // new column index
            logger.debug("Creating Wind Direction StdDev UnitSelector");
            UnitSelector selector = new UnitSelector(instance.unitWindDirStdDevModel);
            instance.windDirStdDevUnitSelector = selector;
            selector.addItemListener(e -> instance.updateWindDirStdDeviationUnits(selector.getSelectedUnit()));
            return selector;
        }
        return proceed(instance, columnIndex, unitGroup);
    }

    private void info.openrocket.swing.gui.simulation.MultiLevelWindTable.updateWindDirStdDeviationUnits(Unit unit) {
        // Create a copy of the rows to avoid ConcurrentModificationException
        List<MultiLevelWindTable.LevelRow> rowsCopy = new ArrayList<>(rows);
        for (MultiLevelWindTable.LevelRow row : rowsCopy) {
            row.setWindDirStdDeviationUnit(unit);
        }

        // Notify listeners for plot update
        fireChangeEvent(unit);
    }

}
