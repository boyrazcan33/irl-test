package com.energia.resourcemanagement.repository;

import com.energia.resourcemanagement.domain.entity.Resource;
import com.energia.resourcemanagement.domain.enums.ResourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    @Query("SELECT r FROM Resource r LEFT JOIN FETCH r.characteristics WHERE r.id = :id")
    Optional<Resource> findByIdWithCharacteristics(@Param("id") UUID id);

    List<Resource> findByCountryCode(String countryCode);

    List<Resource> findByType(ResourceType type);

    Page<Resource> findByCountryCodeAndType(String countryCode, ResourceType type, Pageable pageable);

    Page<Resource> findByCountryCode(String countryCode, Pageable pageable);

    Page<Resource> findByType(ResourceType type, Pageable pageable);

    // CHANGED: Modified return type from List to Stream and added QueryHints
    @Query("SELECT r FROM Resource r LEFT JOIN FETCH r.characteristics")
    @QueryHints(@QueryHint(name = HINT_FETCH_SIZE, value = "20000"))
    Stream<Resource> findAllWithCharacteristics();
}