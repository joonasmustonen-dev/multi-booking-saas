package com.example.booking.tenantdata.location;
import com.example.booking.tenantdata.management.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service
@Transactional("tenantTransactionManager")
public class LocationService {
    private final LocationRepository repository;
    public LocationService(LocationRepository repository) { this.repository = repository; }
    public LocationResponse create(CreateAssignmentRequest request) {
        return LocationResponse.from(repository.save(new Location(request.name())));
    }
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<LocationResponse> findAll() {
        return repository.findAll().stream().sorted(Comparator.comparing(Location::getName).thenComparing(Location::getId))
                .map(LocationResponse::from).toList();
    }
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public LocationResponse findById(UUID id) { return LocationResponse.from(entity(id)); }
    public LocationResponse update(UUID id, UpdateAssignmentRequest request) {
        var entity = entity(id); entity.update(request.name(), request.active()); return LocationResponse.from(entity);
    }
    public LocationResponse setActive(UUID id, boolean active) {
        var entity = entity(id); entity.update(entity.getName(), active); return LocationResponse.from(entity);
    }
    public LocationResponse create(LocationRequest request) {
        var entity = new Location(request.name().trim()); applyDetails(entity, request);
        return LocationResponse.from(repository.save(entity));
    }
    public LocationResponse update(UUID id, LocationRequest request) {
        var entity = entity(id); entity.update(request.name().trim(), request.active() == null ? entity.isActive() : request.active());
        applyDetails(entity, request); return LocationResponse.from(entity);
    }
    private void applyDetails(Location entity, LocationRequest request) {
        if (request.active() != null) entity.update(entity.getName(), request.active());
        if (request.description() != null) entity.setDescription(request.description().trim());
        if (request.addressLine() != null) entity.setAddressLine(request.addressLine().trim());
        if (request.city() != null) entity.setCity(request.city().trim());
        if (request.postalCode() != null) entity.setPostalCode(request.postalCode().trim());
        if (request.countryCode() != null) entity.setCountryCode(request.countryCode().trim());
        if (request.phone() != null) entity.setPhone(request.phone().trim());
    }
    private Location entity(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found"));
    }
}
