package com.energia.resourcemanagement.service.impl;

import com.energia.resourcemanagement.domain.entity.Characteristic;
import com.energia.resourcemanagement.domain.entity.Resource;
import com.energia.resourcemanagement.domain.enums.EventType;
import com.energia.resourcemanagement.domain.enums.ResourceType;
import com.energia.resourcemanagement.dto.request.CreateResourceRequest;
import com.energia.resourcemanagement.dto.request.UpdateResourceRequest;
import com.energia.resourcemanagement.dto.response.ResourceResponse;
import com.energia.resourcemanagement.exception.DuplicateCharacteristicException;
import com.energia.resourcemanagement.exception.ResourceNotFoundException;
import com.energia.resourcemanagement.kafka.event.ResourceEvent;
import com.energia.resourcemanagement.kafka.producer.ResourceEventProducer;
import com.energia.resourcemanagement.mapper.ResourceMapper;
import com.energia.resourcemanagement.repository.ResourceRepository;
import com.energia.resourcemanagement.service.ResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private final ResourceRepository resourceRepository;
    private final ResourceMapper resourceMapper;
    private final ResourceEventProducer eventProducer;

    @Override
    @Transactional
    public ResourceResponse createResource(CreateResourceRequest request) {
        log.info("Creating new resource with type: {} and country: {}", request.getType(), request.getCountryCode());

        Resource resource = resourceMapper.toEntity(request);
        resource.setLocation(resourceMapper.toLocation(request.getLocation()));

        if (request.getCharacteristics() != null && !request.getCharacteristics().isEmpty()) {
            validateCharacteristics(request.getCharacteristics());

            request.getCharacteristics().forEach(charDTO -> {
                Characteristic characteristic = resourceMapper.toCharacteristic(charDTO);
                resource.addCharacteristic(characteristic);
            });
        }

        Resource savedResource = resourceRepository.save(resource);
        log.info("Resource created with id: {}", savedResource.getId());

        ResourceResponse response = resourceMapper.toResponse(savedResource);
        publishResourceEvent(EventType.RESOURCE_CREATED, savedResource.getId(), response);

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ResourceResponse getResource(UUID id) {
        log.info("Fetching resource with id: {}", id);

        Resource resource = resourceRepository.findByIdWithCharacteristics(id)
                .orElseThrow(() -> new ResourceNotFoundException(id));

        return resourceMapper.toResponse(resource);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ResourceResponse> getAllResources(String countryCode, String type, Pageable pageable) {
        log.info("Fetching resources with filters - country: {}, type: {}", countryCode, type);

        Page<Resource> resources;

        if (countryCode != null && type != null) {
            ResourceType resourceType = ResourceType.valueOf(type);
            resources = resourceRepository.findByCountryCodeAndType(countryCode, resourceType, pageable);
        } else if (countryCode != null) {
            resources = resourceRepository.findByCountryCode(countryCode, pageable);
        } else if (type != null) {
            ResourceType resourceType = ResourceType.valueOf(type);
            resources = resourceRepository.findByType(resourceType, pageable);
        } else {
            resources = resourceRepository.findAll(pageable);
        }

        return resources.map(resourceMapper::toResponse);
    }

    @Override
    @Transactional
    public ResourceResponse updateResource(UUID id, UpdateResourceRequest request, Long version) {
        log.info("Updating resource with id: {}", id);

        Resource resource = resourceRepository.findByIdWithCharacteristics(id)
                .orElseThrow(() -> new ResourceNotFoundException(id));

        if (version != null && !version.equals(resource.getVersion())) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
                    Resource.class, id);
        }

        if (request.getLocation() != null) {
            resource.setLocation(resourceMapper.toLocation(request.getLocation()));
        }

        if (request.getCharacteristics() != null) {
            validateCharacteristics(request.getCharacteristics());

            resource.getCharacteristics().clear();
            request.getCharacteristics().forEach(charDTO -> {
                Characteristic characteristic = resourceMapper.toCharacteristic(charDTO);
                resource.addCharacteristic(characteristic);
            });
        }

        Resource updatedResource = resourceRepository.save(resource);
        log.info("Resource updated successfully with id: {}", id);

        ResourceResponse response = resourceMapper.toResponse(updatedResource);
        publishResourceEvent(EventType.RESOURCE_UPDATED, updatedResource.getId(), response);

        return response;
    }

    @Override
    @Transactional
    public void deleteResource(UUID id) {
        log.info("Deleting resource with id: {}", id);

        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(id));

        ResourceResponse response = resourceMapper.toResponse(resource);
        resourceRepository.delete(resource);

        log.info("Resource deleted successfully with id: {}", id);

        publishResourceEvent(EventType.RESOURCE_DELETED, id, response);
    }

    // CHANGED: Complete method replacement - stream-based implementation
    @Override
    @Transactional(readOnly = true)
    public void exportAllToKafka() {
        log.info("Starting stream-based bulk export");
        AtomicLong totalProcessed = new AtomicLong(0);

        try (Stream<Resource> stream = resourceRepository.findAllWithCharacteristics()) {

            stream.collect(Collectors.groupingBy(resource ->
                            totalProcessed.getAndIncrement() / 20000))
                    .values()
                    .parallelStream()
                    .forEach(chunk -> {
                        List<ResourceResponse> responses = resourceMapper.toResponseList(chunk);
                        eventProducer.sendBulkExport(responses);
                        log.info("Processed chunk, total: {}", totalProcessed.get());
                    });
        }

        log.info("Stream export completed. Total: {}", totalProcessed.get());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourceResponse> getAllResourcesForExport() {
        // CHANGED: Modified to use streaming approach for consistency
        try (Stream<Resource> stream = resourceRepository.findAllWithCharacteristics()) {
            List<Resource> resources = stream.collect(Collectors.toList());
            return resourceMapper.toResponseList(resources);
        }
    }

    private void validateCharacteristics(List<com.energia.resourcemanagement.dto.common.CharacteristicDTO> characteristics) {
        Set<String> seen = new HashSet<>();
        for (com.energia.resourcemanagement.dto.common.CharacteristicDTO char1 : characteristics) {
            String key = char1.getCode() + "_" + char1.getType();
            if (!seen.add(key)) {
                throw new DuplicateCharacteristicException(char1.getCode(), char1.getType().toString());
            }
        }
    }

    private void publishResourceEvent(EventType eventType, UUID resourceId, ResourceResponse resource) {
        try {
            ResourceEvent event = ResourceEvent.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(eventType)
                    .resourceId(resourceId)
                    .resource(resource)
                    .timestamp(Instant.now())
                    .build();

            eventProducer.sendResourceEvent(event);
        } catch (Exception e) {
            log.error("Failed to publish event for resource {}: {}", resourceId, e.getMessage());
        }
    }
}