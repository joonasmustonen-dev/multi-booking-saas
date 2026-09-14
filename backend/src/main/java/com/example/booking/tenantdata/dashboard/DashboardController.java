package com.example.booking.tenantdata.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize(
        "hasAnyRole('TENANT_ADMIN', 'STAFF')"
)
public class DashboardController {

    private final DashboardService service;


    public DashboardController(
            DashboardService service) {

        this.service = service;
    }


    @GetMapping("/summary")
    public DashboardSummaryResponse getSummary() {

        return service.getSummary();
    }
}