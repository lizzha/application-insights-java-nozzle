package com.microsoft.nozzle.applicationinsights.nozzle;

import com.microsoft.nozzle.applicationinsights.config.NozzleProperties;
import com.microsoft.nozzle.applicationinsights.config.TelemetryType;
import com.microsoft.nozzle.applicationinsights.config.ApplicationConfig;
import com.microsoft.nozzle.applicationinsights.cache.AppDataCache;
import com.microsoft.nozzle.applicationinsights.message.*;
import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.doppler.LogMessage;
import org.cloudfoundry.doppler.ContainerMetric;
import org.cloudfoundry.doppler.Envelope;
import org.cloudfoundry.doppler.EventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Parse events from the Cloud Foundry Firehose and send corresponding telemetries to Application Insights
 */
@Service
@Slf4j
public class FirehoseEventRouter {

    private final AppDataCache appDataCache;
    private final NozzleProperties properties;
    private final Map<String, ApplicationInsightsSender> appIdtoSenderMap = new HashMap<String, ApplicationInsightsSender>();

    @Autowired
    public FirehoseEventRouter(NozzleProperties properties, AppDataCache appDataCache) {

        this.properties = properties;

        this.appDataCache = appDataCache;

        // Create a sender for each app
        List<ApplicationConfig> configs = properties.getApplicationConfigs();
        for (ApplicationConfig config : configs) {
            log.trace("Creating sender for app: {}", config.getApplicationId());
            ApplicationInsightsSender sender = new ApplicationInsightsSender(config.getInstrumentationKey());
            // Instrumentation key is not null
            if (sender.isEnabled()) {
                appIdtoSenderMap.put(config.getApplicationId(), sender);
            }
        }
    }

    /**
     * Get the sender for an app
     *
     * @param appId
     * @return
     */
    private ApplicationInsightsSender getSender(String appId) {
        return appIdtoSenderMap.get(appId);
    }

    /**
     * Returns whether the telemetry type is ignored
     *
     * @param telemetryType
     * @return
     */
    private boolean ignoreTelemetryType(TelemetryType telemetryType) {
        return properties.getIgnoredTelemetries().contains(telemetryType);
    }

    /**
     * Convert an envelope into an Application Insights telemetry.
     *
     * @param envelope The event from the Firehose
     */
    @Async
    void routeEnvelope(Envelope envelope) {
        if (envelope.getEventType() == EventType.LOG_MESSAGE) {
            LogMessage message = envelope.getLogMessage();
            if (message == null) {
                return;
            }
            ApplicationInsightsSender sender = getSender(message.getApplicationId());
            if (sender == null) {
                return;
            }
            switch (message.getSourceType()) {
                case "RTR":
                    if (!ignoreTelemetryType(TelemetryType.HTTP_REQUEST)) {
                        routeRtrMessage(message, sender);
                    }
                    break;
                case "API":
                case "STG":
                case "SSH":
                    if (!ignoreTelemetryType(TelemetryType.APP_EVENT)) {
                        routeEvent(message, sender);
                    }
                default:
                    if (!ignoreTelemetryType(TelemetryType.TRACE)) {
                        routeTraceMessage(message, sender);
                    }
            }
        } else if (envelope.getEventType() == EventType.CONTAINER_METRIC && !ignoreTelemetryType(TelemetryType.METRIC)) {
            ContainerMetric message = envelope.getContainerMetric();

            if (message != null) {
                ApplicationInsightsSender sender = getSender(message.getApplicationId());
                if (sender != null) {
                    routeMetric(message, sender);
                }
            }
        }
    }

    /**
     * Parse LogMessage to Event telemetry, and send to Application Insights
     *
     * @param message
     * @param sender
     */
    private void routeEvent(LogMessage message, ApplicationInsightsSender sender) {
        String msg = message.getMessage();
        if (msg == null) {
            return;
        }
        String eventName = null;
        switch (message.getSourceType()) {
            case "API":
                if (msg.contains("({\"state\"=>\"STARTED\"})")) {
                    eventName = "App Started";
                } else if (msg.contains("({\"state\"=>\"STOPPED\"})")) {
                    eventName = "App Stopped";
                } else if (msg.contains("Deleted app")) {
                    eventName = "App Deleted";
                } else if (msg.contains("Process has crashed")) {
                    eventName = "App Crashed";
                }
                break;
            case "STG":
                if (msg.contains("Staging complete")) {
                    eventName = "Staging Complete";
                }
                break;
            case "SSH":
                if (msg.contains("Successful remote access")) {
                    eventName = "SSH Success";
                } else if (msg.contains("Remote access ended")) {
                    eventName = "SSH End";
                }
                break;
        }
        if (eventName != null) {
            EventMessage event = new EventMessage(eventName);
            setCommonInfo(message.getApplicationId(), message.getSourceInstance(), event);
            sender.sendEvent(event);
        }
    }

    /**
     * Parse ContainerMetric to Metric telemetry, and aggregate the data
     *
     * @param message
     * @param sender
     */
    private void routeMetric(ContainerMetric message, ApplicationInsightsSender sender) {
        Double cpu = message.getCpuPercentage();
        if (cpu != null) {
            trackMetric("CPU Percentage (%)", message.getApplicationId(), message.getInstanceIndex().toString(), sender, cpu.doubleValue());
        }

        Long disk = message.getDiskBytes();
        if (disk != null) {
            trackMetric("Disk Bytes (MB)", message.getApplicationId(), message.getInstanceIndex().toString(), sender, disk.doubleValue() / 1048576);
        }

        Long memory = message.getMemoryBytes();
        if (memory != null) {
            trackMetric("Memory Bytes (MB)", message.getApplicationId(), message.getInstanceIndex().toString(), sender, memory.doubleValue() / 1048576);
        }

        Long diskQuota = message.getDiskBytesQuota();
        if (diskQuota != null) {
            trackMetric("Disk Quota (MB)", message.getApplicationId(), message.getInstanceIndex().toString(), sender, diskQuota.doubleValue() / 1048576);
        }

        Long memoryQuota = message.getMemoryBytesQuota();
        if (memoryQuota != null) {
            trackMetric("Memory Quota (MB)", message.getApplicationId(), message.getInstanceIndex().toString(), sender, memoryQuota.doubleValue() / 1048576);
        }
    }

    private void trackMetric(String name, String appId, String instanceIndex, ApplicationInsightsSender sender, double value) {
        CustomMetric metric = new CustomMetric(name);
        setCommonInfo(appId, instanceIndex, metric);
        sender.trackMetric(metric, value);
    }

    /**
     * Send aggregated metric data to Application Insights at an interval of 1 minute
     */
    @Scheduled(fixedRate = 60000)
    void sendMetric() {
        if (!ignoreTelemetryType(TelemetryType.METRIC)) {
            for (ApplicationInsightsSender sender : appIdtoSenderMap.values()) {
                sender.sendMetrics();
            }
        }
    }

    /**
     * Parse LogMessage to RTR message, and send it as Request telemetry to Application Insights
     *
     * @param message
     * @param sender
     */
    private void routeRtrMessage(LogMessage message, ApplicationInsightsSender sender) {
        String msg = message.getMessage();

        if (msg != null) {
            RtrMessage rtr = new RtrMessage();
            rtr.parseRtrMessage(msg);

            setCommonInfo(message.getApplicationId(), message.getSourceInstance(), rtr);
            sender.sendRequest(rtr);
        }
    }

    /**
     * Set the common information from LogMessage
     *
     * @param appId
     * @param instanceId
     * @param base
     */
    private void setCommonInfo(String appId, String instanceId, BaseMessage base) {
        appDataCache.getAppData(appId, base);
        base.setInstanceId(instanceId);
    }


    /**
     * Generate Trace telemetry, and send to Application Insights
     *
     * @param message
     * @param sender
     */
    private void routeTraceMessage(LogMessage message, ApplicationInsightsSender sender) {
        String msg = message.getMessage();

        if (msg != null) {
            TraceMessage trace = new TraceMessage();

            setCommonInfo(message.getApplicationId(), message.getSourceInstance(), trace);

            trace.setMessage(msg);
            trace.setMessageType(message.getMessageType());

            sender.sendTrace(trace);
        }
    }
}
