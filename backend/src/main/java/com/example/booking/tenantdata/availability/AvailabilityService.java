package com.example.booking.tenantdata.availability;

import com.example.booking.tenantdata.appointment.Appointment;
import com.example.booking.tenantdata.appointment.AppointmentRepository;
import com.example.booking.tenantdata.appointment.AppointmentStatus;
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

    /*
     * Temporary choice.
     *
     * Later we'll give each tenant/location its own timezone.
     * For now the whole availability engine consistently uses UTC.
     */
    private static final ZoneId AVAILABILITY_ZONE = ZoneOffset.UTC;

    private static final EnumSet<AppointmentStatus> BLOCKING_STATUSES =
            EnumSet.of(
                    AppointmentStatus.PENDING,
                    AppointmentStatus.CONFIRMED
            );

    private final ServiceOfferingRepository serviceRepository;
    private final AvailabilityRuleRepository ruleRepository;
    private final AvailabilityExceptionRepository exceptionRepository;
    private final AppointmentRepository appointmentRepository;

    public AvailabilityService(
            ServiceOfferingRepository serviceRepository,
            AvailabilityRuleRepository ruleRepository,
            AvailabilityExceptionRepository exceptionRepository,
            AppointmentRepository appointmentRepository) {

        this.serviceRepository = serviceRepository;
        this.ruleRepository = ruleRepository;
        this.exceptionRepository = exceptionRepository;
        this.appointmentRepository = appointmentRepository;
    }

    /*
     * Used directly by AppointmentService.
     *
     * This answers:
     *
     * "Can this resource perform this service starting exactly at startAt?"
     */
    @Transactional(
            value = "tenantTransactionManager",
            readOnly = true
    )
    public boolean isAvailable(
            ServiceOffering service,
            BookableResource resource,
            OffsetDateTime startAt,
            UUID ignoreAppointmentId) {


        if (!service.isActive() || !resource.isActive()) {
            return false;
        }

        OffsetDateTime endAt =
                startAt.plusMinutes(
                        service.getDurationMinutes()
                );

        ZonedDateTime start =
                startAt.atZoneSameInstant(
                        AVAILABILITY_ZONE
                );

        ZonedDateTime end =
                endAt.atZoneSameInstant(
                        AVAILABILITY_ZONE
                );

        /*
         * For now appointments must fit inside one day's
         * availability window.
         */
        LocalDate date = start.toLocalDate();

        List<AvailabilityRule> rules =
                ruleRepository
                        .findByResourceIdAndActiveTrue(
                                resource.getId()
                        );

        List<AvailabilityException> exceptions =
                exceptionRepository
                        .findByResourceIdAndEndAtAfterAndStartAtBefore(
                                resource.getId(),
                                start.toOffsetDateTime(),
                                end.toOffsetDateTime()
                        );

        List<Appointment> appointments =
                appointmentRepository.findOverlapping(
                        resource.getId(),
                        startAt,
                        endAt,
                        BLOCKING_STATUSES
                );

        List<TimeWindow> availableWindows =
                buildAvailableWindows(
                        date,
                        rules,
                        exceptions
                );

        return isIntervalAvailable(
                start,
                end,
                availableWindows,
                exceptions,
                appointments
        );

    }

    public boolean isAvailable(
        ServiceOffering service,
        BookableResource resource,
        OffsetDateTime startAt) {

    return isAvailable(
            service,
            resource,
            startAt,
            null
    );
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

        if (!service.isActive()) {
            return List.of();
        }

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

        List<AvailabilityRule> rules =
                ruleRepository
                        .findByResourceIdAndActiveTrue(
                                resource.getId()
                        );

        OffsetDateTime rangeStart =
                from.atStartOfDay(
                                AVAILABILITY_ZONE
                        )
                        .toOffsetDateTime();

        OffsetDateTime rangeEnd =
                to.plusDays(1)
                        .atStartOfDay(
                                AVAILABILITY_ZONE
                        )
                        .toOffsetDateTime();

        List<AvailabilityException> exceptions =
                exceptionRepository
                        .findByResourceIdAndEndAtAfterAndStartAtBefore(
                                resource.getId(),
                                rangeStart,
                                rangeEnd
                        );

        List<Appointment> appointments =
                appointmentRepository.findOverlapping(
                        resource.getId(),
                        rangeStart,
                        rangeEnd,
                        BLOCKING_STATUSES
                );

        Set<AvailabilitySlotResponse> result =
                new LinkedHashSet<>();

        LocalDate date = from;

        while (!date.isAfter(to)) {

            List<TimeWindow> availableWindows =
                    buildAvailableWindows(
                            date,
                            rules,
                            exceptions
                    );

            for (TimeWindow window : availableWindows) {

                ZonedDateTime candidate =
                        window.start();

                while (!candidate
                        .plusMinutes(durationMinutes)
                        .isAfter(window.end())) {

                    ZonedDateTime slotEnd =
                            candidate.plusMinutes(
                                    durationMinutes
                            );

                    if (isIntervalAvailable(
                            candidate,
                            slotEnd,
                            availableWindows,
                            exceptions,
                            appointments)) {

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

    /*
     * Builds the available windows for one particular day:
     *
     * recurring working hours
     * +
     * available=true exceptions
     */
    private List<TimeWindow> buildAvailableWindows(
            LocalDate date,
            List<AvailabilityRule> rules,
            List<AvailabilityException> exceptions) {

        List<TimeWindow> windows =
                new ArrayList<>();

        /*
         * Normal recurring working hours.
         */
        rules.stream()
                .filter(rule ->
                        rule.getDayOfWeek()
                                == date.getDayOfWeek()
                )
                .forEach(rule ->
                        windows.add(
                                new TimeWindow(
                                        date
                                                .atTime(
                                                        rule.getStartTime()
                                                )
                                                .atZone(
                                                        AVAILABILITY_ZONE
                                                ),

                                        date
                                                .atTime(
                                                        rule.getEndTime()
                                                )
                                                .atZone(
                                                        AVAILABILITY_ZONE
                                                )
                                )
                        )
                );

        /*
         * Extra availability.
         *
         * Example:
         * normal Sunday = closed
         * exception = Sunday 10:00-14:00 available
         */
        exceptions.stream()
                .filter(AvailabilityException::isAvailable)
                .forEach(exception -> {

                    ZonedDateTime exceptionStart =
                            exception.getStartAt()
                                    .atZoneSameInstant(
                                            AVAILABILITY_ZONE
                                    );

                    ZonedDateTime exceptionEnd =
                            exception.getEndAt()
                                    .atZoneSameInstant(
                                            AVAILABILITY_ZONE
                                    );

                    ZonedDateTime dayStart =
                            date.atStartOfDay(
                                    AVAILABILITY_ZONE
                            );

                    ZonedDateTime dayEnd =
                            date.plusDays(1)
                                    .atStartOfDay(
                                            AVAILABILITY_ZONE
                                    );

                    ZonedDateTime clippedStart =
                            exceptionStart.isAfter(dayStart)
                                    ? exceptionStart
                                    : dayStart;

                    ZonedDateTime clippedEnd =
                            exceptionEnd.isBefore(dayEnd)
                                    ? exceptionEnd
                                    : dayEnd;

                    if (clippedEnd.isAfter(clippedStart)) {

                        windows.add(
                                new TimeWindow(
                                        clippedStart,
                                        clippedEnd
                                )
                        );
                    }
                });

        return windows;
    }

    /*
     * THIS is the central availability rule.
     *
     * Both:
     *
     * GET /availability
     *
     * and
     *
     * POST /appointments
     *
     * ultimately use this logic.
     */
    private boolean isIntervalAvailable(
            ZonedDateTime start,
            ZonedDateTime end,
            List<TimeWindow> availableWindows,
            List<AvailabilityException> exceptions,
            List<Appointment> appointments) {

        boolean insideWorkingHours =
                availableWindows
                        .stream()
                        .anyMatch(window ->
                                !start.isBefore(window.start())
                                        &&
                                !end.isAfter(window.end())
                        );

        if (!insideWorkingHours) {
            return false;
        }

        if (isBlocked(
                start,
                end,
                exceptions)) {

            return false;
        }

        if (isBooked(
                start,
                end,
                appointments)) {

            return false;
        }

        return true;
    }

    /*
     * available=false exceptions remove availability.
     */
    private boolean isBlocked(
            ZonedDateTime start,
            ZonedDateTime end,
            List<AvailabilityException> exceptions) {

        return exceptions.stream()
                .filter(exception ->
                        !exception.isAvailable()
                )
                .anyMatch(exception -> {

                    ZonedDateTime blockedStart =
                            exception.getStartAt()
                                    .atZoneSameInstant(
                                            AVAILABILITY_ZONE
                                    );

                    ZonedDateTime blockedEnd =
                            exception.getEndAt()
                                    .atZoneSameInstant(
                                            AVAILABILITY_ZONE
                                    );

                    return start.isBefore(blockedEnd)
                            && end.isAfter(blockedStart);
                });
    }

    private boolean isBooked(
            ZonedDateTime start,
            ZonedDateTime end,
            List<Appointment> appointments) {

        OffsetDateTime slotStart =
                start.toOffsetDateTime();

        OffsetDateTime slotEnd =
                end.toOffsetDateTime();

        return appointments
                .stream()
                .anyMatch(appointment ->

                        slotStart.isBefore(
                                appointment.getEndAt()
                        )

                                &&

                        slotEnd.isAfter(
                                appointment.getStartAt()
                        )
                );
    }

    private record TimeWindow(
            ZonedDateTime start,
            ZonedDateTime end) {
    }
}