package com.example.booking.tenantdata.service;

import com.example.booking.tenantdata.resource.BookableResource;
import com.example.booking.tenantdata.resource.BookableResourceRepository;

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

    public ServiceOfferingService(
            ServiceOfferingRepository serviceRepository,
            BookableResourceRepository resourceRepository) {

        this.serviceRepository = serviceRepository;
        this.resourceRepository = resourceRepository;
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

        service.replaceResources(
                resolveResources(request.resourceIds())
        );

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

        service.replaceResources(
                resolveResources(request.resourceIds())
        );

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
            Set<UUID> resourceIds) {

        if (resourceIds == null || resourceIds.isEmpty()) {
            return new HashSet<>();
        }

        List<BookableResource> resources =
                resourceRepository.findAllById(resourceIds);

        if (resources.size() != resourceIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "One or more resources do not exist"
            );
        }

        return new HashSet<>(resources);
    }
}