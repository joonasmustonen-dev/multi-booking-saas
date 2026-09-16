package com.example.booking.tenantdata.waitlist;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, UUID> {
    @EntityGraph(attributePaths = {"customer", "service", "preferredStaff", "preferredLocation"})
    List<WaitlistEntry> findByCustomer_IdOrderByCreatedAtDesc(
        UUID customerId,
        org.springframework.data.domain.Pageable pageable
    );

    @EntityGraph(attributePaths = {"customer", "service", "preferredStaff", "preferredLocation"})
    @Query("select w from WaitlistEntry w order by w.createdAt desc")
    List<WaitlistEntry> findAllWithDetails(
        org.springframework.data.domain.Pageable pageable
    );

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"customer", "service", "preferredStaff", "preferredLocation"})
    @Query("select w from WaitlistEntry w where w.id = :id")
    Optional<WaitlistEntry> findForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"customer", "service", "preferredStaff", "preferredLocation"})
    @Query("""
        select w from WaitlistEntry w
        where w.service.id = :serviceId and w.status = 'WAITING'
          and w.expiresAt > :now
          and w.windowStart <= :startAt and w.windowEnd >= :endAt
          and (w.preferredStaff is null or w.preferredStaff.id = :staffId)
          and (w.preferredLocation is null or w.preferredLocation.id = :locationId)
        order by w.createdAt asc
        """)
    List<WaitlistEntry> findCandidates(
        @Param("serviceId") UUID serviceId,
        @Param("staffId") UUID staffId,
        @Param("locationId") UUID locationId,
        @Param("startAt") OffsetDateTime startAt,
        @Param("endAt") OffsetDateTime endAt,
        @Param("now") OffsetDateTime now
    );

    @Query("""
        select count(w) from WaitlistEntry w
        where w.customer.id = :customerId and w.service.id = :serviceId
          and w.status in ('WAITING', 'OFFERED')
          and w.windowStart < :windowEnd and w.windowEnd > :windowStart
        """)
    long countActiveOverlaps(
        @Param("customerId") UUID customerId,
        @Param("serviceId") UUID serviceId,
        @Param("windowStart") OffsetDateTime windowStart,
        @Param("windowEnd") OffsetDateTime windowEnd
    );

    @EntityGraph(attributePaths = {"customer", "service", "preferredStaff", "preferredLocation"})
    @Query("""
        select distinct w from WaitlistEntry w left join WaitlistOffer o on o.entry = w
        where w.status in ('WAITING', 'OFFERED')
          and (w.expiresAt <= :now or (o.status = 'OFFERED' and o.expiresAt <= :now))
        order by w.createdAt
        """)
    List<WaitlistEntry> findExpired(@Param("now") OffsetDateTime now,
        org.springframework.data.domain.Pageable pageable);
}
