package com.example.booking.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select membership
        from WorkspaceMembership membership
        where lower(membership.email) = lower(:email)
          and membership.status = :status
        order by membership.createdAt
        """)
    List<WorkspaceMembership> findByVerifiedEmailForUpdate(
        @Param("email") String email,
        @Param("status") MembershipStatus status
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
