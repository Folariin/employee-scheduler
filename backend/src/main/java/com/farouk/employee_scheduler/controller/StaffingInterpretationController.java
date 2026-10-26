package com.farouk.employee_scheduler.controller;
import com.farouk.employee_scheduler.dto.*;
import com.farouk.employee_scheduler.service.StaffingInterpretationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/admin/schedules/{id}")
public class StaffingInterpretationController {
    private final StaffingInterpretationService service;
    public StaffingInterpretationController(StaffingInterpretationService service){this.service=service;}
    // Handle locally so Spring's default validation warnings cannot print rejected instruction text.
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public org.springframework.http.ResponseEntity<org.springframework.http.ProblemDetail> invalidInput() {
        var status=org.springframework.http.HttpStatus.BAD_REQUEST;
        return org.springframework.http.ResponseEntity.status(status).body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                status,"Provide valid JSON with instructions containing 1–4000 characters"));
    }
    @PostMapping("/interpret")
    public InterpretationResponse interpret(@PathVariable UUID id,@Valid @RequestBody InterpretationRequest request){return service.interpret(id,request);}
}
