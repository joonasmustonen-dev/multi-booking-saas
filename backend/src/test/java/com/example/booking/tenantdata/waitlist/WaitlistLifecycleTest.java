package com.example.booking.tenantdata.waitlist;

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class WaitlistLifecycleTest {

    @Test
    void recordsConsentAndMovesThroughOfferAndAcceptance() {
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        WaitlistEntry entry = entry(start, true);

        assertEquals(WaitlistStatus.WAITING, entry.getStatus());
        assertNotNull(entry.getConsentRecordedAt());

        entry.offer();
        WaitlistOffer offer = new WaitlistOffer(
            entry, null, null, null, start, start.plusHours(1),
            NotificationChannel.EMAIL, OffsetDateTime.now().plusMinutes(30)
        );

        assertEquals(WaitlistStatus.OFFERED, entry.getStatus());
        assertEquals(WaitlistStatus.OFFERED, offer.getStatus());

        offer.accept(null);
        entry.accept();

        assertEquals(WaitlistStatus.ACCEPTED, entry.getStatus());
        assertEquals(WaitlistStatus.ACCEPTED, offer.getStatus());
        assertThrows(IllegalStateException.class, entry::remove);
    }

    @Test
    void activeEntriesCanExpireOrBeRemovedButCannotBeOfferedTwice() {
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        WaitlistEntry expired = entry(start, false);
        assertNull(expired.getConsentRecordedAt());
        expired.expire();
        assertEquals(WaitlistStatus.EXPIRED, expired.getStatus());
        assertThrows(IllegalStateException.class, expired::offer);

        WaitlistEntry removed = entry(start, true);
        removed.remove();
        assertEquals(WaitlistStatus.REMOVED, removed.getStatus());
        assertThrows(IllegalStateException.class, removed::offer);
    }

    private WaitlistEntry entry(OffsetDateTime start, boolean consent) {
        return new WaitlistEntry(
            null, null, null, null, start, start.plusDays(2),
            start.plusDays(2), consent
        );
    }
}
