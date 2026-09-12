package com.example.booking.tenantdata.availability;

import com.example.booking.tenantdata.resource.BookableResource;
import com.example.booking.tenantdata.service.ServiceOffering;
import com.example.booking.tenantdata.service.ServiceOfferingRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

@Service
public class AvailabilityService {

    private static final int SLOT_INTERVAL_MINUTES = 15;

    private final ServiceOfferingRepository serviceRepository;
    private final AvailabilityRuleRepository ruleRepository;
    private final AvailabilityExceptionRepository exceptionRepository;

    public AvailabilityService(
            ServiceOfferingRepository serviceRepository,
            AvailabilityRuleRepository ruleRepository,
            AvailabilityExceptionRepository exceptionRepository) {

        this.serviceRepository = serviceRepository;
        this.ruleRepository = ruleRepository;
        this.exceptionRepository = exceptionRepository;
    }

    @Transactional(
            value = "tenantTransactionManager",
            readOnly = true
    )
    public List<AvailabilitySlotResponse> findAvailability(
            UUID serviceId,
            LocalDate from,
            LocalDate to,
            UUID requestedResourceId) {

        if (to.isBefore(from)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "to must not be before from"
            );
        }

        if (from.plusDays(31).isBefore(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Availability range cannot exceed 31 days"
            );
        }

        ServiceOffering service =
                serviceRepository
                        .findByIdWithResources(serviceId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Service not found"
                                )
                        );

        List<BookableResource> resources =
                service.getResources()
                        .stream()
                        .filter(BookableResource::isActive)
                        .filter(resource ->
                                requestedResourceId == null
                                || resource.getId()
                                        .equals(requestedResourceId)
                        )
                        .toList();

        if (requestedResourceId != null
                && resources.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Resource is not eligible for this service"
            );
        }

        List<AvailabilitySlotResponse> slots =
                new ArrayList<>();

        for (BookableResource resource : resources) {

            slots.addAll(
                    calculateForResource(
                            resource,
                            service.getDurationMinutes(),
                            from,
                            to
                    )
            );
        }

        slots.sort(
                Comparator.comparing(
                        AvailabilitySlotResponse::start
                )
        );

        return slots;
    }

    private List<AvailabilitySlotResponse> calculateForResource(
            BookableResource resource,
            int durationMinutes,
            LocalDate from,
            LocalDate to) {

        ZoneId zone = ZoneOffset.UTC;

        List<AvailabilityRule> rules =
                ruleRepository
                        .findByResourceIdAndActiveTrue(
                                resource.getId()
                        );

        OffsetDateTime rangeStart =
                from.atStartOfDay(zone)
                        .toOffsetDateTime();

        OffsetDateTime rangeEnd =
                to.plusDays(1)
                        .atStartOfDay(zone)
                        .toOffsetDateTime();

        List<AvailabilityException> exceptions =
                exceptionRepository
                        .findByResourceIdAndEndAtAfterAndStartAtBefore(
                                resource.getId(),
                                rangeStart,
                                rangeEnd
                        );

        Set<AvailabilitySlotResponse> result =
                new LinkedHashSet<>();

        LocalDate date = from;

        while (!date.isAfter(to)) {

            final LocalDate currentDate = date;

            List<TimeWindow> availableWindows =
                    new ArrayList<>();

            rules.stream()
                    .filter(rule ->
                            rule.getDayOfWeek()
                                    == currentDate.getDayOfWeek()
                    )
                    .forEach(rule ->
                            availableWindows.add(
                                    new TimeWindow(
                                            currentDate
                                                    .atTime(rule.getStartTime())
                                                    .atZone(zone),
                                            currentDate
                                                    .atTime(rule.getEndTime())
                                                    .atZone(zone)
                                    )
                            )
                    );

            /*
             * available=true exceptions can create extra
             * availability outside normal working hours.
             */
            exceptions.stream()
                    .filter(AvailabilityException::isAvailable)
                    .forEach(exception -> {

                        ZonedDateTime start =
                                exception.getStartAt()
                                        .atZoneSameInstant(zone);

                        ZonedDateTime end =
                                exception.getEndAt()
                                        .atZoneSameInstant(zone);

                        ZonedDateTime dayStart =
                                currentDate.atStartOfDay(zone);

                        ZonedDateTime dayEnd =
                                currentDate.plusDays(1)
                                        .atStartOfDay(zone);

                        ZonedDateTime clippedStart =
                                start.isAfter(dayStart)
                                        ? start
                                        : dayStart;

                        ZonedDateTime clippedEnd =
                                end.isBefore(dayEnd)
                                        ? end
                                        : dayEnd;

                        if (clippedEnd.isAfter(clippedStart)) {
                            availableWindows.add(
                                    new TimeWindow(
                                            clippedStart,
                                            clippedEnd
                                    )
                            );
                        }
                    });

            for (TimeWindow window : availableWindows) {

                ZonedDateTime candidate = window.start();

                while (!candidate
                        .plusMinutes(durationMinutes)
                        .isAfter(window.end())) {

                    ZonedDateTime slotEnd =
                            candidate.plusMinutes(
                                    durationMinutes
                            );

                    if (!isBlocked(
                            candidate,
                            slotEnd,
                            exceptions,
                            zone)) {

                        result.add(
                                new AvailabilitySlotResponse(
                                        resource.getId(),
                                        candidate.toOffsetDateTime(),
                                        slotEnd.toOffsetDateTime()
                                )
                        );
                    }

                    candidate =
                            candidate.plusMinutes(
                                    SLOT_INTERVAL_MINUTES
                            );
                }
            }

            date = date.plusDays(1);
        }

        return new ArrayList<>(result);
    }

    private boolean isBlocked(
            ZonedDateTime start,
            ZonedDateTime end,
            List<AvailabilityException> exceptions,
            ZoneId zone) {

        return exceptions.stream()
                .filter(exception ->
                        !exception.isAvailable()
                )
                .anyMatch(exception -> {

                    ZonedDateTime blockedStart =
                            exception.getStartAt()
                                    .atZoneSameInstant(zone);

                    ZonedDateTime blockedEnd =
                            exception.getEndAt()
                                    .atZoneSameInstant(zone);

                    return start.isBefore(blockedEnd)
                            && end.isAfter(blockedStart);
                });
    }

    private record TimeWindow(
            ZonedDateTime start,
            ZonedDateTime end) {
    }
}