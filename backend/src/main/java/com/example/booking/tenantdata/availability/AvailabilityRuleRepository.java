package com.example.booking.tenantdata.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

public interface AvailabilityRuleRepository
    extends JpaRepository<AvailabilityRule, UUID>
{
    List<AvailabilityRule> findByResourceIdAndActiveTrue(UUID resourceId);
    List<AvailabilityRule> findByResourceIdOrderByStartTimeAsc(UUID resourceId);

    List<AvailabilityRule> findByStaff_IdOrderByDayOfWeekAscStartTimeAsc(
        UUID id
    );
    List<AvailabilityRule> findByLocation_IdOrderByDayOfWeekAscStartTimeAsc(
        UUID id
    );
    List<AvailabilityRule> findByResource_IdOrderByDayOfWeekAscStartTimeAsc(
        UUID id
    );

    List<AvailabilityRule> findAllByStaff_IdAndActiveTrue(UUID staffId);
    List<AvailabilityRule> findAllByLocation_IdAndActiveTrue(UUID locationId);

    List<AvailabilityRule> findAllByResource_IdAndDayOfWeekAndActiveTrue(
        UUID resourceId,
        DayOfWeek dayOfWeek
    );

    List<AvailabilityRule> findAllByStaff_IdAndDayOfWeekAndActiveTrue(
        UUID staffId,
        DayOfWeek dayOfWeek
    );

    List<AvailabilityRule> findAllByLocation_IdAndDayOfWeekAndActiveTrue(
        UUID locationId,
        DayOfWeek dayOfWeek
    );
}
