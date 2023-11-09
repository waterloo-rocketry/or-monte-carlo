package com.waterloorocketry.openrocket_monte_carlo;

import java.util.Collection;

public class Statistics {
    public static class Sample {
        private final double mean;
        private final double standardDeviation;

        private Sample(double mean, double standardDeviation) {
            this.mean = mean;
            this.standardDeviation = standardDeviation;
        }

        /**
         * Returns the mean value of the data
         */
        public double getMean() {
            return mean;
        }

        /**
         * Returns the standard devation of the data
         */
        public double getStandardDeviation() {
            return standardDeviation;
        }

        @Override
        public String toString() {
            return "mean " + mean + " stddev " + standardDeviation;
        }
    }

    /**
     *
     */
    public static Sample calculateSample(Collection<Double> values) {
        if (values.size() < 2) {
            throw new IllegalArgumentException("At least 2 values must be provided");
        }
        double mean = values.stream().mapToDouble((v) -> v).average().getAsDouble();
        double standardDeviation = Math.sqrt(values.stream().mapToDouble((v) -> (v - mean) * (v - mean)).sum() / (values.size() - 1));
        return new Sample(mean, standardDeviation);
    }
}
