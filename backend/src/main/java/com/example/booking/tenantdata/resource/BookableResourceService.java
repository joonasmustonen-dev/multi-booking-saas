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

    public BookableResourceService(
            BookableResourceRepository repository) {

        this.repository = repository;
    }

    @Transactional("tenantTransactionManager")
    public BookableResource create(
            ResourceRequest request) {

        return repository.save(
                new BookableResource(
                        request.name(),
                        request.type()
                )
        );
    }

    @Transactional(
            value = "tenantTransactionManager",
            readOnly = true
    )
    public List<BookableResource> findAll() {
        return repository.findAll();
    }

    @Transactional(
            value = "tenantTransactionManager",
            readOnly = true
    )
    public BookableResource findById(UUID id) {

        return repository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Resource not found"
                        )
                );
    }

    @Transactional("tenantTransactionManager")
    public BookableResource update(
            UUID id,
            ResourceRequest request) {

        BookableResource resource = findById(id);

        resource.update(
                request.name(),
                request.type(),
                request.active()
        );

        return resource;
    }

    @Transactional("tenantTransactionManager")
    public void delete(UUID id) {

        BookableResource resource = findById(id);

        repository.delete(resource);
    }
}