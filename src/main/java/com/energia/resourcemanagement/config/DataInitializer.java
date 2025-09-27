package com.energia.resourcemanagement.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    @Profile("load-test")
    public void generateLargeDataset() {
        Long currentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM resources", Long.class);

        if (currentCount != null && currentCount < 500000) {
            log.info("Starting JDBC batch insert for {} resources...", 500000 - currentCount);
            long startTime = System.currentTimeMillis();

            generateBulkResourcesWithJDBC(500000 - currentCount.intValue());

            long duration = System.currentTimeMillis() - startTime;
            log.info("Completed in {} seconds", duration / 1000);
        } else {
            log.info("Load test dataset already exists with {} resources", currentCount);
        }
    }

    @Transactional
    private void generateBulkResourcesWithJDBC(int count) {
        String[] cities = {"Tallinn", "Tartu", "Narva", "Helsinki", "Turku", "Tampere"};
        String[] countries = {"EE", "FI"};
        String[] charTypes = {"CONSUMPTION_TYPE", "CHARGING_POINT", "CONNECTION_POINT_STATUS"};

        String resourceSql = "INSERT INTO resources (id, type, country_code, street_address, city, postal_code, " +
                "location_country_code, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String charSql = "INSERT INTO characteristics (id, resource_id, code, type, value) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement resourcePs = conn.prepareStatement(resourceSql);
                 PreparedStatement charPs = conn.prepareStatement(charSql)) {

                int resourceBatchCount = 0;
                int charBatchCount = 0;

                for (int i = 0; i < count; i++) {
                    UUID resourceId = UUID.randomUUID();
                    String country = countries[i % 2];
                    String city = cities[i % cities.length];
                    String type = (i % 2 == 0) ? "METERING_POINT" : "CONNECTION_POINT";

                    // Add resource
                    resourcePs.setObject(1, resourceId);
                    resourcePs.setString(2, type);
                    resourcePs.setString(3, country);
                    resourcePs.setString(4, "Generated Street " + i);
                    resourcePs.setString(5, city);
                    resourcePs.setString(6, String.format("%05d", (i % 99999) + 1));
                    resourcePs.setString(7, country);
                    resourcePs.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
                    resourcePs.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
                    resourcePs.setLong(10, 0L);
                    resourcePs.addBatch();
                    resourceBatchCount++;

                    // Add characteristics
                    int charCount = 2 + (i % 2);
                    for (int j = 0; j < charCount; j++) {
                        charPs.setObject(1, UUID.randomUUID());
                        charPs.setObject(2, resourceId);
                        charPs.setString(3, String.format("GEN%02d", (i + j) % 100));
                        charPs.setString(4, charTypes[(i + j) % charTypes.length]);
                        charPs.setString(5, "Generated_" + ((i + j) % 10));
                        charPs.addBatch();
                        charBatchCount++;
                    }

                    // Execute batch every 1000 resources
                    if (resourceBatchCount >= 1000) {
                        resourcePs.executeBatch();
                        charPs.executeBatch();
                        conn.commit();

                        resourceBatchCount = 0;
                        charBatchCount = 0;

                        if ((i + 1) % 10000 == 0) {
                            log.info("Inserted {} resources", i + 1);
                        }
                    }
                }

                // Execute remaining batch
                if (resourceBatchCount > 0) {
                    resourcePs.executeBatch();
                    charPs.executeBatch();
                    conn.commit();
                }

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }

            conn.setAutoCommit(true);

        } catch (Exception e) {
            log.error("Batch insert failed: ", e);
            throw new RuntimeException("Failed to generate bulk data", e);
        }

        log.info("JDBC batch insert completed. Total resources: {}",
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM resources", Long.class));
    }
}