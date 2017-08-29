package com.microsoft.nozzle.applicationinsights.message;

import org.cloudfoundry.doppler.MessageType;
import lombok.Data;

@Data
public class TraceMessage extends BaseMessage {

    private String message;

    private MessageType messageType;
}
