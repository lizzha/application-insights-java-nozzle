package com.microsoft.nozzle.applicationinsights.config;

import lombok.Data;

@Data
public class ApplicationConfig {

    /**
     * The instrumentation key of Application Insights resource
     */
    private String instrumentationKey;

    /**
     * The application id
     */
    private String applicationId;
}
