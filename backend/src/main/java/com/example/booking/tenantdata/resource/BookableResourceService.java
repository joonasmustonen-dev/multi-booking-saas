package com.example.booking.tenantdata.resource;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class BookableResourceService {

    private final BookableResourceRepository repository;

    public BookableResourceService(BookableResourceRepository repository) {
        this.repository = repository;
    }

    @Transactional("tenantTransactionManager")
    public BookableResource create(ResourceRequest request) {
        validateType(request.type());

        var resource = new BookableResource(
            request.name().trim(),
            request.type()
        );

        resource.update(resource.getName(), request.type(), request.active());

        if (request.description() != null) resource.setDescription(
            request.description().trim()
        );

        return repository.save(resource);
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<BookableResource> findAll() {
        return repository.findAll().stream().filter(this::isGeneric).toList();
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public BookableResource findById(UUID id) {
        return repository
            .findById(id)
            .filter(this::isGeneric)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Resource not found"
                )
            );
    }

    @Transactional("tenantTransactionManager")
    public BookableResource update(UUID id, ResourceRequest request) {
        validateType(request.type());

        BookableResource resource = findById(id);

        resource.update(request.name(), request.type(), request.active());

        if (request.description() != null) resource.setDescription(
            request.description().trim()
        );

        return resource;
    }

    @Transactional("tenantTransactionManager")
    public void delete(UUID id) {
        BookableResource resource = findById(id);

        repository.delete(resource);
    }

    @Transactional("tenantTransactionManager")
    public BookableResource setActive(UUID id, boolean active) {
        var resource = findById(id);

        resource.update(resource.getName(), resource.getType(), active);

        return resource;
    }

    private boolean isGeneric(BookableResource resource) {
        return (
            resource.getType() != ResourceType.STAFF &&
            resource.getType() != ResourceType.ROOM
        );
    }

    private void validateType(ResourceType type) {
        if (
            type == ResourceType.STAFF || type == ResourceType.ROOM
        ) throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Use native staff and location management"
        );
    }
}
