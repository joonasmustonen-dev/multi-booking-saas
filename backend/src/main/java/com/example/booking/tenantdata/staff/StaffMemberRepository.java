package com.example.booking.tenantdata.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StaffMemberRepository
        extends JpaRepository<StaffMember, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from StaffMember s where s.id = :id and s.removed = false")
    java.util.Optional<StaffMember> findForScheduleUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}