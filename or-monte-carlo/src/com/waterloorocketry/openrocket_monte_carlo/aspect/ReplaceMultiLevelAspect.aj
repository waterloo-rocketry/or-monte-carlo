package com.waterloorocketry.openrocket_monte_carlo.aspect;

import java.awt.Window;

import com.waterloorocketry.openrocket_monte_carlo.gui.MultiLevelWindTable;
import info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel;

// Aspect that replaces calls to the original dialog constructor with calls that
// instantiate the subclass instead.
public aspect ReplaceMultiLevelAspect {
    // Match any call to the library constructor with the signature (Window, MultiLevelPinkNoiseWindModel)
    pointcut ctorCall(MultiLevelPinkNoiseWindModel model):
            call(info.openrocket.swing.gui.simulation.MultiLevelWindTable+.new(info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel))
                    && args(model);

    // Around advice that creates the subclass and returns it instead of the original
    // constructor call.
    Object around(MultiLevelPinkNoiseWindModel model): ctorCall(model) {
        System.out.println("Replacing MultiLevelWindTable with custom subclass.");
        return new MultiLevelWindTable(model);
    }
}