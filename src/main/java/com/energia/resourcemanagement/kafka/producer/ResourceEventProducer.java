package com.energia.resourcemanagement.kafka.producer;

import com.energia.resourcemanagement.dto.response.ResourceResponse;
import com.energia.resourcemanagement.kafka.event.ResourceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.resource-events}")
    private String resourceEventsTopic;

    public void sendResourceEvent(ResourceEvent event) {
        log.debug("Sending resource event: {} for resource: {}", event.getEventType(), event.getResourceId());

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(resourceEventsTopic, event.getResourceId().toString(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully sent event: {} for resource: {}",
                        event.getEventType(), event.getResourceId());
            } else {
                log.error("Failed to send event for resource: {}", event.getResourceId(), ex);
            }
        });
    }

    public void sendBulkExport(List<ResourceResponse> resources) {
        log.info("Sending bulk export with {} resources", resources.size());

        // Send in batches to avoid message size limits
        int batchSize = 1000;
        for (int i = 0; i < resources.size(); i += batchSize) {
            int end = Math.min(i + batchSize, resources.size());
            List<ResourceResponse> batch = resources.subList(i, end);

            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(resourceEventsTopic, "bulk-export", batch);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Successfully sent batch of {} resources", batch.size());
                } else {
                    log.error("Failed to send batch", ex);
                }
            });
        }
    }
}