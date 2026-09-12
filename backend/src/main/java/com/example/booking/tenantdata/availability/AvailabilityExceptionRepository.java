package com.example.booking.tenantdata.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AvailabilityExceptionRepository
        extends JpaRepository<AvailabilityException, UUID> {

    List<AvailabilityException>
        findByResourceIdAndEndAtAfterAndStartAtBefore(
                UUID resourceId,
                OffsetDateTime rangeStart,
                OffsetDateTime rangeEnd
        );
}