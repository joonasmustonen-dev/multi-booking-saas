package com.example.booking.tenantdata.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacyPolicyRepository
    extends JpaRepository<PrivacyPolicy, Integer>
{
    @org.springframework.data.jpa.repository.Lock(
        jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
    )
    @org.springframework.data.jpa.repository.Query(
        "select p from PrivacyPolicy p where p.id = 1"
    )
    PrivacyPolicy lockPolicy();
}
