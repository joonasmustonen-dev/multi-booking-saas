package com.example.booking.tenantdata.staff;

import com.example.booking.tenantdata.management.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service
@Transactional("tenantTransactionManager")
public class StaffMemberService {

    private final StaffMemberRepository repository;

    private final com.example.booking.tenantdata.location.LocationRepository locations;

    private final com.example.booking.tenantdata.service.ServiceOfferingRepository offerings;

    public StaffMemberService(
        StaffMemberRepository repository,
        com.example.booking.tenantdata.location.LocationRepository locations,
        com.example.booking.tenantdata.service.ServiceOfferingRepository offerings
    ) {
        this.repository = repository;

        this.locations = locations;

        this.offerings = offerings;
    }

    public StaffMemberResponse create(CreateAssignmentRequest request) {
        return StaffMemberResponse.from(
            repository.save(new StaffMember(request.name()))
        );
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<StaffMemberResponse> findAll() {
        return repository
            .findAll()
            .stream()
            .filter(member -> !member.isRemoved())
            .sorted(
                Comparator.comparing(StaffMember::getName).thenComparing(
                    StaffMember::getId
                )
            )
            .map(StaffMemberResponse::from)
            .toList();
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public StaffMemberResponse findById(UUID id) {
        return StaffMemberResponse.from(entity(id));
    }

    public StaffMemberResponse update(
        UUID id,
        UpdateAssignmentRequest request
    ) {
        var entity = entity(id);

        entity.update(request.name(), request.active());

        return StaffMemberResponse.from(entity);
    }

    public StaffMemberResponse setActive(UUID id, boolean active) {
        var entity = entity(id);

        entity.update(entity.getName(), active);

        return StaffMemberResponse.from(entity);
    }

    public StaffMemberResponse create(StaffMemberRequest request) {
        var member = new StaffMember(request.name().trim());

        apply(member, request);

        return StaffMemberResponse.from(repository.save(member));
    }

    public StaffMemberResponse update(UUID id, StaffMemberRequest request) {
        var member = entity(id);

        apply(member, request);

        return StaffMemberResponse.from(member);
    }

    public void remove(UUID id) {
        var member = entity(id);

        // Keep the staff row for appointment history, remove them from future service eligibility.
        for (var offering : offerings.findAll())
            offering.getStaff().removeIf(staff -> id.equals(staff.getId()));

        member.remove();
    }

    private void apply(StaffMember member, StaffMemberRequest request) {
        member.update(
            request.name().trim(),
            request.active() == null ? member.isActive() : request.active()
        );

        member.setContacts(
            request.email() == null
                ? member.getEmail()
                : request.email().trim(),
            request.phone() == null ? member.getPhone() : request.phone().trim()
        );

        boolean freeAgent =
            request.freeAgent() == null
                ? member.isFreeAgent()
                : request.freeAgent();

        var ids =
            request.locationIds() == null
                ? member
                      .getLocations()
                      .stream()
                      .map(location -> location.getId())
                      .collect(java.util.stream.Collectors.toSet())
                : request.locationIds();

        if (!freeAgent && ids.isEmpty()) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Select a location or mark the staff member as a free agent"
        );
        var resolved = new HashSet<>(locations.findAllById(ids));

        if (resolved.size() != ids.size()) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "A location was not found in this tenant"
        );
        member.assignLocations(freeAgent, freeAgent ? Set.of() : resolved);
    }

    private StaffMember entity(UUID id) {
        return repository
            .findById(id)
            .filter(member -> !member.isRemoved())
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "StaffMember not found"
                )
            );
    }
}
