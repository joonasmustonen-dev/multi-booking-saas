package com.example.booking.tenantdata.staff;

import com.example.booking.tenantdata.availability.*;
import com.example.booking.tenantdata.settings.TenantSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static com.example.booking.tenantdata.staff.StaffWeekScheduleRequest.Shift;

@Service
@Transactional("tenantTransactionManager")
public class StaffWeekScheduleService {

    private final StaffMemberRepository staff;

    private final AvailabilityRuleRepository rules;

    private final AvailabilityExceptionRepository exceptions;

    private final TenantSettingsService settings;

    public StaffWeekScheduleService(
        StaffMemberRepository staff,
        AvailabilityRuleRepository rules,
        AvailabilityExceptionRepository exceptions,
        TenantSettingsService settings
    ) {
        this.staff = staff;

        this.rules = rules;

        this.exceptions = exceptions;

        this.settings = settings;
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public StaffWeekScheduleResponse find(UUID id, LocalDate weekStart) {
        member(id);

        validateWeek(weekStart);

        ZoneId zone = settings.getZoneId();

        var managed = exceptions.findByStaff_IdAndScheduleWeek(id, weekStart);

        List<Shift> shifts = new ArrayList<>();

        if (!managed.isEmpty()) {
            for (var exception : managed)
                if (exception.isAvailable()) {
                    var start = exception.getStartAt().atZoneSameInstant(zone);

                    var end = exception.getEndAt().atZoneSameInstant(zone);

                    shifts.add(
                        new Shift(
                            start.toLocalDate(),
                            start.toLocalTime(),
                            end.toLocalTime()
                        )
                    );
                }
        } else {
            var recurring = rules.findAllByStaff_IdAndActiveTrue(id);

            for (int day = 0; day < 7; day++) {
                LocalDate date = weekStart.plusDays(day);

                for (var rule : recurring)
                    if (rule.getDayOfWeek() == date.getDayOfWeek()) shifts.add(
                        new Shift(date, rule.getStartTime(), rule.getEndTime())
                    );
            }
        }
        shifts.sort(
            Comparator.comparing(Shift::date).thenComparing(Shift::startTime)
        );

        if (managed.isEmpty()) {
            List<Shift> merged = new ArrayList<>();

            for (Shift shift : shifts) {
                if (!merged.isEmpty()) {
                    Shift previous = merged.getLast();

                    if (
                        previous.date().equals(shift.date()) &&
                        !shift.startTime().isAfter(previous.endTime())
                    ) {
                        merged.set(
                            merged.size() - 1,
                            new Shift(
                                previous.date(),
                                previous.startTime(),
                                previous.endTime().isAfter(shift.endTime())
                                    ? previous.endTime()
                                    : shift.endTime()
                            )
                        );

                        continue;
                    }
                }
                merged.add(shift);
            }
            shifts = merged;
        }
        return new StaffWeekScheduleResponse(
            weekStart,
            !managed.isEmpty(),
            shifts
        );
    }

    public StaffWeekScheduleResponse save(
        UUID id,
        LocalDate weekStart,
        StaffWeekScheduleRequest request
    ) {
        validateWeek(weekStart);

        var member = staff
            .findForScheduleUpdate(id)
            .orElseThrow(() -> missing());

        if (request.shifts() == null || request.shifts().size() > 28) fail(
            "Supply up to 28 shifts"
        );

        ZoneId zone = settings.getZoneId();

        Map<LocalDate, List<Shift>> days = new HashMap<>();

        for (Shift shift : request.shifts()) {
            if (
                shift == null ||
                shift.date() == null ||
                shift.startTime() == null ||
                shift.endTime() == null
            ) fail("Every shift needs a date, start, and end");

            if (
                shift.date().isBefore(weekStart) ||
                shift.date().isAfter(weekStart.plusDays(6))
            ) fail("Shift is outside the selected week");

            if (!shift.endTime().isAfter(shift.startTime())) fail(
                "Shift end must follow its start; split overnight shifts by day"
            );

            if (
                zone
                    .getRules()
                    .getValidOffsets(shift.date().atTime(shift.startTime()))
                    .isEmpty() ||
                zone
                    .getRules()
                    .getValidOffsets(shift.date().atTime(shift.endTime()))
                    .isEmpty()
            ) fail("Shift time does not exist in the tenant timezone");

            days.computeIfAbsent(shift.date(), ignored ->
                new ArrayList<>()
            ).add(shift);
        }
        // Validate the whole week before replacing anything.
        for (var shifts : days.values()) {
            shifts.sort(Comparator.comparing(Shift::startTime));

            for (int i = 1; i < shifts.size(); i++) if (
                shifts
                    .get(i)
                    .startTime()
                    .isBefore(shifts.get(i - 1).endTime())
            ) fail("Shifts on the same day cannot overlap");
        }
        exceptions.deleteAll(
            exceptions.findByStaff_IdAndScheduleWeek(id, weekStart)
        );

        List<AvailabilityException> replacement = new ArrayList<>();

        for (int day = 0; day < 7; day++) {
            LocalDate date = weekStart.plusDays(day);

            ZonedDateTime cursor = date.atStartOfDay(zone);

            for (Shift shift : days.getOrDefault(date, List.of())) {
                ZonedDateTime start = date
                    .atTime(shift.startTime())
                    .atZone(zone);

                ZonedDateTime end = date.atTime(shift.endTime()).atZone(zone);

                add(replacement, member, weekStart, cursor, start, false);

                add(replacement, member, weekStart, start, end, true);

                cursor = end;
            }
            add(
                replacement,
                member,
                weekStart,
                cursor,
                date.plusDays(1).atStartOfDay(zone),
                false
            );
        }
        exceptions.saveAllAndFlush(replacement);

        return find(id, weekStart);
    }

    public void reset(UUID id, LocalDate weekStart) {
        validateWeek(weekStart);

        staff.findForScheduleUpdate(id).orElseThrow(() -> missing());

        exceptions.deleteAll(
            exceptions.findByStaff_IdAndScheduleWeek(id, weekStart)
        );
    }

    private void add(
        List<AvailabilityException> result,
        StaffMember member,
        LocalDate week,
        ZonedDateTime start,
        ZonedDateTime end,
        boolean available
    ) {
        if (!end.isAfter(start)) return;
        var exception = new AvailabilityException(
            null,
            member,
            null,
            start.toOffsetDateTime(),
            end.toOffsetDateTime(),
            available
        );

        exception.setScheduleWeek(week);

        result.add(exception);
    }

    private StaffMember member(UUID id) {
        return staff
            .findById(id)
            .filter(member -> !member.isRemoved())
            .orElseThrow(() -> missing());
    }

    private void validateWeek(LocalDate date) {
        if (date == null || date.getDayOfWeek() != DayOfWeek.MONDAY) fail(
            "weekStart must be a Monday"
        );
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Staff member not found"
        );
    }

    private void fail(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
