package dev.legatus.server.service.studio;

import dev.legatus.server.service.dto.lstcp.EventClusterMetricTickFrame;
import dev.legatus.server.service.dto.lstcp.EventEnrollmentRequestedFrame;
import dev.legatus.server.service.dto.lstcp.EventTaskFailedAlertFrame;
import dev.legatus.server.service.dto.lstcp.EventTaskLifecycleFrame;
import dev.legatus.server.service.dto.lstcp.EventTaskLogChunkFrame;
import dev.legatus.server.service.dto.lstcp.EventWorkerStateChangedFrame;

/**
 * SPI for broadcasting and publishing real-time cluster events to connected Legatus Studio sessions.
 */
public interface StudioEventPublisher {

    void publishMetricTick(EventClusterMetricTickFrame.Payload payload);

    void publishEnrollmentRequested(EventEnrollmentRequestedFrame.Payload payload);

    void publishWorkerStateChanged(EventWorkerStateChangedFrame.Payload payload);

    void publishTaskLifecycle(EventTaskLifecycleFrame.Payload payload);

    void publishTaskLogChunk(EventTaskLogChunkFrame.Payload payload);

    void publishTaskFailedAlert(EventTaskFailedAlertFrame.Payload payload);
}
