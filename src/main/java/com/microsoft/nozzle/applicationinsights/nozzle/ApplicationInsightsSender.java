package com.microsoft.nozzle.applicationinsights.nozzle;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.*;
import com.microsoft.nozzle.applicationinsights.message.CustomMetric;
import com.microsoft.nozzle.applicationinsights.message.EventMessage;
import com.microsoft.nozzle.applicationinsights.message.RtrMessage;
import com.microsoft.nozzle.applicationinsights.message.TraceMessage;
import lombok.extern.slf4j.Slf4j;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sends telemetries to Application Insights
 */
@Slf4j
public class ApplicationInsightsSender {

    private TelemetryClient telemetryClient = new TelemetryClient();
    // key is Metric name + app id + instance index
    private final Map<String, CustomMetric> metricMap = new HashMap<String, CustomMetric>();
    private final ReentrantLock lock = new ReentrantLock();
    private boolean enabled = true;

    public ApplicationInsightsSender(String instrumentationKey) {
        telemetryClient.getContext().setInstrumentationKey(instrumentationKey);
        String iKey = telemetryClient.getContext().getInstrumentationKey();
        if (iKey == null) {
            log.error("Error: no instrumentation key set");
            enabled = false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Send Request telemetry to Application Insights
     *
     * @param msg
     */
    public void sendRequest(RtrMessage msg) {
        String name = msg.getName();
        RequestTelemetry telem = new RequestTelemetry(name, msg.getTimestamp(), msg.getResponseTime(), msg.getStatusCode(), msg.isSuccess());

        String url = msg.getUrl();
        telem.setHttpMethod(msg.getMethod());
        try {
            telem.setUrl(url);
        } catch (MalformedURLException e) {
            log.error("Error: malformed url: {}", url);
        }

        telem.getContext().getOperation().setName(name);
        telem.getContext().getUser().setUserAgent(msg.getUserAgent());
        telem.getContext().getLocation().setIp(msg.getXForwardedFor());

        setTelemetryProperty(telem, "referer", msg.getReferer());
        setTelemetryProperty(telem, "remote_addr", msg.getRemoteAddr());
        setTelemetryProperty(telem, "dest_ip_port", msg.getDestIpAndPort());
        setTelemetryProperty(telem, "vcap_request_id", msg.getVcapRequestId());
        setTelemetryProperty(telem, "app_index", msg.getAppIndex());
        setTelemetryProperty(telem, "app_name", msg.getApplicationName());
        setTelemetryProperty(telem, "space_name", msg.getSpaceName());
        setTelemetryProperty(telem, "org_name", msg.getOrganizationName());
        setTelemetryProperty(telem, "app_id", msg.getApplicationId());
        setTelemetryProperty(telem, "source_instance", msg.getInstanceId());

        log.debug("Sending Request telemetry: {}", name);
        telemetryClient.track(telem);
    }

    /**
     * Set the telemetry property
     *
     * @param telem
     * @param name
     * @param value
     */
    private void setTelemetryProperty(BaseTelemetry telem, String name, String value) {
        if (value == null) {
            log.error("Failed to set the property of name {} due to null value", name);
            return;
        }

        telem.getContext().getProperties().put(name, value);
    }

    /**
     * Send Trace telemetry to Application Insights
     *
     * @param msg
     */
    public void sendTrace(TraceMessage msg) {
        SeverityLevel level = SeverityLevel.Information;
        switch (msg.getMessageType()) {
            case ERR:
                level = SeverityLevel.Error;
                break;
            case OUT:
                level = SeverityLevel.Information;
                break;
            default:
                log.error("Error: unknown message type: {}", msg.getMessageType());
        }

        TraceTelemetry telem = new TraceTelemetry(msg.getMessage(), level);

        setTelemetryProperty(telem, "source_instance", msg.getInstanceId());
        setTelemetryProperty(telem, "app_name", msg.getApplicationName());
        setTelemetryProperty(telem, "space_name", msg.getSpaceName());
        setTelemetryProperty(telem, "org_name", msg.getOrganizationName());
        setTelemetryProperty(telem, "app_id", msg.getApplicationId());

        log.debug("Sending Trace telemetry: {}", msg.getMessage());
        telemetryClient.track(telem);
    }

    /**
     * Aggregate the metric data points, to reduce the cost and performance overhead by sending fewer data points to Application Insights
     *
     * @param metric
     */
    public void trackMetric(CustomMetric metric, double value) {
        log.trace("Track Metric telemetry, name: {}, value: {}", metric.getName(), value);
        String key = metric.getName() + metric.getApplicationId() + metric.getInstanceId();

        lock.lock();
        try {
            if (!metricMap.containsKey(key)) {
                metricMap.put(key, metric);
            }
            metricMap.get(key).trackValue(value);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Send Metric telemetry to Application Insights
     */
    public void sendMetrics() {
        Map<String, CustomMetric> currentMap = new HashMap<String, CustomMetric>();
        lock.lock();
        try {
            currentMap.putAll(metricMap);
            metricMap.clear();
        } finally {
            lock.unlock();
        }

        for (CustomMetric metric : currentMap.values()) {
            MetricTelemetry telem = new MetricTelemetry(metric.getName(), metric.getSum());
            telem.setCount(metric.getCount());
            telem.setMax(metric.getMax());
            telem.setMin(metric.getMin());
            telem.setStandardDeviation(metric.getStandardDeviation());

            setTelemetryProperty(telem, "instance_index", metric.getInstanceId());
            setTelemetryProperty(telem, "app_name", metric.getApplicationName());
            setTelemetryProperty(telem, "space_name", metric.getSpaceName());
            setTelemetryProperty(telem, "org_name", metric.getOrganizationName());
            setTelemetryProperty(telem, "app_id", metric.getApplicationId());

            log.debug("Sending Metric telemetry: {}, app: {}, instance: {}", metric.getName(), metric.getApplicationName(), metric.getInstanceId());
            telemetryClient.track(telem);
        }
    }

    /**
     * Send Event telemetry to Application Insights
     *
     * @param msg
     */
    public void sendEvent(EventMessage msg) {
        log.debug("Sending Event telemetry: {}", msg.getName());

        EventTelemetry telem = new EventTelemetry(msg.getName());

        setTelemetryProperty(telem, "source_instance", msg.getInstanceId());
        setTelemetryProperty(telem, "app_name", msg.getApplicationName());
        setTelemetryProperty(telem, "space_name", msg.getSpaceName());
        setTelemetryProperty(telem, "org_name", msg.getOrganizationName());
        setTelemetryProperty(telem, "app_id", msg.getApplicationId());

        telemetryClient.track(telem);
    }
}
