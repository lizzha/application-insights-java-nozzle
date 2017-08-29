package com.microsoft.nozzle.applicationinsights.message;

import lombok.Data;

@Data
public class BaseMessage {

    private String applicationId;

    private String applicationName;

    private String spaceId;

    private String spaceName;

    private String organizationId;

    private String organizationName;

    private String instanceId;
}
