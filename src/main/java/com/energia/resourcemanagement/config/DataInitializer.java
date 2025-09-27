package com.energia.resourcemanagement.config;

import com.energia.resourcemanagement.domain.entity.Characteristic;
import com.energia.resourcemanagement.domain.entity.Location;
import com.energia.resourcemanagement.domain.entity.Resource;
import com.energia.resourcemanagement.domain.enums.CharacteristicType;
import com.energia.resourcemanagement.domain.enums.ResourceType;
import com.energia.resourcemanagement.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final ResourceRepository resourceRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Profile("load-test")
    public void generateLargeDataset() {
        long currentCount = resourceRepository.count();
        if (currentCount < 500000) {
            log.info("Generating {} resources for load testing...", 500000 - currentCount);
            generateBulkResources(500000 - (int)currentCount);
        } else {
            log.info("Load test dataset already exists with {} resources", currentCount);
        }
    }

    private void generateBulkResources(int count) {
        String[] cities = {"Tallinn", "Tartu", "Narva", "Helsinki", "Turku", "Tampere"};
        String[] countries = {"EE", "FI"};
        CharacteristicType[] charTypes = CharacteristicType.values();

        List<Resource> batch = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String country = countries[i % 2];
            String city = cities[i % cities.length];

            Resource resource = Resource.builder()
                    .type(i % 2 == 0 ? ResourceType.METERING_POINT : ResourceType.CONNECTION_POINT)
                    .countryCode(country)
                    .location(Location.builder()
                            .streetAddress("Generated Street " + i)
                            .city(city)
                            .postalCode(String.format("%05d", (i % 99999) + 1))
                            .countryCode(country)
                            .build())
                    .build();

            int charCount = 2 + (i % 2);
            for (int j = 0; j < charCount; j++) {
                Characteristic characteristic = Characteristic.builder()
                        .code(String.format("GEN%02d", (i + j) % 100))
                        .type(charTypes[(i + j) % charTypes.length])
                        .value("Generated_" + ((i + j) % 10))
                        .build();
                resource.addCharacteristic(characteristic);
            }

            batch.add(resource);

            if (batch.size() == 1000) {
                resourceRepository.saveAll(batch);
                batch.clear();
                if ((i + 1) % 10000 == 0) {
                    log.info("Generated {} resources", i + 1);
                }
            }
        }

        if (!batch.isEmpty()) {
            resourceRepository.saveAll(batch);
        }

        log.info("Bulk generation completed. Total resources: {}", resourceRepository.count());
    }
}