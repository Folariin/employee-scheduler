package com.farouk.employee_scheduler.service.llm;
import java.time.LocalDate;
/** Provider boundary. Returns untrusted JSON, never scheduling commands or assignments. */
public interface StaffingInterpreter {
    String interpret(LocalDate week, String instructions);
}
