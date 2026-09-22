package com.example.booking.tenant;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workspace_invitations")
public class WorkspaceInvitation {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "normalized_email", nullable = false, length = 254)
    private String normalizedEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkspaceRole role;

    @Column(name = "staff_member_id")
    private UUID staffMemberId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "invited_by_subject", nullable = false, length = 255)
    private String invitedBySubject;

    @Column(name = "accepted_by_subject", length = 255)
    private String acceptedBySubject;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    protected WorkspaceInvitation() {}

    public WorkspaceInvitation(
        Tenant tenant,
        String email,
        WorkspaceRole role,
        UUID staffMemberId,
        String tokenHash,
        OffsetDateTime expiresAt,
        String invitedBySubject
    ) {
        this.id = UUID.randomUUID();
        this.tenant = tenant;
        this.normalizedEmail = email;
        this.role = role;
        this.staffMemberId = staffMemberId;
        this.tokenHash = tokenHash;
        this.status = InvitationStatus.PENDING;
        this.expiresAt = expiresAt;
        this.invitedBySubject = invitedBySubject;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public String getNormalizedEmail() { return normalizedEmail; }
    public WorkspaceRole getRole() { return role; }
    public UUID getStaffMemberId() { return staffMemberId; }
    public String getTokenHash() { return tokenHash; }
    public InvitationStatus getStatus() { return status; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public String getInvitedBySubject() { return invitedBySubject; }
    public String getAcceptedBySubject() { return acceptedBySubject; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public boolean expireIfNeeded(OffsetDateTime now) {
        if (status == InvitationStatus.PENDING && !expiresAt.isAfter(now)) {
            status = InvitationStatus.EXPIRED;
            return true;
        }
        return false;
    }

    public void accept(String subject) {
        status = InvitationStatus.ACCEPTED;
        acceptedBySubject = subject;
        acceptedAt = OffsetDateTime.now();
    }

    public void revoke() {
        status = InvitationStatus.REVOKED;
        revokedAt = OffsetDateTime.now();
    }
}
