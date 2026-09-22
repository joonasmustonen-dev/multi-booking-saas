package com.example.booking.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WorkspaceAccessAuditRepository
    extends JpaRepository<WorkspaceAccessAudit, UUID> {}
