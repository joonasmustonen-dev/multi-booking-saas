package com.example.booking.tenantdata.waitlist;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitlistOfferRepository extends JpaRepository<WaitlistOffer, UUID> {
    @EntityGraph(attributePaths = {"staff", "location", "resource", "appointment"})
    Optional<WaitlistOffer> findByEntry_Id(UUID entryId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"staff", "location", "resource", "appointment"})
    @Query("select o from WaitlistOffer o where o.entry.id = :entryId")
    Optional<WaitlistOffer> findByEntryForUpdate(@Param("entryId") UUID entryId);
}
