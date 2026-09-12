package com.example.booking.tenantdata.appointment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.booking.tenantdata.appointment.AppointmentStatus;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository
        extends JpaRepository<Appointment, UUID> {

    @EntityGraph(attributePaths = {
            "customer",
            "service",
            "resource"
    })
    @Query("""
        SELECT a
        FROM Appointment a
        WHERE a.id = :id
        """)
    Optional<Appointment> findByIdWithDetails(
            @Param("id") UUID id
    );

    @EntityGraph(attributePaths = {
            "customer",
            "service",
            "resource"
    })
    List<Appointment> findAllByOrderByStartAtAsc();

    @Query("""
        SELECT a
        FROM Appointment a
        WHERE a.resource.id = :resourceId
          AND a.status IN :statuses
          AND a.startAt < :rangeEnd
          AND a.endAt > :rangeStart
        """)
    List<Appointment> findOverlapping(
            @Param("resourceId") UUID resourceId,
            @Param("rangeStart") OffsetDateTime rangeStart,
            @Param("rangeEnd") OffsetDateTime rangeEnd,
            @Param("statuses")
            Collection<AppointmentStatus> statuses
    );


    @Query("""
    SELECT a
    FROM Appointment a
    JOIN FETCH a.customer
    JOIN FETCH a.service
    JOIN FETCH a.resource
    WHERE a.startAt < :to
      AND a.endAt > :from
      AND (:resourceId IS NULL OR a.resource.id = :resourceId)
      AND (:customerId IS NULL OR a.customer.id = :customerId)
      AND (:serviceId IS NULL OR a.service.id = :serviceId)
      AND (:status IS NULL OR a.status = :status)
    ORDER BY a.startAt ASC
    """)
    List<Appointment> findCalendar(
        @Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to,
        @Param("resourceId") UUID resourceId,
        @Param("customerId") UUID customerId,
        @Param("serviceId") UUID serviceId,
        @Param("status") AppointmentStatus status
        );
}