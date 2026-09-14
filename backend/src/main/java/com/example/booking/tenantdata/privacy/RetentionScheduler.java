package com.example.booking.tenantdata.privacy;

import com.example.booking.tenant.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
public class RetentionScheduler {

    private final TenantRepository tenants;

    private final PrivacyService privacy;

    private final SecurityAuditService audit;

    @Value("${app.privacy.scheduled-retention:true}")
    private boolean enabled;

    public RetentionScheduler(
        TenantRepository tenants,
        PrivacyService privacy,
        SecurityAuditService audit
    ) {
        this.tenants = tenants;

        this.privacy = privacy;

        this.audit = audit;
    }

    @Scheduled(
        initialDelayString = "${app.privacy.retention-delay-ms:86400000}",
        fixedDelayString = "${app.privacy.retention-delay-ms:86400000}"
    )
    public void cleanup() {
        if (!enabled) return;
        for (Tenant tenant : tenants.findAll()) {
            if (!"ACTIVE".equals(tenant.getStatus())) continue;
            try {
                TenantContext.setTenantId(tenant.getSlug());

                var preview = privacy.preview();

                if (
                    !preview.policy().scheduledRetention() ||
                    preview.customers() +
                        preview.notes() +
                        preview.staffContacts() +
                        preview.auditEvents() ==
                        0
                ) continue;
                privacy.apply(
                    new PrivacyService.ApplyRequest(preview.token(), true)
                );

                audit.record("system", "RETENTION:scheduled", null, null, 200);
            } catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(
                    RetentionScheduler.class
                ).error(
                    "Scheduled retention failed ({})",
                    failure.getClass().getSimpleName()
                );
            } finally {
                TenantContext.clear();
            }
        }
    }
}
