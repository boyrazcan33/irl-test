package com.energia.resourcemanagement.controller;

import com.energia.resourcemanagement.dto.request.CreateResourceRequest;
import com.energia.resourcemanagement.dto.request.UpdateResourceRequest;
import com.energia.resourcemanagement.dto.response.ResourceResponse;
import com.energia.resourcemanagement.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @PostMapping
    public ResponseEntity<ResourceResponse> createResource(@Valid @RequestBody CreateResourceRequest request) {
        log.info("POST /api/v1/resources - Creating new resource");

        ResourceResponse response = resourceService.createResource(request);
        URI location = URI.create("/api/v1/resources/" + response.getId());

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResourceResponse> getResource(@PathVariable UUID id) {
        log.info("GET /api/v1/resources/{} - Fetching resource", id);

        ResourceResponse response = resourceService.getResource(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<ResourceResponse>> getAllResources(
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String type,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("GET /api/v1/resources - Fetching resources with filters: countryCode={}, type={}",
                countryCode, type);

        Page<ResourceResponse> resources = resourceService.getAllResources(countryCode, type, pageable);
        return ResponseEntity.ok(resources);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResourceResponse> updateResource(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateResourceRequest request,
            @RequestHeader(value = "If-Match", required = false) Long version) {

        log.info("PUT /api/v1/resources/{} - Updating resource", id);

        ResourceResponse response = resourceService.updateResource(id, request, version);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResource(@PathVariable UUID id) {
        log.info("DELETE /api/v1/resources/{} - Deleting resource", id);

        resourceService.deleteResource(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/export-all")
    public ResponseEntity<Map<String, Object>> exportAllResources() {
        log.info("POST /api/v1/resources/export-all - Initiating bulk export");

        long totalCount = resourceService.getTotalResourceCount();
        resourceService.exportAllToKafka();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Export initiated successfully");
        response.put("totalResources", totalCount);
        response.put("estimatedTime", "~" + (totalCount / 100) + " seconds");
        response.put("jobId", UUID.randomUUID());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}