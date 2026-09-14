package com.example.booking.tenantdata.dashboard;

import com.example.booking.tenantdata.CustomerRepository;

import com.example.booking.tenantdata.appointment.Appointment;
import com.example.booking.tenantdata.appointment.AppointmentCalendarResponse;
import com.example.booking.tenantdata.appointment.AppointmentRepository;
import com.example.booking.tenantdata.appointment.AppointmentStatus;

import com.example.booking.tenantdata.availability.AvailabilityService;

import com.example.booking.tenantdata.resource.BookableResource;
import com.example.booking.tenantdata.staff.StaffMemberRepository;
import com.example.booking.tenantdata.staff.StaffMember;
import com.example.booking.tenantdata.resource.ResourceType;

import com.example.booking.tenantdata.service.ServiceOffering;
import com.example.booking.tenantdata.service.ServiceOfferingRepository;

import com.example.booking.tenantdata.settings.TenantSettingsService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import java.time.temporal.TemporalAdjusters;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import java.util.function.Function;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.example.booking.tenantdata.dashboard
        .DashboardSummaryResponse.*;


@Service
public class DashboardService {

    private final CustomerRepository customerRepository;

    private final AppointmentRepository appointmentRepository;

    private final StaffMemberRepository staffRepository;

    private final ServiceOfferingRepository serviceRepository;

    private final AvailabilityService availabilityService;

    private final TenantSettingsService tenantSettingsService;

    public DashboardService(
        CustomerRepository customerRepository,
        AppointmentRepository appointmentRepository,
        StaffMemberRepository staffRepository,
        ServiceOfferingRepository serviceRepository,
        AvailabilityService availabilityService,
        TenantSettingsService tenantSettingsService
    ) {
        this.customerRepository = customerRepository;

        this.appointmentRepository = appointmentRepository;

        this.staffRepository = staffRepository;

        this.serviceRepository = serviceRepository;

        this.availabilityService = availabilityService;

        this.tenantSettingsService = tenantSettingsService;
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public DashboardSummaryResponse getSummary() {
        ZoneId zone = tenantSettingsService.getZoneId();

        LocalDate today = LocalDate.now(zone);

        LocalDate weekStart = today.with(
            TemporalAdjusters.previousOrSame(
                tenantSettingsService.getSettings().weekStartsOn() == 0
                    ? DayOfWeek.SUNDAY
                    : DayOfWeek.MONDAY
            )
        );

        LocalDate weekEnd = weekStart.plusDays(6);

        OffsetDateTime todayStart = today.atStartOfDay(zone).toOffsetDateTime();

        OffsetDateTime tomorrowStart = today
            .plusDays(1)
            .atStartOfDay(zone)
            .toOffsetDateTime();

        OffsetDateTime weekStartAt = weekStart
            .atStartOfDay(zone)
            .toOffsetDateTime();

        OffsetDateTime weekEndExclusive = weekEnd
            .plusDays(1)
            .atStartOfDay(zone)
            .toOffsetDateTime();

        List<Appointment> todayAppointments =
            appointmentRepository.findCalendar(
                todayStart,
                tomorrowStart,
                null,
                null,
                null,
                null,
                null,
                null
            );

        List<Appointment> weekAppointments = appointmentRepository.findCalendar(
            weekStartAt,
            weekEndExclusive,
            null,
            null,
            null,
            null,
            null,
            null
        );

        /*
         * Cancelled appointments do not count
         * as actual bookings/capacity usage.
         */
        List<Appointment> activeToday = todayAppointments
            .stream()
            .filter(
                appointment ->
                    appointment.getStatus() != AppointmentStatus.CANCELLED
            )
            .toList();

        List<Appointment> activeWeek = weekAppointments
            .stream()
            .filter(
                appointment ->
                    appointment.getStatus() != AppointmentStatus.CANCELLED
            )
            .toList();

        long customerCount = customerRepository.count();

        long openSlots = calculateOpenSlots(today, zone);

        List<DailyBookingCount> dailyBookings = buildDailyCounts(
            activeWeek,
            weekStart,
            zone
        );

        List<TeamMemberSummary> team = buildTeamSummary(activeToday);

        List<PopularServiceSummary> popularServices = buildPopularServices(
            activeWeek
        );

        List<StatusCount> statusCounts = buildStatusCounts(weekAppointments);

        List<AppointmentCalendarResponse> appointmentResponses = activeToday
            .stream()
            .map(AppointmentCalendarResponse::from)
            .toList();

        return new DashboardSummaryResponse(
            zone.getId(),
            today,

            activeToday.size(),
            openSlots,
            customerCount,
            activeWeek.size(),

            dailyBookings,
            appointmentResponses,
            team,
            popularServices,
            statusCounts
        );
    }

    private long calculateOpenSlots(LocalDate today, ZoneId zone) {
        OffsetDateTime now = ZonedDateTime.now(zone).toOffsetDateTime();

        List<ServiceOffering> services = serviceRepository
            .findAllWithResources()
            .stream()
            .filter(ServiceOffering::isActive)
            .toList();

        /*
         * One assignment combination/start pair is
         * counted once even if several services
         * could begin at that same time.
         */
        Set<String> uniqueSlots = new LinkedHashSet<>();

        for (ServiceOffering service : services) {
            var slots = availabilityService.findAvailability(
                service.getId(),
                today,
                today,
                null,
                null,
                null
            );

            for (var slot : slots) {
                if (slot.start().isBefore(now)) {
                    continue;
                }

                String key =
                    slot.staffId() +
                    "|" +
                    slot.locationId() +
                    "|" +
                    slot.resourceId() +
                    "|" +
                    slot.start().toInstant();

                uniqueSlots.add(key);
            }
        }

        return uniqueSlots.size();
    }

    private List<DailyBookingCount> buildDailyCounts(
        List<Appointment> appointments,
        LocalDate weekStart,
        ZoneId zone
    ) {
        Map<LocalDate, Long> counts = appointments.stream().collect(
            Collectors.groupingBy(
                appointment ->
                    appointment
                        .getStartAt()
                        .atZoneSameInstant(zone)
                        .toLocalDate(),

                Collectors.counting()
            )
        );

        return IntStream.range(0, 7)
            .mapToObj(dayOffset -> {
                LocalDate date = weekStart.plusDays(dayOffset);

                return new DailyBookingCount(
                    date,
                    counts.getOrDefault(date, 0L)
                );
            })
            .toList();
    }

    private List<TeamMemberSummary> buildTeamSummary(
        List<Appointment> todayAppointments
    ) {
        Map<UUID, Long> appointmentCounts = todayAppointments
            .stream()
            .filter(
                a ->
                    a.getStaff() != null &&
                    a.getStatus() != AppointmentStatus.CANCELLED
            )
            .collect(
                Collectors.groupingBy(
                    a -> a.getStaff().getId(),
                    Collectors.counting()
                )
            );

        return staffRepository
            .findAll()
            .stream()
            .filter(StaffMember::isActive)
            .map(staff ->
                new TeamMemberSummary(
                    staff.getId(),
                    staff.getName(),
                    appointmentCounts.getOrDefault(staff.getId(), 0L)
                )
            )
            .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
            .toList();
    }

    private List<PopularServiceSummary> buildPopularServices(
        List<Appointment> appointments
    ) {
        Map<UUID, Long> bookingCounts = appointments.stream().collect(
            Collectors.groupingBy(
                appointment -> appointment.getService().getId(),

                Collectors.counting()
            )
        );

        Map<UUID, String> serviceNames = appointments
            .stream()
            .map(Appointment::getService)
            .collect(
                Collectors.toMap(
                    ServiceOffering::getId,
                    ServiceOffering::getName,
                    (first, ignored) -> first
                )
            );

        return bookingCounts
            .entrySet()
            .stream()
            .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
            .map(entry ->
                new PopularServiceSummary(
                    entry.getKey(),

                    serviceNames.get(entry.getKey()),

                    entry.getValue()
                )
            )
            .toList();
    }

    private List<StatusCount> buildStatusCounts(
        List<Appointment> appointments
    ) {
        Map<AppointmentStatus, Long> counts = appointments
            .stream()
            .collect(
                Collectors.groupingBy(
                    Appointment::getStatus,
                    Collectors.counting()
                )
            );

        return Arrays.stream(AppointmentStatus.values())
            .map(status ->
                new StatusCount(
                    status,

                    counts.getOrDefault(status, 0L)
                )
            )
            .toList();
    }
}
