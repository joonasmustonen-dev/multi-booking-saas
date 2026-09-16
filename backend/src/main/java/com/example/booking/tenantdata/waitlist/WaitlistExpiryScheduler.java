package com.example.booking.tenantdata.waitlist;

import com.example.booking.tenant.Tenant;
import com.example.booking.tenant.TenantContext;
import com.example.booking.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WaitlistExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(WaitlistExpiryScheduler.class);
    private final TenantRepository tenants;
    private final WaitlistService waitlist;

    public WaitlistExpiryScheduler(TenantRepository tenants, WaitlistService waitlist) {
        this.tenants = tenants;
        this.waitlist = waitlist;
    }

    @Scheduled(
        initialDelayString = "${app.waitlist.expiry-delay-ms:60000}",
        fixedDelayString = "${app.waitlist.expiry-delay-ms:60000}"
    )
    public void expireOffers() {
        for (Tenant tenant : tenants.findAll()) {
            if (!"ACTIVE".equals(tenant.getStatus())) continue;
            try {
                TenantContext.setTenantId(tenant.getSlug());
                waitlist.expireDue();
            } catch (RuntimeException failure) {
                log.error("Waitlist expiry failed for tenant {}", tenant.getSlug(), failure);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
