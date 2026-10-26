package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.dto.*;
import com.farouk.employee_scheduler.model.*;
import com.farouk.employee_scheduler.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service
public class AutomaticSchedulingService {
    private final WeeklyScheduleService schedules;
    private final EmployeeRepository employees;
    private final AvailabilityRepository availability;
    private final ShiftRepository shifts;
    private final ShiftService shiftService;
    private final GenerationPreviewStore store;
    public AutomaticSchedulingService(WeeklyScheduleService schedules,EmployeeRepository employees,
            AvailabilityRepository availability,ShiftRepository shifts,ShiftService shiftService,GenerationPreviewStore store) {
        this.schedules=schedules;this.employees=employees;this.availability=availability;
        this.shifts=shifts;this.shiftService=shiftService;this.store=store;
    }
    private record State(List<SchedulingAlgorithm.EmployeeInput> employees,List<SchedulingAlgorithm.Window> availability,
                         List<SchedulingAlgorithm.Busy> busy,String fingerprint) {}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public GenerationPreview preview(UUID scheduleId,GenerationRequest request,String owner) {
        var schedule=schedules.findById(scheduleId);requireDraft(schedule);
        SchedulingAlgorithm.validate(schedule.getWeekStartDate(),request);
        var state=state(schedule,false);
        var result=SchedulingAlgorithm.generate(schedule.getWeekStartDate(),request,state.employees(),state.availability(),state.busy());
        var preview=new GenerationPreview(UUID.randomUUID(),scheduleId,store.now().plus(GenerationPreviewStore.LIFETIME),
                result.assignments(),result.coverage(),result.workloads());
        var frozen=new GenerationRequest(List.copyOf(request.requirements()),request.maxWeeklyHours(),request.balanceHoursFairly(),request.minShiftMinutes(),request.maxShiftMinutes());
        store.put(new GenerationPreviewStore.Stored(owner,state.fingerprint(),frozen,preview));
        return preview;
    }
    @Transactional
    public List<ShiftResponse> apply(UUID scheduleId,ApplyGenerationRequest request,String owner) {
        var schedule=schedules.lockForModification(scheduleId);requireDraft(schedule);
        if(request==null || request.previewToken()==null || request.suggestionIds()==null || request.suggestionIds().isEmpty()
                || request.suggestionIds().size()>1000 || new HashSet<>(request.suggestionIds()).size()!=request.suggestionIds().size())
            throw new ResponseStatusException(BAD_REQUEST,"Select unique suggestions from a current preview");
        var stored=store.get(request.previewToken(),scheduleId,owner);
        // Schedule lock serializes manual edits, publication, and all applications for this week.
        // Shared employee/availability locks hold relevant eligibility stable through commit.
        var current=state(schedule,true);
        if(!stored.fingerprint().equals(current.fingerprint()))
            throw new ResponseStatusException(CONFLICT,"Employees, availability or weekly assignments changed; generate and review a new preview");
        var selected=new ArrayList<GenerationPreview.Assignment>();
        for(String id:request.suggestionIds()) selected.add(stored.preview().assignments().stream()
                .filter(a->a.suggestionId().equals(id)).findFirst()
                .orElseThrow(()->new ResponseStatusException(BAD_REQUEST,"Selection is not part of this preview")));
        for(var assignment:selected) {
            var requirement=stored.request().requirements().get(assignment.requirementIndex());
            long length=SchedulingAlgorithm.duration(assignment.startTime(),assignment.endTime());
            boolean flexible=requirement.mode()==GenerationRequest.Mode.FLEXIBLE;
            if(!assignment.shiftDate().equals(requirement.date()) || assignment.startTime().isBefore(requirement.startTime())
                    || assignment.endTime().isAfter(requirement.endTime()) || length<=0
                    || (flexible && (length<stored.request().effectiveMinShiftMinutes()*60L || length>stored.request().effectiveMaxShiftMinutes()*60L))
                    || (!flexible && (!assignment.startTime().equals(requirement.startTime()) || !assignment.endTime().equals(requirement.endTime()))))
                throw new ResponseStatusException(CONFLICT,"Preview does not match its window/duration limits; generate a new preview");
        }
        var seconds=new HashMap<UUID,Long>();
        for(var busy:current.busy())seconds.merge(busy.employeeId(),SchedulingAlgorithm.duration(busy.start(),busy.end()),Long::sum);
        for(var a:selected) {
            long total=seconds.merge(a.employeeId(),SchedulingAlgorithm.duration(a.startTime(),a.endTime()),Long::sum);
            if(total>stored.request().maxWeeklyHours()*3600L)
                throw new ResponseStatusException(CONFLICT,"Selected assignments exceed the weekly-hour limit; generate a new preview");
        }
        var created=new ArrayList<ShiftResponse>();
        // Every assignment uses the existing service, with the same transaction and native overlap constraint.
        for(var a:selected)created.add(shiftService.create(scheduleId,new ShiftRequest(a.employeeId(),a.shiftDate(),a.startTime(),a.endTime())));
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit(){store.remove(request.previewToken());}
            });
        } else store.remove(request.previewToken());
        return List.copyOf(created);
    }
    private State state(WeeklySchedule schedule,boolean lock) {
        var team=lock?employees.findAllLockedForScheduling():employees.findAll();
        var windows=lock?availability.findAllLockedForScheduling():availability.findAll();
        var existing=shifts.findByShiftDateBetween(schedule.getWeekStartDate(),schedule.getWeekStartDate().plusDays(6));
        var people=team.stream().map(e->new SchedulingAlgorithm.EmployeeInput(e.getId(),
                e.getUser()==null?"Employee "+e.getId().toString().substring(0,8):e.getUser().getFirstName()+" "+e.getUser().getLastName())).toList();
        var slots=windows.stream().map(a->new SchedulingAlgorithm.Window(a.getEmployee().getId(),a.getDayOfWeek(),a.getStartTime(),a.getEndTime())).toList();
        var busy=existing.stream().map(s->new SchedulingAlgorithm.Busy(s.getEmployee().getId(),s.getShiftDate(),s.getStartTime(),s.getEndTime())).toList();
        var signature=new ArrayList<String>();
        signature.add("week:"+schedule.getId()+":"+schedule.getWeekStartDate()+":"+schedule.getStatus());
        for(var e:team)signature.add("employee:"+e.getId());
        for(var a:windows)signature.add("availability:"+a.getId()+":"+a.getEmployee().getId()+":"+a.getDayOfWeek()+":"+a.getStartTime()+":"+a.getEndTime());
        for(var s:existing)signature.add("shift:"+s.getId()+":"+s.getWeeklySchedule().getId()+":"+s.getEmployee().getId()+":"+s.getShiftDate()+":"+s.getStartTime()+":"+s.getEndTime());
        Collections.sort(signature);
        try {
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n",signature).getBytes(StandardCharsets.UTF_8)));
            return new State(people,slots,busy,digest);
        } catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private void requireDraft(WeeklySchedule schedule) {
        if(schedule.getStatus()!=ScheduleStatus.DRAFT)throw new ResponseStatusException(CONFLICT,"Automatic scheduling is available only for DRAFT schedules");
    }
}
