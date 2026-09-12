package com.example.booking.tenantdata.resource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookableResourceRepository
        extends JpaRepository<BookableResource, UUID> {

    List<BookableResource> findByActiveTrue();
}