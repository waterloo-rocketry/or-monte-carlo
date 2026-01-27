package com.waterloorocketry.openrocket_monte_carlo.aspect;


privileged aspect LevelWindModelAspect {
    // add getter and setter for new field
    public double info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel.LevelWindModel.getWindDirStdDev() {
        return this.model.getWindDirStdDev();
    }

    public void info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel.LevelWindModel.setWindDirStdDev(
            double value) {
        this.model.setWindDirStdDev(value);
    }
}