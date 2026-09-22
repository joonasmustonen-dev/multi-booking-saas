package com.example.booking.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface WorkspaceMembershipRepository
    extends JpaRepository<WorkspaceMembership, UUID> {

    Optional<WorkspaceMembership> findByTenantSlugAndIdentitySubjectAndStatus(
        String tenantSlug,
        String identitySubject,
        MembershipStatus status
    );

    Optional<WorkspaceMembership> findByTenantIdAndIdentitySubject(
        UUID tenantId,
        String identitySubject
    );

    Optional<WorkspaceMembership> findByTenantIdAndStaffMemberIdAndStatus(
        UUID tenantId,
        UUID staffMemberId,
        MembershipStatus status
    );

    List<WorkspaceMembership> findByIdentitySubjectAndStatusOrderByCreatedAt(
        String identitySubject,
        MembershipStatus status
    );

    List<WorkspaceMembership> findByEmailIgnoreCaseAndStatusOrderByCreatedAt(
        String email,
        MembershipStatus status
    );

    Optional<WorkspaceMembership> findByTenantSlugAndEmailIgnoreCaseAndStatus(
        String tenantSlug,
        String email,
        MembershipStatus status
    );

    List<WorkspaceMembership> findByTenantSlugAndStatusOrderByCreatedAt(
        String tenantSlug,
        MembershipStatus status
    );

    long countByTenantIdAndRoleAndStatus(
        UUID tenantId,
        WorkspaceRole role,
        MembershipStatus status
    );

    boolean existsByTenantIdAndEmailIgnoreCaseAndStatus(
        UUID tenantId,
        String email,
        MembershipStatus status
    );
}
