package com.example.booking.tenantdata.service;

import com.example.booking.tenantdata.location.LocationRepository;
import com.example.booking.tenantdata.resource.BookableResource;
import com.example.booking.tenantdata.resource.BookableResourceRepository;
import com.example.booking.tenantdata.staff.StaffMemberRepository;
import com.example.booking.tenantdata.location.Location;
import com.example.booking.tenantdata.location.LocationRepository;

import com.example.booking.tenantdata.staff.StaffMember;
import com.example.booking.tenantdata.staff.StaffMemberRepository;

import com.example.booking.tenantdata.resource.ResourceType;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ServiceOfferingService {

    private final ServiceOfferingRepository serviceRepository;
    private final BookableResourceRepository resourceRepository;
    private final StaffMemberRepository staffRepository;
    private final LocationRepository locationRepository;

    public ServiceOfferingService(
            ServiceOfferingRepository serviceRepository,
            BookableResourceRepository resourceRepository,
            StaffMemberRepository staffRepository,
            LocationRepository locationRepository) {

        this.serviceRepository = serviceRepository;
        this.resourceRepository = resourceRepository;
        this.staffRepository = staffRepository;
        this.locationRepository = locationRepository;
    }

    @Transactional("tenantTransactionManager")
    public ServiceResponse create(CreateServiceRequest request) {

        ServiceOffering service =
                new ServiceOffering(
                        request.name(),
                        request.description(),
                        request.durationMinutes(),
                        request.price(),
                        request.currency()
                );

        service.replaceStaff(
                resolveStaff(
                        request.staffIds()
                )
        );

        service.replaceLocations(
                resolveLocations(
                        request.locationIds()
                )
        );

        service.replaceResources(
                resolveResources(
                        request.resourceIds()
                )
        );

        service.configureRequirements(request.staffRequirement(), request.locationRequirement(), request.resourceRequirement());
        AssignmentPolicy.validateDefinition(service);

        return ServiceResponse.from(
                serviceRepository.save(service)
        );
    }

    @Transactional(
            value = "tenantTransactionManager",
            readOnly = true
    )
    public List<ServiceResponse> findAll() {

        return serviceRepository
                .findAllWithResources()
                .stream()
                .map(ServiceResponse::from)
                .toList();
    }

    @Transactional(
            value = "tenantTransactionManager",
            readOnly = true
    )
    public ServiceResponse findById(UUID id) {

        return ServiceResponse.from(
                getService(id)
        );
    }

    @Transactional("tenantTransactionManager")
    public ServiceResponse update(
            UUID id,
            UpdateServiceRequest request) {

        ServiceOffering service = getService(id);

        service.update(
                request.name(),
                request.description(),
                request.durationMinutes(),
                request.price(),
                request.currency(),
                request.active()
        );

        service.replaceStaff(
                resolveStaff(
                        request.staffIds()
                )
        );

        service.replaceLocations(
                resolveLocations(
                        request.locationIds()
                )
        );

        service.replaceResources(
                resolveResources(
                        request.resourceIds()
                )
        );

        service.configureRequirements(request.staffRequirement(), request.locationRequirement(), request.resourceRequirement());
        AssignmentPolicy.validateDefinition(service);
        return ServiceResponse.from(service);
    }

    @Transactional("tenantTransactionManager")
    public void delete(UUID id) {

        ServiceOffering service = getService(id);

        serviceRepository.delete(service);
    }

    private ServiceOffering getService(UUID id) {

        return serviceRepository
                .findByIdWithResources(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Service not found"
                        )
                );
    }

        private Set<BookableResource> resolveResources(
                Set<UUID> ids) {

        if (
                ids == null
                || ids.isEmpty()
        ) {
                return Set.of();
        }

        Set<BookableResource> result =
                new HashSet<>(
                        resourceRepository
                                .findAllById(ids)
                );

        if (
                result.size()
                != ids.size()
        ) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "One or more resources do not exist"
                );
        }

        for (
                BookableResource resource :
                result
        ) {

                if (
                        resource.getType()
                                == ResourceType.STAFF
                        ||
                        resource.getType()
                                == ResourceType.ROOM
                ) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Staff and locations must use their dedicated fields"
                );
                }

                if (!resource.isActive()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Inactive resources cannot be assigned to a service"
                );
                }
        }

        return result;
        }

        private Set<StaffMember> resolveStaff(
                Set<UUID> ids) {

        if (
                ids == null
                || ids.isEmpty()
        ) {
                return Set.of();
        }

        Set<StaffMember> result =
                new HashSet<>(
                        staffRepository
                                .findAllById(ids)
                );

        if (
                result.size()
                != ids.size()
        ) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "One or more staff members do not exist"
                );
        }

        for (
                StaffMember staff :
                result
        ) {
                if (!staff.isActive()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Inactive staff cannot be assigned to a service"
                );
                }
        }

        return result;
        }

                private Set<Location> resolveLocations(
                Set<UUID> ids) {

        if (
                ids == null
                || ids.isEmpty()
        ) {
                return Set.of();
        }

        Set<Location> result =
                new HashSet<>(
                        locationRepository
                                .findAllById(ids)
                );

        if (
                result.size()
                != ids.size()
        ) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "One or more locations do not exist"
                );
        }

        for (
                Location location :
                result
        ) {
                if (!location.isActive()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Inactive locations cannot be assigned to a service"
                );
                }
        }

        return result;
        }
}