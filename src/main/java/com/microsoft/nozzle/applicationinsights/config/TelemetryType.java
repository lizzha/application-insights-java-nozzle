package com.microsoft.nozzle.applicationinsights.config;

public enum TelemetryType {

    /**
     * Request telemetry
     */
    HTTP_REQUEST,

    /**
     * Metric telemetry
     */
    METRIC,

    /**
     * Event telemetry
     */
    APP_EVENT,

    /**
     * Trace telemtry
     */
    TRACE;
}
