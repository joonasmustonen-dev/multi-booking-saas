package com.example.booking.tenant;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workspace_memberships")
public class WorkspaceMembership {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "identity_subject", nullable = false, length = 255)
    private String identitySubject;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkspaceRole role;

    @Column(name = "staff_member_id")
    private UUID staffMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MembershipStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "removed_at")
    private OffsetDateTime removedAt;

    protected WorkspaceMembership() {}

    public WorkspaceMembership(
        Tenant tenant,
        String identitySubject,
        String email,
        WorkspaceRole role,
        UUID staffMemberId
    ) {
        this.id = UUID.randomUUID();
        this.tenant = tenant;
        this.identitySubject = identitySubject;
        this.email = email;
        this.role = role;
        this.staffMemberId = staffMemberId;
        this.status = MembershipStatus.ACTIVE;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public String getIdentitySubject() { return identitySubject; }
    public String getEmail() { return email; }
    public WorkspaceRole getRole() { return role; }
    public UUID getStaffMemberId() { return staffMemberId; }
    public MembershipStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public boolean hasUnclaimedIdentity() {
        return identitySubject.startsWith("unclaimed:");
    }

    public void claimIdentity(String identitySubject) {
        if (!hasUnclaimedIdentity()) {
            throw new IllegalStateException("Membership identity is already claimed");
        }
        this.identitySubject = identitySubject;
        this.updatedAt = OffsetDateTime.now();
    }

    public void changeRole(WorkspaceRole role) {
        this.role = role;
        this.updatedAt = OffsetDateTime.now();
    }

    public void linkStaff(UUID staffMemberId) {
        this.staffMemberId = staffMemberId;
        this.updatedAt = OffsetDateTime.now();
    }

    public void reactivate(
        String email,
        WorkspaceRole role,
        UUID staffMemberId
    ) {
        this.email = email;
        this.role = role;
        this.staffMemberId = staffMemberId;
        this.status = MembershipStatus.ACTIVE;
        this.removedAt = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public void remove() {
        this.status = MembershipStatus.REMOVED;
        this.staffMemberId = null;
        this.removedAt = OffsetDateTime.now();
        this.updatedAt = this.removedAt;
    }
}
