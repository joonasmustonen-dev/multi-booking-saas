package com.example.booking.tenantdata.appointment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.booking.tenantdata.appointment.AppointmentStatus;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository
    extends JpaRepository<Appointment, UUID>
{
    boolean existsByCustomer_Id(UUID customerId);

    @EntityGraph(
        attributePaths = {
            "customer",
            "service",
            "staff",
            "location",
            "resource"
        }
    )
    org.springframework.data.domain.Page<Appointment> findByCustomer_Id(
        UUID customerId,
        org.springframework.data.domain.Pageable pageable
    );

    interface CustomerStats {
        Long getTotal();
        Long getCompleted();
        Long getCancelled();
        Long getNoShows();
        java.time.Instant getLastVisit();
    }

    @Query(
        value = """
        SELECT count(*) AS total,
          count(*) FILTER (WHERE status = 'COMPLETED') AS completed,
          count(*) FILTER (WHERE status = 'CANCELLED') AS cancelled,
          count(*) FILTER (WHERE status = 'NO_SHOW') AS "noShows",
          max(end_at) FILTER (WHERE status = 'COMPLETED' AND end_at <= :now) AS "lastVisit"
        FROM appointments WHERE customer_id = :customerId
        """,
        nativeQuery = true
    )
    CustomerStats customerStats(
        @Param("customerId") UUID customerId,
        @Param("now") OffsetDateTime now
    );

    interface FrequentService {
        UUID getServiceId();
        String getName();
        Long getVisits();
    }

    @Query(
        value = """
        SELECT s.id AS "serviceId", s.name AS name, count(*) AS visits
        FROM appointments a JOIN services s ON s.id = a.service_id
        WHERE a.customer_id = :customerId AND a.status = 'COMPLETED' AND a.end_at <= :now
        GROUP BY s.id, s.name ORDER BY count(*) DESC, s.name, s.id
        """,
        nativeQuery = true
    )
    List<FrequentService> frequentServices(
        @Param("customerId") UUID customerId,
        @Param("now") OffsetDateTime now,
        org.springframework.data.domain.Pageable pageable
    );

    @EntityGraph(attributePaths = { "staff", "location", "resource" })
    @Query(
        """
        select a from Appointment a
        where a.status in :statuses
          and a.startAt < :rangeEnd
          and a.endAt > :rangeStart
        """
    )
    List<Appointment> findAssignmentBookingsInRange(
        @Param("rangeStart") OffsetDateTime rangeStart,
        @Param("rangeEnd") OffsetDateTime rangeEnd,
        @Param("statuses") Collection<AppointmentStatus> statuses
    );

    @EntityGraph(
        attributePaths = {
            "customer",
            "service",
            "resource",
            "staff",
            "location"
        }
    )
    @Query(
        """
        SELECT a
        FROM Appointment a
        WHERE a.id = :id
        """
    )
    Optional<Appointment> findByIdWithDetails(@Param("id") UUID id);

    @EntityGraph(
        attributePaths = {
            "customer",
            "service",
            "resource",
            "staff",
            "location"
        }
    )
    List<Appointment> findAllByOrderByStartAtAsc();

    @Query(
        """
        SELECT a
        FROM Appointment a
        WHERE a.resource.id = :resourceId
          AND a.status IN :statuses
          AND a.startAt < :rangeEnd
          AND a.endAt > :rangeStart
        """
    )
    List<Appointment> findOverlapping(
        @Param("resourceId") UUID resourceId,
        @Param("rangeStart") OffsetDateTime rangeStart,
        @Param("rangeEnd") OffsetDateTime rangeEnd,
        @Param("statuses") Collection<AppointmentStatus> statuses
    );

    @Query(
        """
    SELECT a
    FROM Appointment a
    JOIN FETCH a.customer
    JOIN FETCH a.service
    LEFT JOIN FETCH a.resource
    LEFT JOIN FETCH a.staff
    LEFT JOIN FETCH a.location
    WHERE a.startAt < :to
      AND a.endAt > :from
      AND (:resourceId IS NULL OR a.resource.id = :resourceId)
      AND (:staffId IS NULL OR a.staff.id = :staffId)
      AND (:locationId IS NULL OR a.location.id = :locationId)
      AND (:customerId IS NULL OR a.customer.id = :customerId)
      AND (:serviceId IS NULL OR a.service.id = :serviceId)
      AND (:status IS NULL OR a.status = :status)
    ORDER BY a.startAt ASC
    """
    )
    List<Appointment> findCalendar(
        @Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to,
        @Param("resourceId") UUID resourceId,
        @Param("staffId") UUID staffId,
        @Param("locationId") UUID locationId,
        @Param("customerId") UUID customerId,
        @Param("serviceId") UUID serviceId,
        @Param("status") AppointmentStatus status
    );

    @Query(
        """
        select
            case
                when count(a) > 0 then true
                else false
            end
        from Appointment a
        where a.status in :statuses

          and a.startAt < :endAt
          and a.endAt > :startAt

          and (
              :ignoredAppointmentId is null
              or a.id <> :ignoredAppointmentId
          )

          and (
              (
                  :staffId is not null
                  and a.staff.id = :staffId
              )
              or
              (
                  :locationId is not null
                  and a.location.id = :locationId
              )
              or
              (
                  :resourceId is not null
                  and a.resource.id = :resourceId
              )
          )
        """
    )
    boolean hasAssignmentConflict(
        @Param("staffId") UUID staffId,
        @Param("locationId") UUID locationId,
        @Param("resourceId") UUID resourceId,
        @Param("startAt") OffsetDateTime startAt,
        @Param("endAt") OffsetDateTime endAt,
        @Param("ignoredAppointmentId") UUID ignoredAppointmentId,
        @Param("statuses") Set<AppointmentStatus> statuses
    );
}
