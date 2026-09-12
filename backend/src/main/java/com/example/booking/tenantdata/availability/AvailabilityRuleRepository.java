package com.example.booking.tenantdata.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AvailabilityRuleRepository
        extends JpaRepository<AvailabilityRule, UUID> {

    List<AvailabilityRule>
        findByResourceIdAndActiveTrue(UUID resourceId);
}