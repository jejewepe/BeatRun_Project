package com.example.runtune.sensor;

import android.hardware.Sensor;
import android.hardware.SensorEvent;

public class StepTracker {
    private int totalSteps = 0;
    private int stepBuffer = 0;

    public void processStep(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            totalSteps++;
            stepBuffer++;
        }
    }

    public int getStepBuffer() {
        return stepBuffer;
    }

    public void resetBuffer() {
        stepBuffer = 0;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public void setTotalSteps(int totalSteps) {
        this.totalSteps = totalSteps;
    }
}
