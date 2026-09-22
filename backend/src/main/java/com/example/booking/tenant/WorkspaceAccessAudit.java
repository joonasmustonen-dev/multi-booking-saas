package com.example.booking.tenant;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workspace_access_audit")
public class WorkspaceAccessAudit {

    @Id
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "actor_subject", nullable = false, length = 255)
    private String actorSubject;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "target_subject", length = 255)
    private String targetSubject;

    @Column(name = "invitation_id")
    private UUID invitationId;

    @Column(nullable = false, length = 500)
    private String detail;

    protected WorkspaceAccessAudit() {}

    public WorkspaceAccessAudit(
        UUID tenantId,
        String actorSubject,
        String action,
        String targetSubject,
        UUID invitationId,
        String detail
    ) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.occurredAt = OffsetDateTime.now();
        this.actorSubject = actorSubject;
        this.action = action;
        this.targetSubject = targetSubject;
        this.invitationId = invitationId;
        this.detail = detail == null ? "" : detail;
    }
}
