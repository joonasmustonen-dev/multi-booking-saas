package com.example.booking.tenantdata.customer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.booking.tenantdata.CustomerRepository;
import com.example.booking.tenantdata.CustomerService;
import com.example.booking.tenantdata.appointment.AppointmentRepository;
import com.example.booking.tenantdata.staff.StaffMemberRepository;

@Service
@Transactional(value = "tenantTransactionManager", readOnly = true)
public class CustomerOperationsService {

    private final CustomerRepository customers;

    private final CustomerService customerService;

    private final AppointmentRepository appointments;

    private final StaffMemberRepository staff;

    public CustomerOperationsService(
        CustomerRepository customers,
        CustomerService customerService,
        AppointmentRepository appointments,
        StaffMemberRepository staff
    ) {
        this.customers = customers;

        this.customerService = customerService;

        this.appointments = appointments;

        this.staff = staff;
    }

    public List<CustomerResponse> search(String query, int limit) {
        return search(query, limit, false);
    }

    public List<CustomerResponse> search(
        String query,
        int limit,
        boolean bookingOnly
    ) {
        String q = query == null ? "" : query.trim();

        if (
            q.length() > 100 || limit < 1 || limit > 50
        ) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Search limit must be 1–50 and query at most 100 characters"
        );
        String text =
            "%" +
            q
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") +
            "%";

        String digits = q.replaceAll("[^0-9]", "").replaceFirst("^0+", "");

        return customers
            .search(
                text,
                digits.isEmpty() ? "" : "%" + digits + "%",
                !bookingOnly &&
                    com.example.booking.security.ApiPermissions.isTenantAdmin(),
                PageRequest.of(0, limit)
            )
            .stream()
            .map(CustomerResponse::from)
            .toList();
    }

    public CustomerActivityResponse activity(UUID id, int page, int size) {
        if (
            page < 0 || page > 100000 || size < 1 || size > 50
        ) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Invalid history page or size (1–50)"
        );
        var customer = customerService.findById(id);

        OffsetDateTime now = OffsetDateTime.now();

        var stats = appointments.customerStats(id, now);

        var history = appointments.findByCustomer_Id(
            id,
            PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("startAt"), Sort.Order.desc("id"))
            )
        );

        String preferredName =
            customer.isProcessingRestricted() ||
            customer.getPreferredStaffId() == null
                ? null
                : staff
                      .findById(customer.getPreferredStaffId())
                      .map(member -> member.getName())
                      .orElse(null);

        return new CustomerActivityResponse(
            CustomerResponse.from(customer),
            preferredName,
            stats.getTotal(),
            stats.getCompleted(),
            stats.getCancelled(),
            stats.getNoShows(),
            stats.getLastVisit() == null
                ? null
                : stats.getLastVisit().atOffset(java.time.ZoneOffset.UTC),
            customer.isProcessingRestricted()
                ? List.of()
                : appointments
                      .frequentServices(id, now, PageRequest.of(0, 5))
                      .stream()
                      .map(s ->
                          new CustomerActivityResponse.FrequentService(
                              s.getServiceId(),
                              s.getName(),
                              s.getVisits()
                          )
                      )
                      .toList(),
            history
                .stream()
                .map(a ->
                    new CustomerActivityResponse.Booking(
                        a.getId(),
                        a.getService().getId(),
                        a.getService().getName(),
                        a.getStaff() == null ? null : a.getStaff().getName(),
                        a.getLocation() == null
                            ? null
                            : a.getLocation().getName(),
                        a.getResource() == null
                            ? null
                            : a.getResource().getName(),
                        a.getStartAt(),
                        a.getEndAt(),
                        a.getStatus()
                    )
                )
                .toList(),
            page,
            size,
            history.getTotalElements(),
            history.getTotalPages()
        );
    }
}
