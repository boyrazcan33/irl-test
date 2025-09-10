package com.energia.resourcemanagement.integration.repository;

import com.energia.resourcemanagement.domain.entity.Characteristic;
import com.energia.resourcemanagement.domain.entity.Location;
import com.energia.resourcemanagement.domain.entity.Resource;
import com.energia.resourcemanagement.domain.enums.CharacteristicType;
import com.energia.resourcemanagement.domain.enums.ResourceType;
import com.energia.resourcemanagement.repository.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ResourceRepositoryTest {

    @Autowired
    private ResourceRepository resourceRepository;

    private Resource testResource;

    @BeforeEach
    void setUp() {
        // Her testten önce veritabanını temizleyelim ki testler birbirini etkilemesin
        resourceRepository.deleteAll();

        testResource = Resource.builder()
                .type(ResourceType.METERING_POINT)
                .countryCode("EE")
                .location(Location.builder()
                        .streetAddress("Repository Test Street")
                        .city("Tallinn")
                        .postalCode("12345")
                        .countryCode("EE")
                        .build())
                .build();

        Characteristic char1 = Characteristic.builder()
                .code("REPO1")
                .type(CharacteristicType.CONSUMPTION_TYPE)
                .value("RESIDENTIAL")
                .build();

        testResource.addCharacteristic(char1);
    }

    @Test
    void saveResource_Success() {
        Resource saved = resourceRepository.save(testResource);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getType()).isEqualTo(ResourceType.METERING_POINT);
        assertThat(saved.getCountryCode()).isEqualTo("EE");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(0L);
    }

    @Test
    void findByIdWithCharacteristics_Success() {
        Resource saved = resourceRepository.save(testResource);

        Optional<Resource> found = resourceRepository.findByIdWithCharacteristics(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCharacteristics()).hasSize(1);
        assertThat(found.get().getCharacteristics().get(0).getCode()).isEqualTo("REPO1");
    }

    @Test
    void findByCountryCode_Success() {
        resourceRepository.save(testResource);

        Resource finnishResource = Resource.builder()
                .type(ResourceType.CONNECTION_POINT)
                .countryCode("FI")
                .location(Location.builder()
                        .streetAddress("Helsinki Street")
                        .city("Helsinki")
                        .postalCode("00100")
                        .countryCode("FI")
                        .build())
                .build();
        resourceRepository.save(finnishResource);

        List<Resource> estonianResources = resourceRepository.findByCountryCode("EE");

        assertThat(estonianResources).hasSize(1);
        assertThat(estonianResources.get(0).getCountryCode()).isEqualTo("EE");
    }

    @Test
    void findByType_Success() {
        resourceRepository.save(testResource);

        Resource connectionPoint = Resource.builder()
                .type(ResourceType.CONNECTION_POINT)
                .countryCode("EE")
                .location(Location.builder()
                        .streetAddress("Connection Street")
                        .city("Tallinn")
                        .postalCode("54321")
                        .countryCode("EE")
                        .build())
                .build();
        resourceRepository.save(connectionPoint);

        List<Resource> meteringPoints = resourceRepository.findByType(ResourceType.METERING_POINT);

        assertThat(meteringPoints).hasSize(1);
        assertThat(meteringPoints.get(0).getType()).isEqualTo(ResourceType.METERING_POINT);
    }

    @Test
    void findByCountryCodeAndType_WithPagination() {
        for (int i = 0; i < 5; i++) {
            Resource resource = Resource.builder()
                    .type(ResourceType.METERING_POINT)
                    .countryCode("EE")
                    .location(Location.builder()
                            .streetAddress("Street " + i)
                            .city("Tallinn")
                            .postalCode("1234" + i)
                            .countryCode("EE")
                            .build())
                    .build();
            resourceRepository.save(resource);
        }

        Page<Resource> page = resourceRepository.findByCountryCodeAndType(
                "EE", ResourceType.METERING_POINT, PageRequest.of(0, 3));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void updateResource_VersionIncremented() {
        Resource saved = resourceRepository.save(testResource);
        Long initialVersion = saved.getVersion();

        saved.getLocation().setCity("Tartu");
        Resource updated = resourceRepository.save(saved);

        assertThat(updated.getVersion()).isEqualTo(initialVersion + 1);
    }

    @Test
    void deleteResource_Success() {
        Resource saved = resourceRepository.save(testResource);
        UUID resourceId = saved.getId();

        resourceRepository.delete(saved);

        Optional<Resource> found = resourceRepository.findById(resourceId);
        assertThat(found).isEmpty();
    }

    @Test
    void findAllWithCharacteristics_Success() {
        // Arrange
        Resource resource1 = Resource.builder()
                .type(ResourceType.METERING_POINT)
                .countryCode("EE")
                .location(Location.builder()
                        .streetAddress("Street 1")
                        .city("Tallinn")
                        .postalCode("11111")
                        .countryCode("EE")
                        .build())
                .build();

        Characteristic char1 = Characteristic.builder()
                .code("ALL1")
                .type(CharacteristicType.CONSUMPTION_TYPE)
                .value("RESIDENTIAL")
                .build();
        resource1.addCharacteristic(char1);

        Resource resource2 = Resource.builder()
                .type(ResourceType.CONNECTION_POINT)
                .countryCode("FI")
                .location(Location.builder()
                        .streetAddress("Street 2")
                        .city("Helsinki")
                        .postalCode("22222")
                        .countryCode("FI")
                        .build())
                .build();

        resourceRepository.save(resource1);
        resourceRepository.save(resource2);

        // Act
        List<Resource> allResources = resourceRepository.findAllWithCharacteristics();

        // Assert
        assertThat(allResources).hasSize(2);

        Resource foundResource1 = allResources.stream()
                .filter(r -> r.getCountryCode().equals("EE"))
                .findFirst().orElseThrow();

        assertThat(foundResource1.getCharacteristics()).hasSize(1);
        assertThat(foundResource1.getCharacteristics().get(0).getCode()).isEqualTo("ALL1");

        Resource foundResource2 = allResources.stream()
                .filter(r -> r.getCountryCode().equals("FI"))
                .findFirst().orElseThrow();

        assertThat(foundResource2.getCharacteristics()).isEmpty();
    }
}