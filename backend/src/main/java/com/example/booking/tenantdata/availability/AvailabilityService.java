package com.example.booking.tenantdata.availability;

import com.example.booking.tenantdata.appointment.Appointment;
import com.example.booking.tenantdata.appointment.AppointmentRepository;
import com.example.booking.tenantdata.appointment.AppointmentStatus;
import com.example.booking.tenantdata.location.Location;
import com.example.booking.tenantdata.resource.BookableResource;
import com.example.booking.tenantdata.resource.ResourceType;
import com.example.booking.tenantdata.service.ServiceOffering;
import com.example.booking.tenantdata.service.AssignmentRequirement;
import com.example.booking.tenantdata.service.AssignmentPolicy;
import com.example.booking.tenantdata.service.ServiceOfferingRepository;
import com.example.booking.tenantdata.settings.TenantSettingsService;
import com.example.booking.tenantdata.staff.StaffMember;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


@Service
public class AvailabilityService {

    private static final EnumSet<AppointmentStatus> BLOCKING_STATUSES =
        EnumSet.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);

    private final ServiceOfferingRepository serviceRepository;

    private final AvailabilityRuleRepository ruleRepository;

    private final AvailabilityExceptionRepository exceptionRepository;

    private final AppointmentRepository appointmentRepository;

    private final TenantSettingsService tenantSettingsService;

    public AvailabilityService(
        ServiceOfferingRepository serviceRepository,
        AvailabilityRuleRepository ruleRepository,
        AvailabilityExceptionRepository exceptionRepository,
        AppointmentRepository appointmentRepository,
        TenantSettingsService tenantSettingsService
    ) {
        this.serviceRepository = serviceRepository;

        this.ruleRepository = ruleRepository;

        this.exceptionRepository = exceptionRepository;

        this.appointmentRepository = appointmentRepository;

        this.tenantSettingsService = tenantSettingsService;
    }

    /*
     * =====================================================
     * APPOINTMENT AVAILABILITY
     * =====================================================
     *
     * Used by AppointmentService.
     *
     * All selected booking targets must simultaneously
     * be available:
     *
     *   staff
     *   AND location
     *   AND generic resource
     *
     * Null means that particular target is not required.
     */

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public boolean isAvailable(
        ServiceOffering service,
        StaffMember staff,
        Location location,
        BookableResource resource,
        OffsetDateTime startAt,
        UUID ignoredAppointmentId
    ) {
        if (!service.isActive()) {
            return false;
        }
        try {
            AssignmentPolicy.validateAssignments(
                service,
                staff,
                location,
                resource
            );
        } catch (ResponseStatusException invalidAssignment) {
            return false;
        }

        /*
         * AppointmentService currently requires at least
         * one assignment. Keep the availability service
         * defensive as well.
         */
        if (staff == null && location == null && resource == null) {
            return false;
        }

        if (staff != null && !staff.isActive()) {
            return false;
        }

        if (location != null && !location.isActive()) {
            return false;
        }

        if (resource != null && !resource.isActive()) {
            return false;
        }

        OffsetDateTime endAt = startAt.plusMinutes(
            service.getDurationMinutes()
        );

        /*
         * First check each target's own working hours
         * and availability exceptions.
         */

        if (staff != null && !isStaffAvailable(staff, startAt, endAt)) {
            return false;
        }

        if (
            location != null && !isLocationAvailable(location, startAt, endAt)
        ) {
            return false;
        }

        if (
            resource != null && !isResourceAvailable(resource, startAt, endAt)
        ) {
            return false;
        }

        /*
         * Then check whether any selected assignment
         * already belongs to an active appointment.
         *
         * During rescheduling the current appointment
         * is ignored.
         */

        boolean conflict = appointmentRepository.hasAssignmentConflict(
            staff != null ? staff.getId() : null,

            location != null ? location.getId() : null,

            resource != null ? resource.getId() : null,

            startAt,
            endAt,
            ignoredAppointmentId,
            BLOCKING_STATUSES
        );

        return !conflict;
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public boolean isAvailable(
        ServiceOffering service,
        StaffMember staff,
        Location location,
        BookableResource resource,
        OffsetDateTime startAt
    ) {
        return isAvailable(service, staff, location, resource, startAt, null);
    }

    /*
     * =====================================================
     * STAFF
     * =====================================================
     */

    private boolean isStaffAvailable(
        StaffMember staff,
        OffsetDateTime startAt,
        OffsetDateTime endAt
    ) {
        ZoneId zone = tenantSettingsService.getZoneId();

        ZonedDateTime localStart = startAt.atZoneSameInstant(zone);

        List<AvailabilityRule> rules =
            ruleRepository.findAllByStaff_IdAndDayOfWeekAndActiveTrue(
                staff.getId(),
                localStart.getDayOfWeek()
            );

        List<AvailabilityException> exceptions =
            exceptionRepository.findStaffExceptions(
                staff.getId(),
                startAt,
                endAt
            );

        return targetIntervalAvailable(startAt, endAt, rules, exceptions, zone);
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public boolean isStaffScheduledToday(
        UUID staffId,
        LocalDate date,
        ZoneId zone
    ) {
        var dayStart = date.atStartOfDay(zone);

        var dayEnd = date.plusDays(1).atStartOfDay(zone);

        var rules = ruleRepository.findAllByStaff_IdAndDayOfWeekAndActiveTrue(
            staffId,
            date.getDayOfWeek()
        );

        var exceptions = exceptionRepository.findStaffExceptions(
            staffId,
            dayStart.toOffsetDateTime(),
            dayEnd.toOffsetDateTime()
        );

        var blocked = exceptions
            .stream()
            .filter(exception -> !exception.isAvailable())
            .sorted(Comparator.comparing(AvailabilityException::getStartAt))
            .toList();

        for (TimeWindow window : buildAvailableWindows(
            date,
            rules,
            exceptions,
            zone
        )) {
            ZonedDateTime cursor = window.start();

            for (var exception : blocked) {
                var start = exception.getStartAt().atZoneSameInstant(zone);

                var end = exception.getEndAt().atZoneSameInstant(zone);

                if (
                    !end.isAfter(cursor) || !start.isBefore(window.end())
                ) continue;
                if (start.isAfter(cursor)) return true;
                if (end.isAfter(cursor)) cursor = end;

                if (!cursor.isBefore(window.end())) break;
            }
            if (cursor.isBefore(window.end())) return true;
        }
        return false;
    }

    /*
     * =====================================================
     * LOCATION
     * =====================================================
     */

    private boolean isLocationAvailable(
        Location location,
        OffsetDateTime startAt,
        OffsetDateTime endAt
    ) {
        ZoneId zone = tenantSettingsService.getZoneId();

        ZonedDateTime localStart = startAt.atZoneSameInstant(zone);

        List<AvailabilityRule> rules =
            ruleRepository.findAllByLocation_IdAndDayOfWeekAndActiveTrue(
                location.getId(),
                localStart.getDayOfWeek()
            );

        List<AvailabilityException> exceptions =
            exceptionRepository.findLocationExceptions(
                location.getId(),
                startAt,
                endAt
            );

        return targetIntervalAvailable(startAt, endAt, rules, exceptions, zone);
    }

    /*
     * =====================================================
     * GENERIC RESOURCE
     * =====================================================
     */

    private boolean isResourceAvailable(
        BookableResource resource,
        OffsetDateTime startAt,
        OffsetDateTime endAt
    ) {
        ZoneId zone = tenantSettingsService.getZoneId();

        ZonedDateTime localStart = startAt.atZoneSameInstant(zone);

        List<AvailabilityRule> rules =
            ruleRepository.findAllByResource_IdAndDayOfWeekAndActiveTrue(
                resource.getId(),
                localStart.getDayOfWeek()
            );

        List<AvailabilityException> exceptions =
            exceptionRepository.findResourceExceptions(
                resource.getId(),
                startAt,
                endAt
            );

        return targetIntervalAvailable(startAt, endAt, rules, exceptions, zone);
    }

    /*
     * =====================================================
     * COMMON SINGLE-TARGET CHECK
     * =====================================================
     */

    private boolean targetIntervalAvailable(
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        List<AvailabilityRule> rules,
        List<AvailabilityException> exceptions,
        ZoneId zone
    ) {
        ZonedDateTime start = startAt.atZoneSameInstant(zone);

        ZonedDateTime end = endAt.atZoneSameInstant(zone);

        /*
         * Recurring availability rules currently describe
         * one local calendar day.
         *
         * Don't allow a normal appointment to accidentally
         * cross midnight.
         */

        if (!start.toLocalDate().equals(end.toLocalDate())) {
            return false;
        }

        /*
         * A blocked exception always wins.
         */

        if (isBlocked(start, end, exceptions, zone)) {
            return false;
        }

        /*
         * Build the union of:
         *
         * recurring availability
         * +
         * available=true exceptions
         */

        List<TimeWindow> availableWindows = buildAvailableWindows(
            start.toLocalDate(),
            rules,
            exceptions,
            zone
        );

        /*
         * The WHOLE appointment must fit inside one
         * available window.
         *
         * An available exception that overlaps only
         * part of the appointment is not enough.
         */

        return availableWindows
            .stream()
            .anyMatch(
                window ->
                    !start.isBefore(window.start()) &&
                    !end.isAfter(window.end())
            );
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<AvailabilitySlotResponse> findAvailability(
        UUID serviceId,
        LocalDate from,
        LocalDate to,
        UUID staffId,
        UUID locationId,
        UUID resourceId
    ) {
        return findAvailability(
            serviceId,
            from,
            to,
            staffId,
            locationId,
            resourceId,
            null
        );
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<AvailabilitySlotResponse> findAvailability(
        UUID serviceId,
        LocalDate from,
        LocalDate to,
        UUID requestedStaffId,
        UUID requestedLocationId,
        UUID requestedResourceId,
        UUID ignoredAppointmentId
    ) {
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
        ServiceOffering service = serviceRepository
            .findByIdWithResources(serviceId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Service not found"
                )
            );

        if (!service.isActive()) return List.of();
        if (service.getDurationMinutes() <= 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Service duration must be positive"
            );
        }
        AssignmentPolicy.validateFilter(
            service.getStaffRequirement(),
            requestedStaffId,
            "Staff"
        );

        AssignmentPolicy.validateFilter(
            service.getLocationRequirement(),
            requestedLocationId,
            "Location"
        );

        AssignmentPolicy.validateFilter(
            service.getResourceRequirement(),
            requestedResourceId,
            "Resource"
        );

        List<StaffMember> staff = service
            .getStaff()
            .stream()
            .filter(StaffMember::isActive)
            .filter(
                t ->
                    service.getStaffRequirement() !=
                    AssignmentRequirement.FORBIDDEN
            )
            .filter(
                t ->
                    requestedStaffId == null ||
                    t.getId().equals(requestedStaffId)
            )
            .sorted(Comparator.comparing(StaffMember::getId))
            .toList();

        List<Location> locations = service
            .getLocations()
            .stream()
            .filter(Location::isActive)
            .filter(
                t ->
                    service.getLocationRequirement() !=
                    AssignmentRequirement.FORBIDDEN
            )
            .filter(
                t ->
                    requestedLocationId == null ||
                    t.getId().equals(requestedLocationId)
            )
            .sorted(Comparator.comparing(Location::getId))
            .toList();

        List<BookableResource> resources = service
            .getResources()
            .stream()
            .filter(BookableResource::isActive)
            .filter(
                t ->
                    service.getResourceRequirement() !=
                    AssignmentRequirement.FORBIDDEN
            )
            .filter(
                t ->
                    t.getType() != ResourceType.STAFF &&
                    t.getType() != ResourceType.ROOM
            )
            .filter(
                t ->
                    requestedResourceId == null ||
                    t.getId().equals(requestedResourceId)
            )
            .sorted(Comparator.comparing(BookableResource::getId))
            .toList();

        requireEligible(requestedStaffId, staff, "Staff member");

        requireEligible(requestedLocationId, locations, "Location");

        requireEligible(requestedResourceId, resources, "Resource");

        ZoneId zone = tenantSettingsService.getZoneId();

        OffsetDateTime rangeStart = from.atStartOfDay(zone).toOffsetDateTime();

        OffsetDateTime rangeEnd = to
            .plusDays(1)
            .atStartOfDay(zone)
            .toOffsetDateTime();

        List<TargetSchedule> staffSchedules = new ArrayList<>();

        for (StaffMember t : staff) {
            staffSchedules.add(
                new TargetSchedule(
                    t.getId(),
                    ruleRepository.findAllByStaff_IdAndActiveTrue(t.getId()),
                    exceptionRepository.findStaffExceptions(
                        t.getId(),
                        rangeStart,
                        rangeEnd
                    )
                )
            );
        }
        List<TargetSchedule> locationSchedules = new ArrayList<>();

        for (Location t : locations) {
            locationSchedules.add(
                new TargetSchedule(
                    t.getId(),
                    ruleRepository.findAllByLocation_IdAndActiveTrue(t.getId()),
                    exceptionRepository.findLocationExceptions(
                        t.getId(),
                        rangeStart,
                        rangeEnd
                    )
                )
            );
        }
        if (
            requestedStaffId == null &&
            service.getStaffRequirement() != AssignmentRequirement.REQUIRED
        ) staffSchedules.add(null);

        if (
            requestedLocationId == null &&
            service.getLocationRequirement() != AssignmentRequirement.REQUIRED
        ) locationSchedules.add(null);

        List<TargetSchedule> resourceSchedules = new ArrayList<>();

        for (BookableResource t : resources) {
            resourceSchedules.add(
                new TargetSchedule(
                    t.getId(),
                    ruleRepository.findByResourceIdAndActiveTrue(t.getId()),
                    exceptionRepository.findResourceExceptions(
                        t.getId(),
                        rangeStart,
                        rangeEnd
                    )
                )
            );
        }
        if (
            requestedResourceId == null &&
            service.getResourceRequirement() != AssignmentRequirement.REQUIRED
        ) resourceSchedules.add(null);

        if (
            staffSchedules.isEmpty() ||
            locationSchedules.isEmpty() ||
            resourceSchedules.isEmpty()
        ) return List.of();
        long combinations =
            (long) staffSchedules.size() *
            locationSchedules.size() *
            resourceSchedules.size();

        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;

        if (combinations > 2000 || combinations * days * 288 > 250000) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Too many availability combinations. Choose a staff member, location or resource, or a shorter date range."
            );
        }
        List<Appointment> bookings = appointmentRepository
            .findAssignmentBookingsInRange(
                rangeStart,
                rangeEnd,
                BLOCKING_STATUSES
            )
            .stream()
            .filter(
                a ->
                    ignoredAppointmentId == null ||
                    !ignoredAppointmentId.equals(a.getId())
            )
            .toList();

        Set<AvailabilitySlotResponse> slots = new LinkedHashSet<>();

        int evaluatedStarts = 0;

        var policy = tenantSettingsService.getSettings();

        OffsetDateTime now = OffsetDateTime.now();

        OffsetDateTime earliest = now.plusMinutes(
            policy.minimumNoticeMinutes()
        );

        LocalDate latestDate = LocalDate.now(zone).plusDays(
            policy.bookingHorizonDays()
        );

        for (TargetSchedule staffTarget : staffSchedules) {
            for (TargetSchedule locationTarget : locationSchedules) {
                for (TargetSchedule resourceTarget : resourceSchedules) {
                    List<TargetSchedule> targets = new ArrayList<>();

                    if (staffTarget != null) targets.add(staffTarget);

                    if (locationTarget != null) targets.add(locationTarget);

                    if (resourceTarget != null) targets.add(resourceTarget);

                    if (targets.isEmpty()) continue;
                    UUID staffId = targetId(staffTarget),
                        locationId = targetId(locationTarget),
                        resourceId = targetId(resourceTarget);

                    StaffMember selectedStaff =
                        staffId == null
                            ? null
                            : service
                                  .getStaff()
                                  .stream()
                                  .filter(member ->
                                      staffId.equals(member.getId())
                                  )
                                  .findFirst()
                                  .orElseThrow();

                    Location selectedLocation =
                        locationId == null
                            ? null
                            : service
                                  .getLocations()
                                  .stream()
                                  .filter(place ->
                                      locationId.equals(place.getId())
                                  )
                                  .findFirst()
                                  .orElseThrow();

                    if (
                        !AssignmentPolicy.staffAllowedAtLocation(
                            selectedStaff,
                            selectedLocation
                        )
                    ) continue;
                    List<Appointment> conflicts = bookings
                        .stream()
                        .filter(
                            a ->
                                (staffId != null &&
                                    a.getStaff() != null &&
                                    staffId.equals(a.getStaff().getId())) ||
                                (locationId != null &&
                                    a.getLocation() != null &&
                                    locationId.equals(
                                        a.getLocation().getId()
                                    )) ||
                                (resourceId != null &&
                                    a.getResource() != null &&
                                    resourceId.equals(a.getResource().getId()))
                        )
                        .toList();

                    List<AvailabilityException> exceptions = targets
                        .stream()
                        .flatMap(t -> t.exceptions().stream())
                        .toList();

                    for (
                        LocalDate date = from;
                        !date.isAfter(to);
                        date = date.plusDays(1)
                    ) {
                        List<TimeWindow> windows = null;

                        for (TargetSchedule t : targets) {
                            List<TimeWindow> own = buildAvailableWindows(
                                date,
                                t.rules(),
                                t.exceptions(),
                                zone
                            );

                            windows =
                                windows == null
                                    ? own
                                    : intersectWindows(windows, own);
                        }
                        for (TimeWindow window : windows) {
                            for (
                                ZonedDateTime start = window.start();
                                !start
                                    .plusMinutes(service.getDurationMinutes())
                                    .isAfter(window.end());
                                start = start.plusMinutes(
                                    policy.slotIntervalMinutes()
                                )
                            ) {
                                if (++evaluatedStarts > 250000) {
                                    throw new ResponseStatusException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "Availability request is too broad. Narrow the date range or assignment filters."
                                    );
                                }
                                ZonedDateTime end = start.plusMinutes(
                                    service.getDurationMinutes()
                                );

                                if (
                                    !start.toOffsetDateTime().isAfter(now) ||
                                    start
                                        .toOffsetDateTime()
                                        .isBefore(earliest) ||
                                    date.isAfter(latestDate) ||
                                    !start
                                        .toLocalDate()
                                        .equals(end.toLocalDate())
                                ) continue;
                                if (
                                    isIntervalAvailable(
                                        start,
                                        end,
                                        windows,
                                        exceptions,
                                        conflicts,
                                        zone
                                    )
                                ) {
                                    if (
                                        slots.size() >= 10000
                                    ) throw new ResponseStatusException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "Too many available slots. Narrow the date range or assignment filters."
                                    );
                                    slots.add(
                                        new AvailabilitySlotResponse(
                                            staffId,
                                            locationId,
                                            resourceId,
                                            start.toOffsetDateTime(),
                                            end.toOffsetDateTime()
                                        )
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
        return slots
            .stream()
            .sorted(
                Comparator.comparing(AvailabilitySlotResponse::start)
                    .thenComparing(
                        AvailabilitySlotResponse::staffId,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                    )
                    .thenComparing(
                        AvailabilitySlotResponse::locationId,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                    )
                    .thenComparing(
                        AvailabilitySlotResponse::resourceId,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                    )
            )
            .toList();
    }

    private void requireEligible(
        UUID requestedId,
        List<?> targets,
        String label
    ) {
        if (requestedId != null && targets.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                label + " is not eligible for this service"
            );
        }
    }

    private UUID targetId(TargetSchedule target) {
        return target == null ? null : target.id();
    }

    private List<TimeWindow> intersectWindows(
        List<TimeWindow> left,
        List<TimeWindow> right
    ) {
        List<TimeWindow> result = new ArrayList<>();

        for (TimeWindow a : left) {
            for (TimeWindow b : right) {
                ZonedDateTime start = a.start().isAfter(b.start())
                    ? a.start()
                    : b.start();

                ZonedDateTime end = a.end().isBefore(b.end())
                    ? a.end()
                    : b.end();

                if (end.isAfter(start)) result.add(new TimeWindow(start, end));
            }
        }
        return result;
    }

    private record TargetSchedule(
        UUID id,
        List<AvailabilityRule> rules,
        List<AvailabilityException> exceptions
    ) {}

    /*
     * =====================================================
     * AVAILABLE WINDOWS
     * =====================================================
     *
     * Builds availability for one tenant-local day from:
     *
     * recurring working hours
     * +
     * available=true exceptions
     */

    private List<TimeWindow> buildAvailableWindows(
        LocalDate date,
        List<AvailabilityRule> rules,
        List<AvailabilityException> exceptions,
        ZoneId zone
    ) {
        List<TimeWindow> windows = new ArrayList<>();

        /*
         * Recurring working hours.
         */

        rules
            .stream()
            .filter(rule -> rule.getDayOfWeek() == date.getDayOfWeek())
            .forEach(rule ->
                windows.add(
                    new TimeWindow(
                        date.atTime(rule.getStartTime()).atZone(zone),

                        date.atTime(rule.getEndTime()).atZone(zone)
                    )
                )
            );

        /*
         * available=true exception
         *
         * Example:
         *
         * normal Sunday = closed
         * exception      = Sunday 10:00-14:00 available
         */

        exceptions
            .stream()
            .filter(AvailabilityException::isAvailable)
            .forEach(exception -> {
                ZonedDateTime exceptionStart = exception
                    .getStartAt()
                    .atZoneSameInstant(zone);

                ZonedDateTime exceptionEnd = exception
                    .getEndAt()
                    .atZoneSameInstant(zone);

                ZonedDateTime dayStart = date.atStartOfDay(zone);

                ZonedDateTime dayEnd = date.plusDays(1).atStartOfDay(zone);

                ZonedDateTime clippedStart = exceptionStart.isAfter(dayStart)
                    ? exceptionStart
                    : dayStart;

                ZonedDateTime clippedEnd = exceptionEnd.isBefore(dayEnd)
                    ? exceptionEnd
                    : dayEnd;

                if (clippedEnd.isAfter(clippedStart)) {
                    windows.add(new TimeWindow(clippedStart, clippedEnd));
                }
            });

        return windows;
    }

    /*
     * =====================================================
     * SLOT CHECK
     * =====================================================
     *
     * Used by the current slot generator.
     */

    private boolean isIntervalAvailable(
        ZonedDateTime start,
        ZonedDateTime end,
        List<TimeWindow> availableWindows,
        List<AvailabilityException> exceptions,
        List<Appointment> appointments,
        ZoneId zone
    ) {
        boolean insideWorkingHours = availableWindows
            .stream()
            .anyMatch(
                window ->
                    !start.isBefore(window.start()) &&
                    !end.isAfter(window.end())
            );

        if (!insideWorkingHours) {
            return false;
        }

        if (isBlocked(start, end, exceptions, zone)) {
            return false;
        }

        if (isBooked(start, end, appointments)) {
            return false;
        }

        return true;
    }

    /*
     * =====================================================
     * BLOCKED EXCEPTIONS
     * =====================================================
     *
     * available=false exceptions remove availability.
     */

    private boolean isBlocked(
        ZonedDateTime start,
        ZonedDateTime end,
        List<AvailabilityException> exceptions,
        ZoneId zone
    ) {
        return exceptions
            .stream()
            .filter(exception -> !exception.isAvailable())
            .anyMatch(exception -> {
                ZonedDateTime blockedStart = exception
                    .getStartAt()
                    .atZoneSameInstant(zone);

                ZonedDateTime blockedEnd = exception
                    .getEndAt()
                    .atZoneSameInstant(zone);

                return start.isBefore(blockedEnd) && end.isAfter(blockedStart);
            });
    }

    /*
     * =====================================================
     * EXISTING BOOKING CHECK
     * =====================================================
     */

    private boolean isBooked(
        ZonedDateTime start,
        ZonedDateTime end,
        List<Appointment> appointments
    ) {
        OffsetDateTime slotStart = start.toOffsetDateTime();

        OffsetDateTime slotEnd = end.toOffsetDateTime();

        return appointments
            .stream()
            .anyMatch(
                appointment ->
                    slotStart.isBefore(appointment.getEndAt()) &&
                    slotEnd.isAfter(appointment.getStartAt())
            );
    }

    /*
     * =====================================================
     * INTERNAL WINDOW
     * =====================================================
     */

    private record TimeWindow(ZonedDateTime start, ZonedDateTime end) {}
}
