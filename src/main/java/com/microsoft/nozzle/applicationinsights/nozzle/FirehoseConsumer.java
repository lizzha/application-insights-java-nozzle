package com.microsoft.nozzle.applicationinsights.nozzle;

import com.microsoft.nozzle.applicationinsights.config.ApplicationConfig;
import com.microsoft.nozzle.applicationinsights.config.NozzleProperties;
import com.microsoft.nozzle.applicationinsights.config.TelemetryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.doppler.*;
import org.springframework.context.SmartLifecycle;

import java.util.stream.Collectors;

/**
 * Consume events from the Firehose and delegate to the event router
 */
@RequiredArgsConstructor
@Slf4j
public class FirehoseConsumer implements SmartLifecycle {
    private final DopplerClient dopplerClient;
    private final NozzleProperties properties;
    private final FirehoseEventRouter router;

    private boolean running = false;

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable runnable) {
        runnable.run();
        stop();
    }

    @Override
    public void start() {
        log.info("Connecting to the Firehose");
        FirehoseRequest request = FirehoseRequest.builder()
                .subscriptionId(properties.getSubscriptionId()).build();

        log.info("Accepting telemetry types: {}", properties.getCapturedTelemetries().stream().map(TelemetryType::toString).collect(Collectors.joining(", ")));
        log.info("Collecting telemetries for apps: {}", properties.getApplicationConfigs().stream().map(ApplicationConfig::getApplicationId).collect(Collectors.joining(", ")));

        dopplerClient.firehose(request).retry().subscribe(this::receiveEvent, this::receiveError);

        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    private void receiveEvent(Envelope envelope) {
        EventType type = envelope.getEventType();

        if (type == EventType.LOG_MESSAGE || type == EventType.CONTAINER_METRIC) {
            router.routeEnvelope(envelope);
        }
    }

    private void receiveError(Throwable error) {
        log.error("Error in receiving Firehose event: {}", error.getMessage(), error);
    }
}
