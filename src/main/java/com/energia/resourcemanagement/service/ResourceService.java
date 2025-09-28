package com.energia.resourcemanagement.service;

import com.energia.resourcemanagement.dto.request.CreateResourceRequest;
import com.energia.resourcemanagement.dto.request.UpdateResourceRequest;
import com.energia.resourcemanagement.dto.response.ResourceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ResourceService {

    ResourceResponse createResource(CreateResourceRequest request);

    ResourceResponse getResource(UUID id);

    Page<ResourceResponse> getAllResources(String countryCode, String type, Pageable pageable);

    ResourceResponse updateResource(UUID id, UpdateResourceRequest request, Long version);

    void deleteResource(UUID id);

    void exportAllToKafka();

    long getTotalResourceCount();
}