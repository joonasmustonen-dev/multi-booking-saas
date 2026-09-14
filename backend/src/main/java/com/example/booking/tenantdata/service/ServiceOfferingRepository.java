package com.example.booking.tenantdata.service;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOfferingRepository
    extends JpaRepository<ServiceOffering, UUID>
{
    @EntityGraph(attributePaths = { "resources", "staff", "locations" })
    @Query("select distinct s from ServiceOffering s")
    List<ServiceOffering> findAllWithResources();

    @EntityGraph(attributePaths = { "resources", "staff", "locations" })
    @Query(
        """
        select s
        from ServiceOffering s
        where s.id = :id
        """
    )
    Optional<ServiceOffering> findByIdWithResources(@Param("id") UUID id);
}
