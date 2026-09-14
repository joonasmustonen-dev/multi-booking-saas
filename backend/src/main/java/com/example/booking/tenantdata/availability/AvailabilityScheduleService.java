package com.example.booking.tenantdata.availability;

import com.example.booking.tenantdata.resource.*;
import com.example.booking.tenantdata.staff.*;
import com.example.booking.tenantdata.location.*;
import com.example.booking.tenantdata.settings.TenantSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.List;
import java.util.UUID;

@Service
@Transactional("tenantTransactionManager")
public class AvailabilityScheduleService {
    private final BookableResourceRepository resources;
    private final StaffMemberRepository staff;
    private final LocationRepository locations;
    private final AvailabilityRuleRepository rules;
    private final AvailabilityExceptionRepository exceptions;
    private final TenantSettingsService settings;
    public AvailabilityScheduleService(BookableResourceRepository resources, StaffMemberRepository staff,
            LocationRepository locations, AvailabilityRuleRepository rules,
            AvailabilityExceptionRepository exceptions, TenantSettingsService settings) {
        this.resources = resources; this.staff = staff; this.locations = locations;
        this.rules = rules; this.exceptions = exceptions; this.settings = settings;
    }
    public AvailabilityRuleResponse createRule(AvailabilityOwnerType type, UUID id, AvailabilityRuleRequest request) {
        validateTimes(request.startTime(), request.endTime());
        Owner owner = owner(type, id);
        return AvailabilityRuleResponse.from(rules.save(new AvailabilityRule(owner.resource(), owner.staff(), owner.location(),
                request.dayOfWeek(), request.startTime(), request.endTime())));
    }
    public AvailabilityRuleResponse updateRule(AvailabilityOwnerType type, UUID id, UUID ruleId, UpdateAvailabilityRuleRequest request) {
        owner(type, id);
        AvailabilityRule rule = rule(type, id, ruleId);
        validateTimes(request.startTime(), request.endTime());
        rule.update(request.dayOfWeek(), request.startTime(), request.endTime(), request.active());
        return AvailabilityRuleResponse.from(rule);
    }
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<AvailabilityRuleResponse> findRules(AvailabilityOwnerType type, UUID id) {
        owner(type, id);
        List<AvailabilityRule> result = switch (type) {
            case STAFF -> rules.findByStaff_IdOrderByDayOfWeekAscStartTimeAsc(id);
            case LOCATION -> rules.findByLocation_IdOrderByDayOfWeekAscStartTimeAsc(id);
            case RESOURCE -> rules.findByResource_IdOrderByDayOfWeekAscStartTimeAsc(id);
        };
        return result.stream().map(AvailabilityRuleResponse::from).toList();
    }
    public void deleteRule(AvailabilityOwnerType type, UUID id, UUID ruleId) {
        owner(type, id); rules.delete(rule(type, id, ruleId));
    }
    public List<AvailabilityRuleResponse> replaceRules(AvailabilityOwnerType type, UUID id,
            ReplaceAvailabilityRulesRequest request) {
        Owner owner = owner(type, id);
        // Validate the complete draft before deleting any existing rules.
        for (var draft : request.rules()) validateTimes(draft.startTime(), draft.endTime());
        for (DayOfWeek day : DayOfWeek.values()) {
            var active = request.rules().stream().filter(r -> r.dayOfWeek() == day && r.active())
                    .sorted(java.util.Comparator.comparing(ReplaceAvailabilityRulesRequest.Rule::startTime)).toList();
            LocalTime previousEnd = null;
            for (var draft : active) {
                if (previousEnd != null && draft.startTime().isBefore(previousEnd))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recurring hours overlap on " + day);
                previousEnd = draft.endTime();
            }
        }
        var existing = switch (type) {
            case STAFF -> rules.findByStaff_IdOrderByDayOfWeekAscStartTimeAsc(id);
            case LOCATION -> rules.findByLocation_IdOrderByDayOfWeekAscStartTimeAsc(id);
            case RESOURCE -> rules.findByResource_IdOrderByDayOfWeekAscStartTimeAsc(id);
        };
        rules.deleteAll(existing);
        return rules.saveAllAndFlush(request.rules().stream().map(draft -> {
            var rule = new AvailabilityRule(owner.resource(), owner.staff(), owner.location(),
                    draft.dayOfWeek(), draft.startTime(), draft.endTime());
            rule.update(draft.dayOfWeek(), draft.startTime(), draft.endTime(), draft.active());
            return rule;
        }).toList()).stream().map(AvailabilityRuleResponse::from).toList();
    }
    public AvailabilityExceptionResponse createException(AvailabilityOwnerType type, UUID id, AvailabilityExceptionRequest request) {
        Owner owner = owner(type, id);
        var times = exceptionTimes(request);
        return AvailabilityExceptionResponse.from(exceptions.save(new AvailabilityException(owner.resource(), owner.staff(), owner.location(),
                times.start(), times.end(), request.available())));
    }
    public AvailabilityExceptionResponse updateException(AvailabilityOwnerType type, UUID id, UUID exceptionId, AvailabilityExceptionRequest request) {
        owner(type, id);
        AvailabilityException exception = exception(type, id, exceptionId);
        var times = exceptionTimes(request);
        exception.update(times.start(), times.end(), request.available());
        return AvailabilityExceptionResponse.from(exception);
    }
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<AvailabilityExceptionResponse> findExceptions(AvailabilityOwnerType type, UUID id) {
        owner(type, id);
        List<AvailabilityException> result = switch (type) {
            case STAFF -> exceptions.findByStaff_IdOrderByStartAtAsc(id);
            case LOCATION -> exceptions.findByLocation_IdOrderByStartAtAsc(id);
            case RESOURCE -> exceptions.findByResourceIdOrderByStartAtAsc(id);
        };
        return result.stream().filter(exception -> exception.getScheduleWeek() == null).map(AvailabilityExceptionResponse::from).toList();
    }
    public void deleteException(AvailabilityOwnerType type, UUID id, UUID exceptionId) {
        owner(type, id); exceptions.delete(exception(type, id, exceptionId));
    }
    private AvailabilityRule rule(AvailabilityOwnerType type, UUID id, UUID ruleId) {
        var rule = rules.findById(ruleId).orElseThrow(() -> missing("Availability rule"));
        if (!ownedBy(type, id, rule.getStaff(), rule.getLocation(), rule.getResource())) throw missing("Availability rule");
        return rule;
    }
    private AvailabilityException exception(AvailabilityOwnerType type, UUID id, UUID exceptionId) {
        var exception = exceptions.findById(exceptionId).orElseThrow(() -> missing("Availability exception"));
        if (!ownedBy(type, id, exception.getStaff(), exception.getLocation(), exception.getResource())) throw missing("Availability exception");
        if (exception.getScheduleWeek() != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Edit this exception using the weekly staff rota");
        return exception;
    }
    private boolean ownedBy(AvailabilityOwnerType type, UUID id, StaffMember staff, Location location, BookableResource resource) {
        return switch (type) {
            case STAFF -> staff != null && id.equals(staff.getId());
            case LOCATION -> location != null && id.equals(location.getId());
            case RESOURCE -> resource != null && id.equals(resource.getId());
        };
    }
    private Owner owner(AvailabilityOwnerType type, UUID id) {
        return switch (type) {
            case STAFF -> new Owner(null, staff.findById(id).filter(member -> !member.isRemoved()).orElseThrow(() -> missing("Staff member")), null);
            case LOCATION -> new Owner(null, null, locations.findById(id).orElseThrow(() -> missing("Location")));
            case RESOURCE -> {
                var resource = resources.findById(id).orElseThrow(() -> missing("Resource"));
                if (resource.getType() == ResourceType.STAFF || resource.getType() == ResourceType.ROOM)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use native staff or location availability");
                yield new Owner(resource, null, null);
            }
        };
    }
    private void validateTimes(LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endTime must be after startTime");
    }
    private ExceptionTimes exceptionTimes(AvailabilityExceptionRequest request) {
        ZoneId zone = settings.getZoneId();
        if (zone.getRules().getValidOffsets(request.startAt()).isEmpty() || zone.getRules().getValidOffsets(request.endAt()).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exception time does not exist in the tenant timezone");
        var start = request.startAt().atZone(zone).toOffsetDateTime();
        var end = request.endAt().atZone(zone).toOffsetDateTime();
        if (!end.isAfter(start)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endAt must be after startAt");
        return new ExceptionTimes(start, end);
    }
    private ResponseStatusException missing(String label) { return new ResponseStatusException(HttpStatus.NOT_FOUND, label + " not found"); }
    private record Owner(BookableResource resource, StaffMember staff, Location location) {}
    private record ExceptionTimes(OffsetDateTime start, OffsetDateTime end) {}
}
