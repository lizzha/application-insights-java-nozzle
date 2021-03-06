package com.microsoft.nozzle.applicationinsights.message;

import lombok.Data;

@Data
public class EventMessage extends BaseMessage {
    private String name;

    public EventMessage(String name) {
        this.name = name;
    }
}
