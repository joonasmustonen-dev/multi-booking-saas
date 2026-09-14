package com.example.booking.tenantdata.service;

import com.example.booking.tenantdata.staff.StaffMember;
import com.example.booking.tenantdata.location.Location;
import com.example.booking.tenantdata.resource.BookableResource;
import com.example.booking.tenantdata.resource.ResourceType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class AssignmentPolicy {

    private AssignmentPolicy() {}

    public static void validateDefinition(ServiceOffering service) {
        if (
            service.getStaffRequirement() == null ||
            service.getLocationRequirement() == null ||
            service.getResourceRequirement() == null
        ) fail("All assignment requirements must be specified");

        if (
            service.getStaffRequirement() != AssignmentRequirement.REQUIRED &&
            service.getLocationRequirement() !=
                AssignmentRequirement.REQUIRED &&
            service.getResourceRequirement() != AssignmentRequirement.REQUIRED
        ) {
            fail("At least one assignment category must be required");
        }
        definition(
            service.getStaffRequirement(),
            service.getStaff().size(),
            "Staff"
        );

        definition(
            service.getLocationRequirement(),
            service.getLocations().size(),
            "Location"
        );

        definition(
            service.getResourceRequirement(),
            service.getResources().size(),
            "Resource"
        );
    }

    private static void definition(
        AssignmentRequirement requirement,
        int eligibleCount,
        String label
    ) {
        if (
            requirement == AssignmentRequirement.REQUIRED && eligibleCount == 0
        ) fail(label + " requires an eligible assignment");

        if (
            requirement == AssignmentRequirement.FORBIDDEN && eligibleCount != 0
        ) fail(label + " is forbidden; clear its eligible assignments");
    }

    public static void validatePresence(
        AssignmentRequirement requirement,
        Object assignment,
        String label
    ) {
        if (requirement == null) fail(label + " requirement is not configured");

        if (
            requirement == AssignmentRequirement.REQUIRED && assignment == null
        ) fail(label + " is required");

        if (
            requirement == AssignmentRequirement.FORBIDDEN && assignment != null
        ) fail(label + " is forbidden for this service");
    }

    public static void validateFilter(
        AssignmentRequirement requirement,
        Object requestedId,
        String label
    ) {
        if (requirement == null) fail(label + " requirement is not configured");

        if (
            requirement == AssignmentRequirement.FORBIDDEN &&
            requestedId != null
        ) fail(label + " is forbidden for this service");
    }

    public static void validateAssignments(
        ServiceOffering service,
        StaffMember staff,
        Location location,
        BookableResource resource
    ) {
        validatePresence(service.getStaffRequirement(), staff, "Staff");

        validatePresence(
            service.getLocationRequirement(),
            location,
            "Location"
        );

        validatePresence(
            service.getResourceRequirement(),
            resource,
            "Resource"
        );

        if (staff == null && location == null && resource == null) fail(
            "Select a staff member, location, or resource"
        );

        if (
            staff != null &&
            (!staff.isActive() ||
                service
                    .getStaff()
                    .stream()
                    .noneMatch(s -> s.getId().equals(staff.getId())))
        ) fail("Staff member is not active and eligible for this service");

        if (
            location != null &&
            (!location.isActive() ||
                service
                    .getLocations()
                    .stream()
                    .noneMatch(l -> l.getId().equals(location.getId())))
        ) fail("Location is not active and eligible for this service");

        if (!staffAllowedAtLocation(staff, location)) fail(
            "Staff member is not assigned to this location"
        );

        if (
            resource != null &&
            (!resource.isActive() ||
                resource.getType() == ResourceType.STAFF ||
                resource.getType() == ResourceType.ROOM ||
                service
                    .getResources()
                    .stream()
                    .noneMatch(r -> r.getId().equals(resource.getId())))
        ) fail("Resource is not active and eligible for this service");
    }

    public static boolean staffAllowedAtLocation(
        StaffMember staff,
        Location location
    ) {
        return (
            staff == null ||
            location == null ||
            staff.isFreeAgent() ||
            staff
                .getLocations()
                .stream()
                .anyMatch(assigned -> assigned.getId().equals(location.getId()))
        );
    }

    private static void fail(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
