package com.waterloorocketry.openrocket_monte_carlo;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class SimulationTableModel extends AbstractTableModel implements Listener {
    private final List<SimulationData> data = new ArrayList<>();
    private final String[] columnNames =
            {"Simulation Name", "Wind Speed(mph)", "Wind Direction(°)", "Temperature(°C)", "Pressure(mbar)",
                    "Apogee(ft)", "Max Velocity(m/s)", "Min Stability"};

    public void addSimulation(SimulationData simulation) {
        simulation.addListener(this);
        data.add(simulation);
        fireTableDataChanged();
    }

    public void addSimulations(List<SimulationData> simulations) {
        for (SimulationData simulation : simulations) {
            addSimulation(simulation);
        }
    }

    public void clearSimulations() {
        data.clear();
        fireTableDataChanged();
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public int getRowCount() {
        return data.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        switch (columnIndex) {
            case 0:
                return data.get(rowIndex).getName();
            case 1:
                return data.get(rowIndex).getMaxWindSpeedInMPH();
            case 2:
                return data.get(rowIndex).getMaxWindDirectionInDegrees();
            case 3:
                return data.get(rowIndex).getTemperatureInCelsius();
            case 4:
                return data.get(rowIndex).getPressureInMBar();
            case 5:
                return data.get(rowIndex).getApogeeInFeet();
            case 6:
                return data.get(rowIndex).getMaxVelocity();
            case 7:
                if (data.get(rowIndex).getMinStability().isEmpty()) {
                    return null;
                }
                return data.get(rowIndex).getMinStability().get(0);
            default:
                return null;
        }
    }

    public SimulationData getDataAt(int rowIndex) {
        return data.get(rowIndex);
    }

    @Override
    public void update() {
        fireTableDataChanged();
    }
}
