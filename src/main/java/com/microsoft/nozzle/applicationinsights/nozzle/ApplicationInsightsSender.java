package com.microsoft.nozzle.applicationinsights.nozzle;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.*;
import com.microsoft.nozzle.applicationinsights.message.CustomMetric;
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
    private final Map<String, CustomMetric> nametoMetricMap = new HashMap<String, CustomMetric>();
    private final ReentrantLock lock = new ReentrantLock();

    public ApplicationInsightsSender(String instrumentationKey) {
        telemetryClient.getContext().setInstrumentationKey(instrumentationKey);
        String iKey = telemetryClient.getContext().getInstrumentationKey();
        if (iKey == null) {
            log.error("Error: no instrumentation key set");
        }
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
        setTelemetryProperty(telem, "referer", msg.getReferer());
        setTelemetryProperty(telem, "remote_addr", msg.getRemoteAddr());
        setTelemetryProperty(telem, "dest_ip_port", msg.getDestIpAndPort());
        setTelemetryProperty(telem, "vcap_request_id", msg.getVcapRequestId());
        setTelemetryProperty(telem, "instance_id", msg.getInstanceId());
        setTelemetryProperty(telem, "app_name", msg.getApplicationName());
        setTelemetryProperty(telem, "space_name", msg.getSpaceName());
        setTelemetryProperty(telem, "org_name", msg.getOrganizationName());

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
                level = SeverityLevel.Warning;
                break;
            case OUT:
                level = SeverityLevel.Information;
                break;
            default:
                log.error("Error: unknown message type: {}", msg.getMessageType());
        }

        TraceTelemetry telem = new TraceTelemetry(msg.getMessage(), level);

        setTelemetryProperty(telem, "instance_id", msg.getInstanceId());
        setTelemetryProperty(telem, "app_name", msg.getApplicationName());
        setTelemetryProperty(telem, "space_name", msg.getSpaceName());
        setTelemetryProperty(telem, "org_name", msg.getOrganizationName());

        log.debug("Sending Trace telemetry: {}", msg.getMessage());
        telemetryClient.track(telem);
    }

    /**
     * Aggregate the metric data points, to reduce the cost and performance overhead by sending fewer data points to Application Insights
     *
     * @param name
     * @param value
     */
    public void trackMetric(String name, double value) {
        log.trace("Track Metric telemetry, name: {}, value: {}", name, value);
        lock.lock();
        try {
            CustomMetric metric;
            if (!nametoMetricMap.containsKey(name)) {
                metric = new CustomMetric();
                nametoMetricMap.put(name, metric);
            } else {
                metric = nametoMetricMap.get(name);
            }
            metric.trackValue(value);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Send Metric telemetry to Application Insights
     */
    public void sendMetrics() {
        lock.lock();
        try {
            for (String name : nametoMetricMap.keySet()) {
                CustomMetric metric = nametoMetricMap.get(name);

                MetricTelemetry telem = new MetricTelemetry(name, metric.getSum());
                telem.setCount(metric.getCount());
                telem.setMax(metric.getMax());
                telem.setMin(metric.getMin());
                telem.setStandardDeviation(metric.getStandardDeviation());

                telemetryClient.track(telem);
                log.debug("Sending Metric telemetry: {}", name);
            }
            nametoMetricMap.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Send Event telemetry to Application Insights
     *
     * @param name
     */
    public void sendEvent(String name) {
        log.debug("Sending Event telemetry: {}", name);
        telemetryClient.trackEvent(name);
    }
}
