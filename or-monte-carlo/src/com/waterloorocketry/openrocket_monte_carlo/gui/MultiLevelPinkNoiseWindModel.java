package com.waterloorocketry.openrocket_monte_carlo.gui;

import info.openrocket.core.models.wind.PinkNoiseWindModel;
import info.openrocket.core.util.ChangeSource;
import info.openrocket.core.util.StateChangeListener;

import java.util.*;

public class MultiLevelPinkNoiseWindModel extends info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel {

    public static class LevelWindModel implements Cloneable, ChangeSource {
        private final List<StateChangeListener> listeners = new ArrayList<>();
        protected double altitude;
        protected PinkNoiseWindModel model;

        LevelWindModel(double altitude, PinkNoiseWindModel model) {
            this.altitude = altitude;
            this.model = model;
        }

        public double getAltitude() {
            return altitude;
        }

        public void setAltitude(double altitude) {
            this.altitude = altitude;
            fireChangeEvent();
        }

        public double getSpeed() {
            return model.getAverage();
        }

        public void setSpeed(double speed) {
            model.setAverage(speed);
        }

        public double getDirection() {
            return model.getDirection();
        }

        public void setDirection(double direction) {
            model.setDirection(direction);
        }

        public double getStandardDeviation() {
            return model.getStandardDeviation();
        }

        public void setStandardDeviation(double standardDeviation) {
            model.setStandardDeviation(standardDeviation);
        }

        public double getTurbulenceIntensity() {
            return model.getTurbulenceIntensity();
        }

        public void setTurbulenceIntensity(double turbulenceIntensity) {
            model.setTurbulenceIntensity(turbulenceIntensity);
        }

        public String getIntensityDescription() {
            return model.getIntensityDescription();
        }

        @Override
        public LevelWindModel clone() {
            try {
                LevelWindModel clone = (LevelWindModel) super.clone();
                clone.model = this.model.clone();
                return clone;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(); // This should never happen
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LevelWindModel that = (LevelWindModel) o;
            return Double.compare(that.altitude, altitude) == 0 &&
                    model.equals(that.model);
        }

        @Override
        public int hashCode() {
            return Objects.hash(altitude, model);
        }

        @Override
        public void addChangeListener(StateChangeListener listener) {
            listeners.add(listener);
            model.addChangeListener(listener);
        }

        @Override
        public void removeChangeListener(StateChangeListener listener) {
            listeners.remove(listener);
            model.removeChangeListener(listener);
        }

        public void fireChangeEvent() {
            EventObject event = new EventObject(this);
            // Copy the list before iterating to prevent concurrent modification exceptions.
            EventListener[] list = listeners.toArray(new EventListener[0]);
            for (EventListener l : list) {
                if (l instanceof StateChangeListener) {
                    ((StateChangeListener) l).stateChanged(event);
                }
            }
        }
    }
}
