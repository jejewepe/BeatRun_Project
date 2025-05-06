package com.example.runtune.stats;

public class StatsCalculator {
    private final double strideLengthMeters;

    public StatsCalculator(double strideLengthMeters) {
        this.strideLengthMeters = strideLengthMeters;
    }

    public double calculateDistance(int steps) {
        return steps * strideLengthMeters;
    }

    public double calculateSpeed(double distanceMeters, long elapsedMillis) {
        if (elapsedMillis <= 0) return 0;
        double elapsedSeconds = elapsedMillis / 1000.0;
        double speedMetersPerSecond = distanceMeters / elapsedSeconds;
        return speedMetersPerSecond * 3.6;
    }
}

