package com.example.booking.tenantdata.privacy;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "security_audit_event")
public class SecurityAuditEvent {

    @Id
    UUID id;

    OffsetDateTime occurredAt;

    String actorId;

    String action;

    UUID recordId;

    String requestId;

    int outcome;

    protected SecurityAuditEvent() {}

    public SecurityAuditEvent(
        String actorId,
        String action,
        UUID recordId,
        String requestId,
        int outcome
    ) {
        this.id = UUID.randomUUID();

        this.occurredAt = OffsetDateTime.now();

        this.actorId = actorId;

        this.action = action;

        this.recordId = recordId;

        this.requestId = requestId;

        this.outcome = outcome;
    }

    public record Summary(
        UUID id,
        OffsetDateTime occurredAt,
        String actorId,
        String action,
        UUID recordId,
        String requestId,
        int outcome
    ) {}

    public Summary summary() {
        return new Summary(
            id,
            occurredAt,
            actorId,
            action,
            recordId,
            requestId,
            outcome
        );
    }
}
