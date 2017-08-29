package com.microsoft.nozzle.applicationinsights.nozzle;

import com.microsoft.nozzle.applicationinsights.config.NozzleProperties;
import com.microsoft.nozzle.applicationinsights.config.TelemetryType;
import com.microsoft.nozzle.applicationinsights.config.ApplicationConfig;
import com.microsoft.nozzle.applicationinsights.cache.AppDataCache;
import com.microsoft.nozzle.applicationinsights.message.BaseMessage;
import com.microsoft.nozzle.applicationinsights.message.RtrMessage;
import com.microsoft.nozzle.applicationinsights.message.TraceMessage;
import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.doppler.LogMessage;
import org.cloudfoundry.doppler.ContainerMetric;
import org.cloudfoundry.doppler.Envelope;
import org.cloudfoundry.doppler.EventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.text.SimpleDateFormat;

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
            appIdtoSenderMap.put(config.getApplicationId(), sender);
        }
    }

    /**
     * Get the sender for an app
     *
     * @param appId
     * @return
     */
    private ApplicationInsightsSender getSender(String appId) {
        if (appId != null && appIdtoSenderMap.containsKey(appId)) {
            return appIdtoSenderMap.get(appId);
        }
        return null;
    }

    /**
     * Convert an envelope into an Application Insights telemetry.
     *
     * @param envelope The event from the Firehose
     */
    @Async
    void routeEnvelope(Envelope envelope) {
        List<TelemetryType> telemetryTypes = properties.getCapturedTelemetries();

        if (envelope.getEventType() == EventType.LOG_MESSAGE) {
            LogMessage message = envelope.getLogMessage();

            if (message != null) {
                ApplicationInsightsSender sender = getSender(message.getApplicationId());
                if (sender != null) {
                    switch (message.getSourceType()) {
                        case "RTR":
                            if (telemetryTypes.contains(TelemetryType.HTTP_REQUEST)) {
                                routeRtrMessage(message, sender);
                            }
                            break;
                        case "API":
                        case "STG":
                        case "SSH":
                            if (telemetryTypes.contains(TelemetryType.APP_EVENT)) {
                                routeEvent(message, sender);
                            }
                        default:
                            if (telemetryTypes.contains(TelemetryType.TRACE)) {
                                routeTraceMessage(message, sender);
                            }
                    }
                }
            }
        }
        else if (envelope.getEventType() == EventType.CONTAINER_METRIC && telemetryTypes.contains(TelemetryType.METRIC)) {
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
        if (msg != null) {
            switch (message.getSourceType()) {
                case "API":
                    if (msg.contains("({\"state\"=>\"STARTED\"})")) {
                        sender.sendEvent("App Started");
                    } else if (msg.contains("({\"state\"=>\"STOPPED\"})")) {
                        sender.sendEvent("App Stopped");
                    } else if (msg.contains("Deleted app")) {
                        sender.sendEvent("App Deleted");
                    } else if (msg.contains("Process has crashed")) {
                        sender.sendEvent("App Crashed");
                    }
                    break;
                case "STG":
                    if (msg.contains("Staging complete")) {
                        sender.sendEvent("Staging Complete");
                    }
                    break;
                case "SSH":
                    if (msg.contains("Successful remote access")) {
                        sender.sendEvent("SSH Success");
                    } else if (msg.contains("Remote access ended")) {
                        sender.sendEvent("SSH End");
                    }
                    break;
            }
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
            sender.trackMetric("CPU Percentage (%)", message.getCpuPercentage());
        }

        Long disk = message.getDiskBytes();
        if (disk != null) {
            sender.trackMetric("Disk Bytes (MB)", disk.doubleValue() / 1048576);
        }

        Long memory = message.getMemoryBytes();
        if (memory != null) {
            sender.trackMetric("Memory Bytes (MB)", memory.doubleValue() / 1048576);
        }

        Long diskQuota = message.getDiskBytesQuota();
        if (diskQuota != null) {
            sender.trackMetric("Disk Quota (MB)", diskQuota.doubleValue() / 1048576);
        }

        Long memoryQuota = message.getMemoryBytesQuota();
        if (memoryQuota != null) {
            sender.trackMetric("Memory Quota (MB)", memoryQuota.doubleValue() / 1048576);
        }
    }

    /**
     * Send aggregated metric data to Application Insights at an interval of 1 minute
     */
    @Scheduled(fixedRate = 60000)
    void sendMetric() {
        if (properties.getCapturedTelemetries().contains(TelemetryType.METRIC)) {
            for (String id : appIdtoSenderMap.keySet()) {
                ApplicationInsightsSender sender = appIdtoSenderMap.get(id);
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
            RtrMessage rtr = parseRtrMessage(msg);

            if (rtr != null) {
                setCommonInfo(message, rtr);
                sender.sendRequest(rtr);
            }
        }
    }

    /**
     * Set the common information from LogMessage
     *
     * @param message
     * @param base
     */
    private void setCommonInfo(LogMessage message, BaseMessage base) {
        appDataCache.getAppData(message.getApplicationId(), base);
        base.setInstanceId(message.getSourceInstance());
    }

    /**
     * Parse RTR message from a string
     *
     * @param message
     * @return
     */
    private RtrMessage parseRtrMessage(String message) {
        String[] parts = message.split("\"");
        if (parts.length < 20) {
            log.error("Invalid RTR message: {}", message);
            return null;
        }

        RtrMessage rtr = new RtrMessage();

        try {
            // host and timestamp
            String[] strs = parts[0].trim().split(" ");
            rtr.setHost(strs[0]);

            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            Date timestamp = format.parse(strs[2].substring(1, strs[2].length() - 1));
            rtr.setTimestamp(timestamp);

            // method, path and protocol
            strs = parts[1].trim().split(" ");
            rtr.setMethod(strs[0]);
            rtr.setPath(strs[1]);
            rtr.setProtocol(strs[2]);

            // status code, request bytes received, and body bytes sent
            strs = parts[2].trim().split(" ");
            rtr.setStatusCode(strs[0]);
            int code = Integer.parseInt(strs[0]);
            rtr.setSuccess(code < 400);
            rtr.setRequestBytesReceived(strs[1]);
            rtr.setBodyBytesSent(strs[2]);

            // referer
            rtr.setReferer(parts[3].trim());

            // userAgent
            rtr.setUserAgent(parts[5].trim());

            //remoteAddr
            rtr.setRemoteAddr(parts[7].trim());

            // dest ip and port
            rtr.setDestIpAndPort(parts[9].trim());

            // x_forwarded_for
            if (parts[10].contains("x_forwarded_for")) {
                rtr.setXForwardedFor(parts[11].trim().split(",")[0]);
            } else {
                log.error("Error parsing x_forwarded_for: {}", parts[10]);
            }

            // x_forwarded_proto
            switch (parts[13].trim()) {
                case "http":
                    rtr.setXForwardedProto("http");
                    break;
                case "https":
                    rtr.setXForwardedProto("https");
                    break;
                default:
                    log.error("Error parsing x_forwarded_proto: {}", parts[13]);
            }

            // vcap_request_id
            if (parts[14].contains("vcap_request_id")) {
                rtr.setVcapRequestId(parts[15].trim());
            } else {
                log.error("Error parsing vcap_request_id: {}", parts[14]);
            }

            // response time and app id
            strs = parts[16].trim().split(" ");
            if (strs[0].contains("response_time")) {
                float responseTime = Float.parseFloat(strs[0].split(":")[1]);
                // millisecond
                rtr.setResponseTime((long) (responseTime * 1000));
            } else {
                log.error("Error parsing response time: {}", parts[16]);
            }

            if (strs[1].contains("app_id")) {
                rtr.setAppId(parts[17].trim());
            } else {
                log.error("Error parsing app id: {}", parts[16]);
            }

            // app index
            if (parts[18].contains("app_index")) {
                rtr.setAppIndex(parts[19].trim());
            } else {
                log.error("Error parsing app index: {}", parts[18]);
            }

        } catch (Exception e) {
            log.error("Error parsing RTR message: {}", e.getMessage());
        }

        return rtr;
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

            setCommonInfo(message, trace);

            trace.setMessage(msg);
            trace.setMessageType(message.getMessageType());

            sender.sendTrace(trace);

        }
    }
}
