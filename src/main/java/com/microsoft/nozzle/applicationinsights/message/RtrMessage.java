package com.microsoft.nozzle.applicationinsights.message;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * RTR message corresponds to Request telemetry in Application Insights
 */
@Data
@Slf4j
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

    public void parseRtrMessage(String message) {
        String[] parts = message.split("\"");
        if (parts.length < 20) {
            log.error("Invalid RTR message: {}", message);
        }

        try {
            // host and timestamp
            String[] strs = parts[0].trim().split(" ");
            this.host = strs[0];

            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            Date timestamp = format.parse(strs[2].substring(1, strs[2].length() - 1));
            this.timestamp = timestamp;

            // method, path and protocol
            strs = parts[1].trim().split(" ");
            this.method = strs[0];
            this.path = strs[1];
            this.protocol = strs[2];

            // status code, request bytes received, and body bytes sent
            strs = parts[2].trim().split(" ");
            this.statusCode = strs[0];
            int code = Integer.parseInt(strs[0]);
            this.success = (code < 400);
            this.requestBytesReceived = strs[1];
            this.bodyBytesSent = strs[2];

            // referer
            this.referer = parts[3].trim();

            // userAgent
            this.userAgent = parts[5].trim();

            //remoteAddr
            this.remoteAddr = parts[7].trim();

            // dest ip and port
            this.destIpAndPort = parts[9].trim();

            // x_forwarded_for
            if (parts[10].contains("x_forwarded_for")) {
                this.xForwardedFor = parts[11].trim().split(",")[0];
            } else {
                log.error("Error parsing x_forwarded_for: {}", parts[10]);
            }

            // x_forwarded_proto
            switch (parts[13].trim()) {
                case "http":
                    this.xForwardedProto = "http";
                    break;
                case "https":
                    this.xForwardedProto = "https";
                    break;
                default:
                    log.error("Error parsing x_forwarded_proto: {}", parts[13]);
            }

            // vcap_request_id
            if (parts[14].contains("vcap_request_id")) {
                this.vcapRequestId = parts[15].trim();
            } else {
                log.error("Error parsing vcap_request_id: {}", parts[14]);
            }

            // response time and app id
            strs = parts[16].trim().split(" ");
            if (strs[0].contains("response_time")) {
                float responseTime = Float.parseFloat(strs[0].split(":")[1]);
                // millisecond
                this.responseTime = (long) (responseTime * 1000);
            } else {
                log.error("Error parsing response time: {}", parts[16]);
            }

            if (strs[1].contains("app_id")) {
                this.appId = parts[17].trim();
            } else {
                log.error("Error parsing app id: {}", parts[16]);
            }

            // app index
            if (parts[18].contains("app_index")) {
                this.appIndex = parts[19].trim();
            } else {
                log.error("Error parsing app index: {}", parts[18]);
            }
        } catch (Exception e) {
            log.error("Error parsing RTR message: {}", e.getMessage());
        }
    }
}
