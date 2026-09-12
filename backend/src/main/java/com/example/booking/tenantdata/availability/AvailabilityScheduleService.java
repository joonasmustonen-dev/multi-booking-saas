package com.example.booking.tenantdata.availability;

import com.example.booking.tenantdata.resource.BookableResource;
import com.example.booking.tenantdata.resource.BookableResourceRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AvailabilityScheduleService {

    private final BookableResourceRepository resourceRepository;
    private final AvailabilityRuleRepository ruleRepository;
    private final AvailabilityExceptionRepository exceptionRepository;

    public AvailabilityScheduleService(
            BookableResourceRepository resourceRepository,
            AvailabilityRuleRepository ruleRepository,
            AvailabilityExceptionRepository exceptionRepository) {

        this.resourceRepository = resourceRepository;
        this.ruleRepository = ruleRepository;
        this.exceptionRepository = exceptionRepository;
    }

    @Transactional("tenantTransactionManager")
    public AvailabilityRule createRule(
            UUID resourceId,
            AvailabilityRuleRequest request) {

        if (!request.endTime().isAfter(request.startTime())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "endTime must be after startTime"
            );
        }

        BookableResource resource = findResource(resourceId);

        return ruleRepository.save(
                new AvailabilityRule(
                        resource,
                        request.dayOfWeek(),
                        request.startTime(),
                        request.endTime()
                )
        );
    }

    @Transactional("tenantTransactionManager")
    public AvailabilityException createException(
            UUID resourceId,
            AvailabilityExceptionRequest request) {

        if (!request.endAt().isAfter(request.startAt())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "endAt must be after startAt"
            );
        }

        BookableResource resource = findResource(resourceId);

        return exceptionRepository.save(
                new AvailabilityException(
                        resource,
                        request.startAt(),
                        request.endAt(),
                        request.available()
                )
        );
    }

    private BookableResource findResource(UUID id) {

        return resourceRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Resource not found"
                        )
                );
    }
}