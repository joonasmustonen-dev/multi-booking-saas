package com.example.booking.tenantdata.availability;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AvailabilityExceptionRepository
    extends JpaRepository<AvailabilityException, UUID>
{
    List<AvailabilityException> findByStaff_IdOrderByStartAtAsc(UUID id);
    List<AvailabilityException> findByLocation_IdOrderByStartAtAsc(UUID id);
    List<AvailabilityException> findByResourceIdOrderByStartAtAsc(
        UUID resourceId
    );

    List<AvailabilityException> findByResourceIdAndEndAtAfterAndStartAtBefore(
        UUID resourceId,
        OffsetDateTime rangeStart,
        OffsetDateTime rangeEnd
    );

    @Query(
        """
            select e
            from AvailabilityException e
            where e.resource.id = :resourceId
            and e.startAt < :endAt
            and e.endAt > :startAt
            """
    )
    List<AvailabilityException> findResourceExceptions(
        @Param("resourceId") UUID resourceId,
        @Param("startAt") OffsetDateTime startAt,
        @Param("endAt") OffsetDateTime endAt
    );

    @Query(
        """
        select e
        from AvailabilityException e
        where e.staff.id = :staffId
          and e.startAt < :endAt
          and e.endAt > :startAt
        """
    )
    List<AvailabilityException> findStaffExceptions(
        @Param("staffId") UUID staffId,
        @Param("startAt") OffsetDateTime startAt,
        @Param("endAt") OffsetDateTime endAt
    );

    @Query(
        """
        select e
        from AvailabilityException e
        where e.location.id = :locationId
          and e.startAt < :endAt
          and e.endAt > :startAt
        """
    )
    List<AvailabilityException> findLocationExceptions(
        @Param("locationId") UUID locationId,
        @Param("startAt") OffsetDateTime startAt,
        @Param("endAt") OffsetDateTime endAt
    );

    List<AvailabilityException> findByStaff_IdAndScheduleWeek(
        UUID staffId,
        java.time.LocalDate scheduleWeek
    );
}
