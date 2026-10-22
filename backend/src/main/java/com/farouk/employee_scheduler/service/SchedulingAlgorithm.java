package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.dto.GenerationRequest;
import com.farouk.employee_scheduler.dto.GenerationRequest.Mode;
import com.farouk.employee_scheduler.dto.GenerationPreview.*;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import org.springframework.web.server.ResponseStatusException;

/** Deterministic greedy planner. No persistence or dependency on JPA. */
public final class SchedulingAlgorithm {
    private SchedulingAlgorithm() {}
    public record EmployeeInput(UUID id, String displayName) {}
    public record Window(UUID employeeId, DayOfWeek day, LocalTime start, LocalTime end) {}
    public record Busy(UUID employeeId, LocalDate date, LocalTime start, LocalTime end) {}
    public record Result(List<Assignment> assignments, List<Coverage> coverage, List<Workload> workloads) {}
    public static void validate(LocalDate week, GenerationRequest request) {
        if (request==null || request.requirements()==null || request.requirements().isEmpty()
                || request.requirements().size()>100 || request.maxWeeklyHours()<1 || request.maxWeeklyHours()>168)
            bad("Use 1–100 requirements and maxWeeklyHours between 1 and 168");
        if(request.effectiveMinShiftMinutes()<1 || request.effectiveMaxShiftMinutes()>1440
                || request.effectiveMinShiftMinutes()>request.effectiveMaxShiftMinutes())
            bad("Generated flexible shift durations must satisfy 1 <= minShiftMinutes <= maxShiftMinutes <= 1440");
        int slots=0;
        for (var r:request.requirements()) {
            if (r==null || r.date()==null || r.startTime()==null || r.endTime()==null
                    || !r.startTime().isBefore(r.endTime()) || r.startTime().getNano()!=0 || r.endTime().getNano()!=0
                    || r.date().isBefore(week) || r.date().isAfter(week.plusDays(6))
                    || r.requiredEmployees()<1 || r.requiredEmployees()>50)
                bad("Each requirement needs a date in this week, whole-second start before end, and 1–50 employees");
            slots+=r.requiredEmployees();
        }
        if (slots>1000) bad("A preview supports at most 1000 requested assignments");
        for(int i=0;i<request.requirements().size();i++) for(int j=i+1;j<request.requirements().size();j++) {
            var a=request.requirements().get(i);var b=request.requirements().get(j);
            if(a.date().equals(b.date()) && overlaps(a.startTime(),a.endTime(),b.startTime(),b.endTime()))
                bad("Requirements on the same day must not overlap; adjacent intervals are allowed");
        }
    }
    public static Result generate(LocalDate week, GenerationRequest request, List<EmployeeInput> employees,
                                   List<Window> availability, List<Busy> existing) {
        validate(week,request);
        var team=employees.stream().sorted(Comparator.comparing(e->e.id().toString())).toList();
        var occupied=new ArrayList<>(existing);
        Map<UUID,Long> seconds=new HashMap<>(), initial=new HashMap<>();
        for(var e:team) {
            long total=existing.stream().filter(b->b.employeeId().equals(e.id()) && !b.date().isBefore(week)
                    && !b.date().isAfter(week.plusDays(6))).mapToLong(b->duration(b.start(),b.end())).sum();
            seconds.put(e.id(),total); initial.put(e.id(),total);
        }
        var order=new ArrayList<Integer>();for(int i=0;i<request.requirements().size();i++)order.add(i);
        order.sort(Comparator.comparing((Integer i)->request.requirements().get(i).date())
                .thenComparing(i->request.requirements().get(i).startTime()).thenComparingInt(i->i));
        var assignments=new ArrayList<Assignment>();var coverage=new ArrayList<Coverage>();
        for(int index:order) {
            var r=request.requirements().get(index);long length=duration(r.startTime(),r.endTime());
            int filled=(int)existing.stream().filter(b->b.date().equals(r.date())).map(Busy::employeeId).distinct()
                    .filter(id->r.mode()==Mode.FLEXIBLE
                            ? existing.stream().anyMatch(b->b.employeeId().equals(id) && b.date().equals(r.date())
                                && overlaps(b.start(),b.end(),r.startTime(),r.endTime()))
                            : covers(existing,id,r)).count();
            int needed=Math.max(0,r.requiredEmployees()-filled), proposed=0;
            if(r.mode()==Mode.FLEXIBLE) {
                var options=new ArrayList<Candidate>();
                for(var employee:team) {
                    // Existing workers count once even if their existing shift is shorter than the new minimum.
                    if(existing.stream().anyMatch(b->b.employeeId().equals(employee.id()) && b.date().equals(r.date())
                            && overlaps(b.start(),b.end(),r.startTime(),r.endTime())))continue;
                    var candidate=flexibleCandidate(employee,r,availability,occupied,
                            request.maxWeeklyHours()*3600L-seconds.get(employee.id()),
                            request.effectiveMinShiftMinutes()*60,request.effectiveMaxShiftMinutes()*60);
                    if(candidate!=null)options.add(candidate);
                }
                Comparator<Candidate> ranking=Comparator.comparingLong(Candidate::length).reversed()
                        .thenComparing(Candidate::start).thenComparing(c->c.employee().id().toString());
                if(request.balanceHoursFairly())ranking=Comparator.comparingLong((Candidate c)->seconds.get(c.employee().id())).thenComparing(ranking);
                options.sort(ranking);
                for(var candidate:options) {
                    if(proposed>=needed)break;
                    var employee=candidate.employee();
                    assignments.add(new Assignment(index+":"+employee.id(),index,employee.id(),employee.displayName(),r.date(),candidate.start(),candidate.end()));
                    occupied.add(new Busy(employee.id(),r.date(),candidate.start(),candidate.end()));
                    seconds.merge(employee.id(),candidate.length(),Long::sum);proposed++;
                }
            } else {
                Comparator<EmployeeInput> ranking=Comparator.comparing(e->e.id().toString());
                if(request.balanceHoursFairly())ranking=Comparator.comparingLong((EmployeeInput e)->seconds.get(e.id())).thenComparing(ranking);
                var candidates=team.stream().sorted(ranking).toList();
                for(var e:candidates) {
                    if(proposed>=needed)break;
                    if(seconds.get(e.id())+length>request.maxWeeklyHours()*3600L)continue;
                    if(availability.stream().noneMatch(w->w.employeeId().equals(e.id()) && w.day()==r.date().getDayOfWeek()
                            && !w.start().isAfter(r.startTime()) && !w.end().isBefore(r.endTime())))continue;
                    if(occupied.stream().anyMatch(b->b.employeeId().equals(e.id()) && b.date().equals(r.date())
                            && overlaps(b.start(),b.end(),r.startTime(),r.endTime())))continue;
                    assignments.add(new Assignment(index+":"+e.id(),index,e.id(),e.displayName(),r.date(),r.startTime(),r.endTime()));
                    occupied.add(new Busy(e.id(),r.date(),r.startTime(),r.endTime()));
                    seconds.merge(e.id(),length,Long::sum);proposed++;
                }
            }
            int shortage=Math.max(0,needed-proposed);
            String explanation;
            if(r.mode()==Mode.FLEXIBLE) {
                explanation="Distinct employees during the window; simultaneous coverage is not guaranteed. "+
                        (shortage==0?"Target met.":"Shortage: no more distinct employees have a free, available interval of at least "+
                                request.effectiveMinShiftMinutes()+" minutes within the weekly-hour limit.");
                if(filled>r.requiredEmployees())explanation+=" Existing workers already exceed this target; existing shifts are preserved.";
            } else explanation=shortage==0?"Staffing target covered":"Not enough employees satisfy full availability, non-overlap and weekly-hour limits";
            coverage.add(new Coverage(index,r.date(),r.startTime(),r.endTime(),r.requiredEmployees(),filled,proposed,shortage,explanation,r.mode()));
        }
        var workloads=team.stream().map(e->new Workload(e.id(),e.displayName(),hours(initial.get(e.id())),
                hours(seconds.get(e.id())-initial.get(e.id())),hours(seconds.get(e.id())))).toList();
        return new Result(List.copyOf(assignments),List.copyOf(coverage),workloads);
    }
    private record Candidate(EmployeeInput employee,LocalTime start,LocalTime end) {
        long length(){return duration(start,end);}
    }
    private static Candidate flexibleCandidate(EmployeeInput employee,GenerationRequest.Requirement requirement,
            List<Window> windows,List<Busy> occupied,long budget,int minimum,int maximum) {
        if(budget<minimum)return null;
        int cap=(int)Math.min(budget,maximum);
        var busy=occupied.stream().filter(b->b.employeeId().equals(employee.id()) && b.date().equals(requirement.date()))
                .sorted(Comparator.comparing(Busy::start).thenComparing(Busy::end)).toList();
        Candidate best=null;
        for(var window:windows) {
            if(!window.employeeId().equals(employee.id()) || window.day()!=requirement.date().getDayOfWeek())continue;
            // Round inward to whole seconds; never extend fractional-second availability.
            int start=Math.max(requirement.startTime().toSecondOfDay(),ceilSecond(window.start()));
            int end=Math.min(requirement.endTime().toSecondOfDay(),window.end().toSecondOfDay());
            int cursor=start;
            for(var assignment:busy) {
                int busyStart=assignment.start().toSecondOfDay(),busyEnd=ceilSecond(assignment.end());
                if(busyEnd<=cursor)continue;
                if(busyStart>=end)break;
                best=prefer(best,slot(employee,cursor,Math.min(end,busyStart),minimum,cap));
                cursor=Math.max(cursor,busyEnd);
                if(cursor>=end)break;
            }
            best=prefer(best,slot(employee,cursor,end,minimum,cap));
        }
        return best;
    }
    private static int ceilSecond(LocalTime value){return value.toSecondOfDay()+(value.getNano()==0?0:1);}
    private static Candidate slot(EmployeeInput employee,int start,int end,int minimum,int maximum) {
        int length=Math.min(end-start,maximum);
        return length<minimum?null:new Candidate(employee,LocalTime.ofSecondOfDay(start),LocalTime.ofSecondOfDay(start+length));
    }
    private static Candidate prefer(Candidate best,Candidate candidate) {
        if(candidate==null)return best;
        return best==null || candidate.length()>best.length() || (candidate.length()==best.length() && candidate.start().isBefore(best.start()))?candidate:best;
    }
    private static boolean covers(List<Busy> existing,UUID employeeId,GenerationRequest.Requirement requirement) {
        var intervals=existing.stream().filter(b->b.employeeId().equals(employeeId) && b.date().equals(requirement.date()))
                .sorted(Comparator.comparing(Busy::start)).toList();
        LocalTime cursor=requirement.startTime();
        for(var interval:intervals) {
            if(!interval.end().isAfter(cursor))continue;
            if(interval.start().isAfter(cursor))return false;
            cursor=interval.end();
            if(!cursor.isBefore(requirement.endTime()))return true;
        }
        return false;
    }
    public static boolean overlaps(LocalTime a,LocalTime b,LocalTime c,LocalTime d){return a.isBefore(d)&&b.isAfter(c);}
    public static long duration(LocalTime start,LocalTime end){return Duration.between(start,end).getSeconds();}
    private static BigDecimal hours(long seconds){return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600),2,RoundingMode.HALF_UP);}
    private static void bad(String message){throw new ResponseStatusException(BAD_REQUEST,message);}
}
