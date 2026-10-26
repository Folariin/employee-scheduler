package com.farouk.employee_scheduler.service;

import com.farouk.employee_scheduler.dto.*;
import com.farouk.employee_scheduler.model.ScheduleStatus;
import com.farouk.employee_scheduler.service.llm.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;

@Service
public class StaffingInterpretationService {
    private final WeeklyScheduleService schedules;
    private final StaffingInterpreter provider;
    private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    public StaffingInterpretationService(WeeklyScheduleService schedules,StaffingInterpreter provider){this.schedules=schedules;this.provider=provider;}
    // Deliberately no transaction spanning the external call, and no references to shift/apply/write services.
    public InterpretationResponse interpret(UUID id, InterpretationRequest request) {
        if(request==null || request.instructions()==null || request.instructions().isBlank() || request.instructions().length()>4000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Instructions must contain 1–4000 characters");
        var schedule=schedules.findById(id);
        if(schedule.getStatus()!=ScheduleStatus.DRAFT)throw new ResponseStatusException(HttpStatus.CONFLICT,"Interpretation requires a draft schedule");
        var week=schedule.getWeekStartDate();
        // Do not spend a provider call on an explicitly unsupported requirement.
        if(request.instructions().toLowerCase(Locale.ROOT).matches("(?s).*(continuous(ly)?|always on duty|at all times|throughout the (day|window)).*"))
            return new InterpretationResponse(List.of(),null,null,List.of(),List.of("Continuous staffing coverage is unsupported. Fixed shifts cover exact intervals; flexible shifts count distinct employees across a window."),"Continuous coverage requires a different scheduling capability.");
        String raw=provider.interpret(week,request.instructions());
        try { return parse(week,request.instructions(),raw); }
        catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI returned invalid staffing requirements; revise instructions or use manual entry");}
    }
    InterpretationResponse parse(LocalDate week,String instructions,String raw) {
        if(raw==null || raw.length()>32768)throw new IllegalArgumentException();
        // readTree alone permits trailing tokens; read a second value to explicitly reject them.
        JsonNode root;
        try(var parser=json.createParser(raw)) {
            root=json.readTree(parser);
            if(parser.nextToken()!=null)throw new IllegalArgumentException();
        }
        validateShape(root,json.readTree(InterpretationSchema.JSON));
        var questions=strings(root.get("clarificationQuestions"));
        var unsupported=strings(root.get("unsupportedConstraints"));
        String summary=root.get("summary").asString();
        if(summary.isBlank() || summary.length()>2000)throw new IllegalArgumentException();
        if(!root.get("maxWeeklyHours").isNull() && !root.get("maxWeeklyHours").canConvertToInt())throw new IllegalArgumentException();
        Integer hours=root.get("maxWeeklyHours").isNull()?null:root.get("maxWeeklyHours").intValue();
        Boolean fair=root.get("balanceHoursFairly").isNull()?null:root.get("balanceHoursFairly").booleanValue();
        evidence(instructions,root.get("maxWeeklyHoursEvidence"),hours!=null);
        evidence(instructions,root.get("fairnessEvidence"),fair!=null);
        if(hours!=null && (hours<1 || hours>168))throw new IllegalArgumentException();
        var rows=root.get("requirements");
        if(rows.size()>100)throw new IllegalArgumentException();
        var requirements=new ArrayList<GenerationRequest.Requirement>();
        int index=0;
        for(var row:rows) {
            index++;
            LocalDate date=null;LocalTime start=null,end=null;Integer count=null;GenerationRequest.Mode mode=null;
            if(!row.get("day").isNull()) {
                String day=row.get("day").asString();
                date=day.matches("[A-Z]+")?week.plusDays(DayOfWeek.valueOf(day).getValue()-1):LocalDate.parse(day);
                if(date.isBefore(week) || date.isAfter(week.plusDays(6)))throw new IllegalArgumentException();
            }
            if(!row.get("startTime").isNull())start=LocalTime.parse(row.get("startTime").asString());
            if(!row.get("endTime").isNull())end=LocalTime.parse(row.get("endTime").asString());
            if(start!=null && start.getNano()!=0 || end!=null && end.getNano()!=0 || start!=null && end!=null && !start.isBefore(end))throw new IllegalArgumentException();
            if(!row.get("requiredEmployees").isNull()) {
                if(!row.get("requiredEmployees").canConvertToInt())throw new IllegalArgumentException();
                count=row.get("requiredEmployees").intValue();if(count<1 || count>50)throw new IllegalArgumentException();
            }
            if(!row.get("mode").isNull())mode=GenerationRequest.Mode.valueOf(row.get("mode").asString());
            if(date==null || start==null || end==null || count==null || mode==null) {
                questions.add("Requirement "+index+": specify any missing day, start/end times, employee count and whether FIXED full shifts or FLEXIBLE distinct employees are intended.");
            } else requirements.add(new GenerationRequest.Requirement(date,start,end,count,mode));
        }
        if(requirements.isEmpty() && questions.isEmpty() && unsupported.isEmpty())questions.add("Specify the days, times, employee counts and FIXED or FLEXIBLE intent.");
        if(!requirements.isEmpty())SchedulingAlgorithm.validate(week,new GenerationRequest(requirements,hours==null?168:hours,Boolean.TRUE.equals(fair)));
        return new InterpretationResponse(List.copyOf(requirements),hours,fair,List.copyOf(questions),List.copyOf(unsupported),summary);
    }
    private static void evidence(String instructions,JsonNode node,boolean set) {
        if(set ? node.isNull() || node.asString().isBlank() || node.asString().length()>500 || !instructions.contains(node.asString()) : !node.isNull())throw new IllegalArgumentException();
    }
    private static ArrayList<String> strings(JsonNode node) {
        if(node.size()>20)throw new IllegalArgumentException();
        var result=new ArrayList<String>();
        for(var value:node){if(value.asString().isBlank() || value.asString().length()>1000)throw new IllegalArgumentException();result.add(value.asString());}
        return result;
    }
    private static void validateShape(JsonNode value,JsonNode schema) {
        var type=schema.get("type");
        boolean valid=false;
        if(type.isString())valid=hasType(value,type.asString());
        else for(var allowed:type)valid|=hasType(value,allowed.asString());
        if(!valid)throw new IllegalArgumentException();
        if(value.isNull())return;
        if(value.isObject()) {
            var properties=schema.get("properties");
            if(value.size()!=properties.size())throw new IllegalArgumentException();
            for(var entry:properties.properties()) {
                if(!value.has(entry.getKey()))throw new IllegalArgumentException();
                validateShape(value.get(entry.getKey()),entry.getValue());
            }
        } else if(value.isArray())for(var child:value)validateShape(child,schema.get("items"));
        if(schema.has("enum")) {
            boolean matches=false;for(var allowed:schema.get("enum"))matches|=allowed.equals(value);
            if(!matches)throw new IllegalArgumentException();
        }
    }
    private static boolean hasType(JsonNode n,String type) {
        return switch(type){case "object"->n.isObject();case "array"->n.isArray();case "string"->n.isString();
            case "integer"->n.isIntegralNumber();case "boolean"->n.isBoolean();case "null"->n.isNull();default->false;};
    }
}
