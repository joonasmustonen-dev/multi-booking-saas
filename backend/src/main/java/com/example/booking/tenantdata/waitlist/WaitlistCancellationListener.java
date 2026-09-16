package com.example.booking.tenantdata.waitlist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WaitlistCancellationListener {
    private static final Logger log = LoggerFactory.getLogger(WaitlistCancellationListener.class);
    private final WaitlistService waitlist;
    public WaitlistCancellationListener(WaitlistService waitlist) { this.waitlist = waitlist; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancellation(AppointmentCancelledEvent event) {
        try {
            waitlist.matchCancellation(event);
        } catch (RuntimeException failure) {
            log.error("Waitlist matching failed after an appointment cancellation", failure);
        }
    }
}
