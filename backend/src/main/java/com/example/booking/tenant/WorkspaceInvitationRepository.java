package com.example.booking.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface WorkspaceInvitationRepository
    extends JpaRepository<WorkspaceInvitation, UUID> {

    Optional<WorkspaceInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from WorkspaceInvitation invitation where invitation.tokenHash = :tokenHash")
    Optional<WorkspaceInvitation> findLockedByTokenHash(
        @Param("tokenHash") String tokenHash
    );

    Optional<WorkspaceInvitation> findByTenantIdAndNormalizedEmailAndStatus(
        UUID tenantId,
        String normalizedEmail,
        InvitationStatus status
    );

    Optional<WorkspaceInvitation> findByTenantIdAndStaffMemberIdAndStatus(
        UUID tenantId,
        UUID staffMemberId,
        InvitationStatus status
    );

    List<WorkspaceInvitation> findByTenantSlugOrderByCreatedAtDesc(
        String tenantSlug
    );
}
