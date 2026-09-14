package com.example.booking.tenantdata.privacy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.data.domain.PageRequest;
import java.util.*;

@Service
public class SecurityAuditService {

    private final SecurityAuditRepository events;

    public SecurityAuditService(SecurityAuditRepository events) {
        this.events = events;
    }

    @Transactional(
        value = "tenantTransactionManager",
        propagation = Propagation.REQUIRES_NEW
    )
    public void record(
        String actor,
        String action,
        UUID record,
        String requestId,
        int outcome
    ) {
        events.saveAndFlush(
            new SecurityAuditEvent(
                actor.substring(0, Math.min(actor.length(), 128)),
                action,
                record,
                requestId,
                outcome
            )
        );
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<SecurityAuditEvent.Summary> recent() {
        return events
            .findAllByOrderByOccurredAtDesc(PageRequest.of(0, 50))
            .stream()
            .map(SecurityAuditEvent::summary)
            .toList();
    }
}
