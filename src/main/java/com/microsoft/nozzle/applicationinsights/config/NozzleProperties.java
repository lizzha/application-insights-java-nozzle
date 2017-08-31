package com.microsoft.nozzle.applicationinsights.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.annotation.PostConstruct;

@Data
@ConfigurationProperties
public class NozzleProperties {

    @PostConstruct
    public void postConstruct() {
        setIgnoredTelemetries();
        setApplicationConfigs();
    }

    /**
     * The instrumentation key of Application Insights resource
     */
    private String instrumentationKey;

    /**
     * The Cloud Controller host. Should be in the form "api.{{SYSTEM_DOMAIN}}"
     */
    private String apiAddr;

    /**
     * An OAuth client id who has access "doppler.firehose" and "cloud_controller.admin"
     */
    private String clientId;

    /**
     * The secret for the above client
     */
    private String clientSecret;

    /**
     * A unique subscription ID used by the Firehose
     */
    private String subscriptionId = "appinsights-nozzle";

    /**
     * Skip SSL validation when connecting to the firehose
     */
    private boolean skipSslValidation = false;

    /**
     * The telemetry types NOT to send to Application Insights. Comma separated string. Valid types are HttpRequest, Metric, AppEvent, AppLog
     */
    private String telemetryIgnoreList;

    /**
     * String of a list of APPLICATION_ID and INSTRUMENTATION_KEY, it's recommended that each application use a separate Application Insights resource
     */
    private String applicationConfig;

    private final List<TelemetryType> ignoredTelemetries = new ArrayList<TelemetryType>();

    private final List<ApplicationConfig> applicationConfigs = new ArrayList<ApplicationConfig>();

    /**
     * Parse the value of telemetryTypes
     */
    public void setIgnoredTelemetries() {
        ignoredTelemetries.clear();

        if (telemetryIgnoreList == null || telemetryIgnoreList.isEmpty()) {
            return;
        }

        String lower = telemetryIgnoreList.toLowerCase();

        if (lower.contains("httprequest")) {
            ignoredTelemetries.add(TelemetryType.HTTP_REQUEST);
        }
        if (lower.contains("metric")) {
            ignoredTelemetries.add(TelemetryType.METRIC);
        }
        if (lower.contains("appevent")) {
            ignoredTelemetries.add(TelemetryType.APP_EVENT);
        }
        if (lower.contains("trace")) {
            ignoredTelemetries.add(TelemetryType.TRACE);
        }
    }

    /**
     * Parse the value of applicationConfig
     */
    public void setApplicationConfigs() {
        applicationConfigs.clear();

        if (applicationConfig == null || applicationConfig.isEmpty()) {
            return;
        }

        Pattern pattern = Pattern.compile("map\\[((APPLICATION_ID|INSTRUMENTATION_KEY):[\\w-]*\\s*)*");
        Matcher matcher = pattern.matcher(applicationConfig);

        while (matcher.find()) {
            String config = matcher.group(0);

            Pattern p = Pattern.compile("(([^\\s]*):([^\\s]*))");
            Matcher m = p.matcher(config);

            ApplicationConfig appConfig = new ApplicationConfig();

            while (m.find()) {
                if (m.group(2).contains("APPLICATION_ID")) {
                    appConfig.setApplicationId(m.group(3));
                } else if (m.group(2).contains("INSTRUMENTATION_KEY")) {
                    appConfig.setInstrumentationKey(m.group(3));
                }
            }

            applicationConfigs.add(appConfig);

        }
    }
}
