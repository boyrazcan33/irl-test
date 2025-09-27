package com.energia.resourcemanagement.unit.service;

import com.energia.resourcemanagement.domain.entity.Characteristic;
import com.energia.resourcemanagement.domain.entity.Location;
import com.energia.resourcemanagement.domain.entity.Resource;
import com.energia.resourcemanagement.domain.enums.CharacteristicType;
import com.energia.resourcemanagement.domain.enums.EventType;
import com.energia.resourcemanagement.domain.enums.ResourceType;
import com.energia.resourcemanagement.dto.common.CharacteristicDTO;
import com.energia.resourcemanagement.dto.common.LocationDTO;
import com.energia.resourcemanagement.dto.request.CreateResourceRequest;
import com.energia.resourcemanagement.dto.request.UpdateResourceRequest;
import com.energia.resourcemanagement.dto.response.ResourceResponse;
import com.energia.resourcemanagement.exception.DuplicateCharacteristicException;
import com.energia.resourcemanagement.exception.ResourceNotFoundException;
import com.energia.resourcemanagement.kafka.event.ResourceEvent;
import com.energia.resourcemanagement.kafka.producer.ResourceEventProducer;
import com.energia.resourcemanagement.mapper.ResourceMapper;
import com.energia.resourcemanagement.repository.ResourceRepository;
import com.energia.resourcemanagement.service.impl.ResourceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private ResourceMapper resourceMapper;

    @Mock
    private ResourceEventProducer eventProducer;

    @InjectMocks
    private ResourceServiceImpl resourceService;

    private UUID resourceId;
    private Resource resource;
    private ResourceResponse resourceResponse;
    private CreateResourceRequest createRequest;
    private UpdateResourceRequest updateRequest;

    @BeforeEach
    void setUp() {
        resourceId = UUID.randomUUID();

        resource = Resource.builder()
                .id(resourceId)
                .type(ResourceType.METERING_POINT)
                .countryCode("EE")
                .location(Location.builder()
                        .streetAddress("Test Street")
                        .city("Tallinn")
                        .postalCode("12345")
                        .countryCode("EE")
                        .build())
                .version(0L)
                .build();

        resourceResponse = ResourceResponse.builder()
                .id(resourceId)
                .type(ResourceType.METERING_POINT)
                .countryCode("EE")
                .location(LocationDTO.builder()
                        .streetAddress("Test Street")
                        .city("Tallinn")
                        .postalCode("12345")
                        .countryCode("EE")
                        .build())
                .version(0L)
                .build();

        createRequest = CreateResourceRequest.builder()
                .type(ResourceType.METERING_POINT)
                .countryCode("EE")
                .location(LocationDTO.builder()
                        .streetAddress("Test Street")
                        .city("Tallinn")
                        .postalCode("12345")
                        .countryCode("EE")
                        .build())
                .characteristics(Arrays.asList(
                        CharacteristicDTO.builder()
                                .code("TEST1")
                                .type(CharacteristicType.CONSUMPTION_TYPE)
                                .value("RESIDENTIAL")
                                .build()
                ))
                .build();

        updateRequest = UpdateResourceRequest.builder()
                .location(LocationDTO.builder()
                        .streetAddress("Updated Street")
                        .city("Tartu")
                        .postalCode("54321")
                        .countryCode("EE")
                        .build())
                .build();
    }

    @Test
    void createResource_Success() {
        when(resourceMapper.toEntity(createRequest)).thenReturn(resource);
        when(resourceMapper.toLocation(createRequest.getLocation())).thenReturn(resource.getLocation());
        when(resourceMapper.toCharacteristic(any(CharacteristicDTO.class))).thenReturn(
                Characteristic.builder()
                        .code("TEST1")
                        .type(CharacteristicType.CONSUMPTION_TYPE)
                        .value("RESIDENTIAL")
                        .build()
        );
        when(resourceRepository.save(any(Resource.class))).thenReturn(resource);
        when(resourceMapper.toResponse(resource)).thenReturn(resourceResponse);

        ResourceResponse result = resourceService.createResource(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(resourceId);
        verify(resourceRepository).save(any(Resource.class));
        verify(eventProducer).sendResourceEvent(any(ResourceEvent.class));
    }

    @Test
    void createResource_WithDuplicateCharacteristics_ThrowsException() {
        CreateResourceRequest requestWithDuplicates = CreateResourceRequest.builder()
                .type(ResourceType.METERING_POINT)
                .countryCode("EE")
                .location(createRequest.getLocation())
                .characteristics(Arrays.asList(
                        CharacteristicDTO.builder()
                                .code("DUP1")
                                .type(CharacteristicType.CONSUMPTION_TYPE)
                                .value("VALUE1")
                                .build(),
                        CharacteristicDTO.builder()
                                .code("DUP1")
                                .type(CharacteristicType.CONSUMPTION_TYPE)
                                .value("VALUE2")
                                .build()
                ))
                .build();

        when(resourceMapper.toEntity(requestWithDuplicates)).thenReturn(resource);
        when(resourceMapper.toLocation(requestWithDuplicates.getLocation())).thenReturn(resource.getLocation());

        assertThatThrownBy(() -> resourceService.createResource(requestWithDuplicates))
                .isInstanceOf(DuplicateCharacteristicException.class)
                .hasMessageContaining("DUP1");
    }

    @Test
    void getResource_Success() {
        when(resourceRepository.findByIdWithCharacteristics(resourceId)).thenReturn(Optional.of(resource));
        when(resourceMapper.toResponse(resource)).thenReturn(resourceResponse);

        ResourceResponse result = resourceService.getResource(resourceId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(resourceId);
        verify(resourceRepository).findByIdWithCharacteristics(resourceId);
    }

    @Test
    void getResource_NotFound() {
        when(resourceRepository.findByIdWithCharacteristics(resourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.getResource(resourceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(resourceId.toString());
    }

    @Test
    void getAllResources_WithAllFilters() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Resource> resourcePage = new PageImpl<>(List.of(resource));

        when(resourceRepository.findByCountryCodeAndType("EE", ResourceType.METERING_POINT, pageable))
                .thenReturn(resourcePage);
        when(resourceMapper.toResponse(resource)).thenReturn(resourceResponse);

        Page<ResourceResponse> result = resourceService.getAllResources("EE", "METERING_POINT", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(resourceRepository).findByCountryCodeAndType("EE", ResourceType.METERING_POINT, pageable);
    }

    @Test
    void getAllResources_WithCountryCodeOnly() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Resource> resourcePage = new PageImpl<>(List.of(resource));

        when(resourceRepository.findByCountryCode("EE", pageable)).thenReturn(resourcePage);
        when(resourceMapper.toResponse(resource)).thenReturn(resourceResponse);

        Page<ResourceResponse> result = resourceService.getAllResources("EE", null, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(resourceRepository).findByCountryCode("EE", pageable);
    }

    @Test
    void getAllResources_WithTypeOnly() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Resource> resourcePage = new PageImpl<>(List.of(resource));

        when(resourceRepository.findByType(ResourceType.METERING_POINT, pageable)).thenReturn(resourcePage);
        when(resourceMapper.toResponse(resource)).thenReturn(resourceResponse);

        Page<ResourceResponse> result = resourceService.getAllResources(null, "METERING_POINT", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(resourceRepository).findByType(ResourceType.METERING_POINT, pageable);
    }

    @Test
    void getAllResources_NoFilters() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Resource> resourcePage = new PageImpl<>(List.of(resource));

        when(resourceRepository.findAll(pageable)).thenReturn(resourcePage);
        when(resourceMapper.toResponse(resource)).thenReturn(resourceResponse);

        Page<ResourceResponse> result = resourceService.getAllResources(null, null, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(resourceRepository).findAll(pageable);
    }

    @Test
    void updateResource_Success() {
        when(resourceRepository.findByIdWithCharacteristics(resourceId)).thenReturn(Optional.of(resource));
        when(resourceMapper.toLocation(updateRequest.getLocation())).thenReturn(
                Location.builder()
                        .streetAddress("Updated Street")
                        .city("Tartu")
                        .postalCode("54321")
                        .countryCode("EE")
                        .build()
        );
        when(resourceRepository.save(resource)).thenReturn(resource);
        when(resourceMapper.toResponse(resource)).thenReturn(resourceResponse);

        ResourceResponse result = resourceService.updateResource(resourceId, updateRequest, null);

        assertThat(result).isNotNull();
        verify(resourceRepository).save(resource);
        verify(eventProducer).sendResourceEvent(any(ResourceEvent.class));
    }

    @Test
    void updateResource_WithOptimisticLocking() {
        resource.setVersion(1L);
        when(resourceRepository.findByIdWithCharacteristics(resourceId)).thenReturn(Optional.of(resource));

        assertThatThrownBy(() -> resourceService.updateResource(resourceId, updateRequest, 999L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(resourceRepository, never()).save(any());
        verify(eventProducer, never()).sendResourceEvent(any());
    }

    @Test
    void updateResource_NotFound() {
        when(resourceRepository.findByIdWithCharacteristics(resourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.updateResource(resourceId, updateRequest, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteResource_Success() {
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
        when(resourceMapper.toResponse(resource)).thenReturn(resourceResponse);

        resourceService.deleteResource(resourceId);

        verify(resourceRepository).delete(resource);

        ArgumentCaptor<ResourceEvent> eventCaptor = ArgumentCaptor.forClass(ResourceEvent.class);
        verify(eventProducer).sendResourceEvent(eventCaptor.capture());

        ResourceEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getEventType()).isEqualTo(EventType.RESOURCE_DELETED);
        assertThat(capturedEvent.getResourceId()).isEqualTo(resourceId);
    }

    @Test
    void deleteResource_NotFound() {
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.deleteResource(resourceId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(resourceRepository, never()).delete(any());
    }

    @Test
    void exportAllToKafka_Success() {
        List<Resource> resources = List.of(resource);
        List<ResourceResponse> responses = List.of(resourceResponse);

        when(resourceRepository.findAllWithCharacteristics()).thenReturn(resources.stream());
        when(resourceMapper.toResponseList(resources)).thenReturn(responses);

        resourceService.exportAllToKafka();

        verify(eventProducer).sendBulkExport(responses);
    }

    @Test
    void getAllResourcesForExport_Success() {
        List<Resource> resources = List.of(resource);
        List<ResourceResponse> responses = List.of(resourceResponse);

        when(resourceRepository.findAllWithCharacteristics()).thenReturn(resources.stream());
        when(resourceMapper.toResponseList(resources)).thenReturn(responses);

        List<ResourceResponse> result = resourceService.getAllResourcesForExport();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(resourceId);
    }
}