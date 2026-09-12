package com.example.booking.tenantdata.appointment;

import com.example.booking.tenantdata.Customer;
import com.example.booking.tenantdata.CustomerRepository;
import com.example.booking.tenantdata.resource.BookableResource;
import com.example.booking.tenantdata.resource.BookableResourceRepository;
import com.example.booking.tenantdata.service.ServiceOffering;
import com.example.booking.tenantdata.service.ServiceOfferingRepository;
import com.example.booking.tenantdata.availability.AvailabilityService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class AppointmentService {


    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final BookableResourceRepository resourceRepository;

    private final AvailabilityService availabilityService;

    public AppointmentService(
        AppointmentRepository appointmentRepository,
        CustomerRepository customerRepository,
        ServiceOfferingRepository serviceRepository,
        BookableResourceRepository resourceRepository,
        AvailabilityService availabilityService) {

        this.appointmentRepository = appointmentRepository;
        this.customerRepository = customerRepository;
        this.serviceRepository = serviceRepository;
        this.resourceRepository = resourceRepository;
        this.availabilityService = availabilityService;
        }

    @Transactional("tenantTransactionManager")
    public AppointmentResponse create(
            CreateAppointmentRequest request) {

        Customer customer =
                customerRepository.findById(request.customerId())
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Customer not found"
                                )
                        );

        ServiceOffering service =
                serviceRepository
                        .findByIdWithResources(request.serviceId())
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Service not found"
                                )
                        );

        BookableResource resource =
                resourceRepository.findById(request.resourceId())
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Resource not found"
                                )
                        );

        if (!resource.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Resource is inactive"
            );
        }

        boolean eligible =
                service.getResources()
                        .stream()
                        .anyMatch(r ->
                                r.getId()
                                        .equals(resource.getId())
                        );

        if (!eligible) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Resource cannot perform this service"
            );
        }

        OffsetDateTime startAt =
                request.startAt();

        OffsetDateTime endAt =
                startAt.plusMinutes(
                        service.getDurationMinutes()
                );

        if (!startAt.isAfter(OffsetDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Appointment must be in the future"
            );
        }

        boolean available =
        availabilityService.isAvailable(
                service,
                resource,
                startAt
        );

        if (!available) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The selected time is not available"
        );
}

        Appointment appointment =
                new Appointment(
                        customer,
                        service,
                        resource,
                        startAt,
                        endAt,
                        request.notes()
                );

        try {

            /*
             * Flush is intentional.
             *
             * It forces PostgreSQL's exclusion constraint
             * to execute here, so we can convert the race
             * into HTTP 409.
             */
            appointment =
                    appointmentRepository.saveAndFlush(
                            appointment
                    );

        } catch (DataIntegrityViolationException e) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The selected time is no longer available",
                    e
            );
        }

        return AppointmentResponse.from(appointment);
    }

    @Transactional(
            value = "tenantTransactionManager",
            readOnly = true
    )
    public List<AppointmentResponse> findAll() {

        return appointmentRepository
                .findAllByOrderByStartAtAsc()
                .stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    @Transactional(
            value = "tenantTransactionManager",
            readOnly = true
    )
    public AppointmentResponse findById(UUID id) {

        return AppointmentResponse.from(
                getAppointment(id)
        );
    }

        @Transactional("tenantTransactionManager")
        public AppointmentResponse updateStatus(
                UUID id,
                AppointmentStatus targetStatus) {

        Appointment appointment =
                getAppointment(id);

        validateStatusTransition(
                appointment,
                targetStatus
        );

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

        Appointment appointment =
                getAppointment(id);

        validateStatusTransition(
                appointment,
                AppointmentStatus.CANCELLED
        );

        appointment.changeStatus(
                AppointmentStatus.CANCELLED
        );

        return flushMutation(appointment);
        }


    @Transactional("tenantTransactionManager")
        public AppointmentResponse reschedule(
                UUID appointmentId,
                RescheduleAppointmentRequest request) {

        Appointment appointment =
                getAppointment(appointmentId);

        if (appointment.getStatus()
                != AppointmentStatus.PENDING
                &&
                appointment.getStatus()
                != AppointmentStatus.CONFIRMED) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Only pending or confirmed appointments can be rescheduled"
                );
        }

        ServiceOffering service =
                appointment.getService();

        BookableResource resource =
                resourceRepository
                        .findById(request.resourceId())
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Resource not found"
                                )
                        );

        if (!resource.isActive()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Resource is inactive"
                );
        }

        boolean eligible =
                service.getResources()
                        .stream()
                        .anyMatch(candidate ->
                                candidate.getId()
                                        .equals(resource.getId())
                        );

        if (!eligible) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Resource cannot perform this service"
                );
        }

        OffsetDateTime startAt =
                request.startAt();

        if (!startAt.isAfter(OffsetDateTime.now())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Appointment must be in the future"
                );
        }

        OffsetDateTime endAt =
                startAt.plusMinutes(
                        service.getDurationMinutes()
                );

        boolean available =
                availabilityService.isAvailable(
                        service,
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

        appointment.reschedule(
                resource,
                startAt,
                endAt
        );

        try {

                /*
                * Force PostgreSQL's exclusion constraint
                * to execute before returning.
                */
                appointmentRepository.flush();

        } catch (DataIntegrityViolationException e) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The selected time is no longer available",
                        e
                );
        }

        return flushMutation(appointment);
        }


        private void validateStatusTransition(
        Appointment appointment,
        AppointmentStatus targetStatus) {

        AppointmentStatus currentStatus =
            appointment.getStatus();

        if (!currentStatus.canTransitionTo(targetStatus)) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cannot transition appointment from "
                                + currentStatus
                                + " to "
                                + targetStatus
                );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        /*
        * Don't confirm an appointment that has already started.
        */
        if (targetStatus == AppointmentStatus.CONFIRMED
                && !appointment.getStartAt().isAfter(now)) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cannot confirm an appointment that has already started"
                );
        }

        /*
        * Completed means the scheduled appointment
        * has actually reached its end.
        */
        if (targetStatus == AppointmentStatus.COMPLETED
                && now.isBefore(appointment.getEndAt())) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Appointment cannot be completed before its end time"
                );
        }

        /*
        * A customer cannot be considered a no-show
        * before the appointment has actually started.
        */
        if (targetStatus == AppointmentStatus.NO_SHOW
                && now.isBefore(appointment.getStartAt())) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Appointment cannot be marked as no-show before its start time"
                );
        }
        }


        private AppointmentResponse flushMutation(
        Appointment appointment) {

    try {

        /*
         * Force Hibernate to issue the UPDATE now.
         *
         * This lets us catch both optimistic-lock conflicts
         * and PostgreSQL overlap conflicts here.
         */
        appointmentRepository.flush();

        return AppointmentResponse.from(
                appointment
        );

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


        @Transactional(
        value = "tenantTransactionManager",
        readOnly = true
)
        public List<AppointmentCalendarResponse> findCalendar(
                OffsetDateTime from,
                OffsetDateTime to,
                UUID resourceId,
                UUID customerId,
                UUID serviceId,
                AppointmentStatus status) {

        if (!to.isAfter(from)) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "to must be after from"
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

        return appointmentRepository
                .findCalendar(
                        from,
                        to,
                        resourceId,
                        customerId,
                        serviceId,
                        status
                )
                .stream()
                .map(AppointmentCalendarResponse::from)
                .toList();
        }

}