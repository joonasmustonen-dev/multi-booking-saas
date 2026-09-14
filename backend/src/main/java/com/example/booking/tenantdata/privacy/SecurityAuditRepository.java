package com.example.booking.tenantdata.privacy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface SecurityAuditRepository
    extends JpaRepository<SecurityAuditEvent, UUID>
{
    List<SecurityAuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
