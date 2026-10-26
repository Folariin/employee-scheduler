package com.farouk.employee_scheduler.controller;

import com.farouk.employee_scheduler.dto.*;
import com.farouk.employee_scheduler.service.AutomaticSchedulingService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/schedules/{id}")
public class AutomaticSchedulingController {
    private final AutomaticSchedulingService service;
    public AutomaticSchedulingController(AutomaticSchedulingService service){this.service=service;}
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ProblemDetail> concurrentChange() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Concurrent scheduling or availability change; generate and review a new preview"));
    }
    @PostMapping("/generate-preview")
    public GenerationPreview preview(@PathVariable UUID id,@Valid @RequestBody GenerationRequest request,Authentication user) {
        return service.preview(id,request,user.getName());
    }
    @PostMapping("/apply-generated")
    public List<ShiftResponse> apply(@PathVariable UUID id,@Valid @RequestBody ApplyGenerationRequest request,Authentication user) {
        return service.apply(id,request,user.getName());
    }
}
