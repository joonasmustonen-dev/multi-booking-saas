package com.example.booking.tenantdata.appointment;

import com.example.booking.tenantdata.Customer;
import com.example.booking.tenantdata.settings.TenantSettingsService;
import com.example.booking.tenantdata.staff.StaffMember;
import com.example.booking.tenantdata.staff.StaffMemberRepository;
import com.example.booking.tenantdata.CustomerRepository;
import com.example.booking.tenantdata.resource.BookableResource;
import com.example.booking.tenantdata.resource.ResourceType;
import com.example.booking.tenantdata.resource.BookableResourceRepository;
import com.example.booking.tenantdata.service.ServiceOffering;
import com.example.booking.tenantdata.service.ServiceOfferingRepository;
import com.example.booking.tenantdata.availability.AvailabilityService;
import com.example.booking.tenantdata.location.LocationRepository;
import com.example.booking.tenantdata.location.Location;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;


@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;

    private final CustomerRepository customerRepository;

    private final ServiceOfferingRepository serviceRepository;

    private final BookableResourceRepository resourceRepository;

    private final TenantSettingsService tenantSettingsService;

    private final AvailabilityService availabilityService;

    private final StaffMemberRepository staffRepository;

    private final LocationRepository locationRepository;

    public AppointmentService(
        AppointmentRepository appointmentRepository,
        CustomerRepository customerRepository,
        ServiceOfferingRepository serviceRepository,
        BookableResourceRepository resourceRepository,
        AvailabilityService availabilityService,
        TenantSettingsService tenantSettingsService,
        StaffMemberRepository staffRepository,
        LocationRepository locationRepository
    ) {
        this.appointmentRepository = appointmentRepository;

        this.customerRepository = customerRepository;

        this.serviceRepository = serviceRepository;

        this.resourceRepository = resourceRepository;

        this.availabilityService = availabilityService;

        this.tenantSettingsService = tenantSettingsService;

        this.staffRepository = staffRepository;

        this.locationRepository = locationRepository;
    }

    @Transactional("tenantTransactionManager")
    public AppointmentResponse create(CreateAppointmentRequest request) {
        Customer customer = customerRepository
            .findForUpdate(request.customerId())
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found"
                )
            );

        requireBookableCustomer(customer);

        customer.touch();

        ServiceOffering service = serviceRepository
            .findByIdWithResources(request.serviceId())
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Service not found"
                )
            );

        com.example.booking.tenantdata.service.AssignmentPolicy.validatePresence(
            service.getStaffRequirement(),
            request.staffId(),
            "Staff"
        );

        com.example.booking.tenantdata.service.AssignmentPolicy.validatePresence(
            service.getLocationRequirement(),
            request.locationId(),
            "Location"
        );

        com.example.booking.tenantdata.service.AssignmentPolicy.validatePresence(
            service.getResourceRequirement(),
            request.resourceId(),
            "Resource"
        );

        BookableResource resource = resolveResource(request.resourceId());

        OffsetDateTime startAt = request.startAt();

        OffsetDateTime endAt = startAt.plusMinutes(
            service.getDurationMinutes()
        );

        tenantSettingsService.validateBookingStart(startAt);

        if (!startAt.isAfter(OffsetDateTime.now())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Appointment must be in the future"
            );
        }

        StaffMember staff = resolveStaff(request.staffId());

        Location location = resolveLocation(request.locationId());

        validateAssignments(service, staff, location, resource);

        boolean available = availabilityService.isAvailable(
            service,
            staff,
            location,
            resource,
            startAt
        );

        if (!available) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The selected time is not available"
            );
        }

        Appointment appointment = new Appointment(
            customer,
            service,
            staff,
            location,
            resource,
            startAt,
            endAt,
            request.notes(),
            AppointmentStatus.valueOf(
                tenantSettingsService.getSettings().defaultAppointmentStatus()
            )
        );

        try {
            /*
             * Flush is intentional.
             *
             * It forces PostgreSQL's exclusion constraint
             * to execute here, so we can convert the race
             * into HTTP 409.
             */
            appointment = appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The selected time is no longer available",
                e
            );
        }

        return AppointmentResponse.from(appointment);
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<AppointmentResponse> findAll() {
        return findPage(0, 100);
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<AppointmentResponse> findPage(int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 200) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid appointment page or size (1–200)"
            );
        }
        return appointmentRepository
            .findAllBy(
                org.springframework.data.domain.PageRequest.of(
                    page,
                    size,
                    org.springframework.data.domain.Sort.by("startAt", "id")
                )
            )
            .stream()
            .map(AppointmentResponse::from)
            .toList();
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public AppointmentResponse findById(UUID id) {
        return AppointmentResponse.from(getAppointment(id));
    }

    @Transactional("tenantTransactionManager")
    public AppointmentResponse updateStatus(
        UUID id,
        AppointmentStatus targetStatus
    ) {
        Appointment appointment = getAppointment(id);

        validateStatusTransition(appointment, targetStatus);

        appointment.changeStatus(targetStatus);

        return flushMutation(appointment);
    }

    private Appointment getAppointment(UUID id) {
        return appointmentRepository
            .findByIdWithDetails(id)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Appointment not found"
                )
            );
    }

    @Transactional("tenantTransactionManager")
    public AppointmentResponse cancel(UUID id) {
        Appointment appointment = getAppointment(id);

        validateStatusTransition(appointment, AppointmentStatus.CANCELLED);

        appointment.changeStatus(AppointmentStatus.CANCELLED);

        return flushMutation(appointment);
    }

    @Transactional("tenantTransactionManager")
    public AppointmentResponse reschedule(
        UUID appointmentId,
        RescheduleAppointmentRequest request
    ) {
        Appointment appointment = getAppointment(appointmentId);

        Customer customer = customerRepository
            .findForUpdate(appointment.getCustomer().getId())
            .orElseThrow();

        requireBookableCustomer(customer);

        customer.touch();

        if (
            appointment.getStatus() != AppointmentStatus.PENDING &&
            appointment.getStatus() != AppointmentStatus.CONFIRMED
        ) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Only pending or confirmed appointments can be rescheduled"
            );
        }

        ServiceOffering service = appointment.getService();

        com.example.booking.tenantdata.service.AssignmentPolicy.validatePresence(
            service.getStaffRequirement(),
            request.staffId(),
            "Staff"
        );

        com.example.booking.tenantdata.service.AssignmentPolicy.validatePresence(
            service.getLocationRequirement(),
            request.locationId(),
            "Location"
        );

        com.example.booking.tenantdata.service.AssignmentPolicy.validatePresence(
            service.getResourceRequirement(),
            request.resourceId(),
            "Resource"
        );

        BookableResource resource = resolveResource(request.resourceId());

        StaffMember staff = resolveStaff(request.staffId());

        Location location = resolveLocation(request.locationId());

        validateAssignments(
            appointment.getService(),
            staff,
            location,
            resource
        );

        OffsetDateTime startAt = request.startAt();

        tenantSettingsService.validateBookingStart(startAt);

        if (!startAt.isAfter(OffsetDateTime.now())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Appointment must be in the future"
            );
        }

        OffsetDateTime endAt = startAt.plusMinutes(
            service.getDurationMinutes()
        );

        boolean available = availabilityService.isAvailable(
            service,
            staff,
            location,
            resource,
            startAt,
            appointment.getId()
        );

        if (!available) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The selected time is not available"
            );
        }

        appointment.reschedule(staff, location, resource, startAt, endAt);

        return flushMutation(appointment);
    }

    private void requireBookableCustomer(Customer customer) {
        if (
            customer.isProcessingRestricted() || customer.getErasedAt() != null
        ) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "New bookings and rescheduling are paused for this customer"
            );
        }
    }

    private void validateStatusTransition(
        Appointment appointment,
        AppointmentStatus targetStatus
    ) {
        AppointmentStatus currentStatus = appointment.getStatus();

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot transition appointment from " +
                    currentStatus +
                    " to " +
                    targetStatus
            );
        }

        OffsetDateTime now = OffsetDateTime.now();

        /*
         * Don't confirm an appointment that has already started.
         */
        if (
            targetStatus == AppointmentStatus.CONFIRMED &&
            !appointment.getStartAt().isAfter(now)
        ) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot confirm an appointment that has already started"
            );
        }

        /*
         * Completed means the scheduled appointment
         * has actually reached its end.
         */
        if (
            targetStatus == AppointmentStatus.COMPLETED &&
            now.isBefore(appointment.getEndAt())
        ) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Appointment cannot be completed before its end time"
            );
        }

        /*
         * A customer cannot be considered a no-show
         * before the appointment has actually started.
         */
        if (
            targetStatus == AppointmentStatus.NO_SHOW &&
            now.isBefore(appointment.getStartAt())
        ) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Appointment cannot be marked as no-show before its start time"
            );
        }
    }

    private AppointmentResponse flushMutation(Appointment appointment) {
        try {
            /*
             * Force Hibernate to issue the UPDATE now.
             *
             * This lets us catch both optimistic-lock conflicts
             * and PostgreSQL overlap conflicts here.
             */
            appointmentRepository.flush();

            return AppointmentResponse.from(appointment);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Appointment was changed by another request. Reload and try again.",
                e
            );
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The appointment conflicts with another booking.",
                e
            );
        }
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<AppointmentCalendarResponse> findCalendar(
        LocalDate from,
        LocalDate to,
        UUID resourceId,
        UUID customerId,
        UUID serviceId,
        AppointmentStatus status
    ) {
        return findCalendar(
            from,
            to,
            resourceId,
            null,
            null,
            customerId,
            serviceId,
            status
        );
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<AppointmentCalendarResponse> findCalendar(
        LocalDate from,
        LocalDate to,
        UUID resourceId,
        UUID staffId,
        UUID locationId,
        UUID customerId,
        UUID serviceId,
        AppointmentStatus status
    ) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "to must not be before from"
            );
        }

        /*
         * Protect the API from someone requesting
         * years of appointment data in one call.
         */
        if (from.plusDays(90).isBefore(to)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Calendar range cannot exceed 90 days"
            );
        }
        ZoneId zone = tenantSettingsService.getZoneId();

        OffsetDateTime rangeStart = from.atStartOfDay(zone).toOffsetDateTime();

        OffsetDateTime rangeEnd = to
            .plusDays(1)
            .atStartOfDay(zone)
            .toOffsetDateTime();

        List<Appointment> calendar = appointmentRepository.findCalendar(
            rangeStart,
            rangeEnd,
            resourceId,
            staffId,
            locationId,
            customerId,
            serviceId,
            status,
            org.springframework.data.domain.PageRequest.of(0, 10001)
        );

        if (calendar.size() > 10000) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Too many appointments. Choose a shorter period or narrow the calendar filters."
            );
        }
        return calendar
            .stream()
            .map(AppointmentCalendarResponse::from)
            .toList();
    }

    private StaffMember resolveStaff(UUID staffId) {
        if (staffId == null) {
            return null;
        }

        StaffMember staff = staffRepository
            .findById(staffId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Staff member not found"
                )
            );

        if (!staff.isActive()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Staff member is inactive"
            );
        }

        return staff;
    }

    private Location resolveLocation(UUID locationId) {
        if (locationId == null) {
            return null;
        }

        Location location = locationRepository
            .findById(locationId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Location not found"
                )
            );

        if (!location.isActive()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Location is inactive"
            );
        }

        return location;
    }

    private BookableResource resolveResource(UUID resourceId) {
        if (resourceId == null) {
            return null;
        }

        BookableResource resource = resourceRepository
            .findById(resourceId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Resource not found"
                )
            );

        if (
            resource.getType() == ResourceType.STAFF ||
            resource.getType() == ResourceType.ROOM
        ) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Staff and locations must use their dedicated fields"
            );
        }

        if (!resource.isActive()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Resource is inactive"
            );
        }

        return resource;
    }

    private void validateAssignments(
        ServiceOffering service,
        StaffMember staff,
        Location location,
        BookableResource resource
    ) {
        com.example.booking.tenantdata.service.AssignmentPolicy.validateAssignments(
            service,
            staff,
            location,
            resource
        );
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<com.example.booking.tenantdata.availability.AvailabilitySlotResponse> findRescheduleAvailability(
        UUID appointmentId,
        LocalDate from,
        LocalDate to,
        UUID staffId,
        UUID locationId,
        UUID resourceId
    ) {
        Appointment appointment = getAppointment(appointmentId);

        if (
            appointment.getStatus() != AppointmentStatus.PENDING &&
            appointment.getStatus() != AppointmentStatus.CONFIRMED
        ) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Only pending or confirmed appointments can be rescheduled"
        );
        return availabilityService.findAvailability(
            appointment.getService().getId(),
            from,
            to,
            staffId,
            locationId,
            resourceId,
            appointmentId
        );
    }
}
