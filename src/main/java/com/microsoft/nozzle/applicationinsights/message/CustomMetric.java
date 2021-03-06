package com.microsoft.nozzle.applicationinsights.message;

import lombok.Data;

@Data
public class CustomMetric extends BaseMessage {
    private String name;

    private int count;

    private double min;

    private double max;

    private double sum;

    private double sumOfSquares;

    public CustomMetric(String name){
        this.name = name;
    }

    public double getAverage() {
        return (count == 0) ? 0 : (sum / count);
    }

    public double getVariance() {
        Double average = getAverage();
        return (count == 0) ? 0 : (sumOfSquares / count) - (average * average);
    }

    public double getStandardDeviation() {
        Double variance = getVariance();
        return Math.sqrt(variance);
    }

    /**
     * Aggregate the data point
     * @param value
     */
    public void trackValue(double value) {
        if (count == 0 || value < min) {
            min = value;
        }
        if (count == 0 || value > max) {
            max = value;
        }
        count++;
        sum += value;
        sumOfSquares += value * value;
    }
}
