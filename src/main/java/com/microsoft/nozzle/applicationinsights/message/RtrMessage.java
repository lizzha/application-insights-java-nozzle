package com.microsoft.nozzle.applicationinsights.message;

import lombok.Data;

import java.util.Date;

/**
 * RTR message corresponds to Request telemetry in Application Insights
 */
@Data
public class RtrMessage extends BaseMessage {

    private String host;

    private Date timestamp;

    private String method;

    private String path;

    private String protocol;

    private String statusCode;

    private String requestBytesReceived;

    private String bodyBytesSent;

    private String referer;

    private String userAgent;

    private String remoteAddr;

    private String destIpAndPort;

    private String xForwardedFor;

    private String xForwardedProto;

    private String vcapRequestId;

    private Long responseTime;

    private String appId;

    private String appIndex;

    private boolean success;

    public String getUrl() {
        return xForwardedProto + "://" + host + path;
    }

    public String getName() {
        return method + " " + path;
    }
}
