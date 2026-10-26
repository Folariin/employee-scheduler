# AI staffing instructions

The optional backend Groq integration converts manager text into editable staffing requirements. It never receives the employee roster, availability, credentials, or shift records, and cannot create, assign, apply or publish shifts. It sends only the manager's text, the selected week's dates and the interpretation rules/schema. No new dependency, table or migration is needed.

## Configure the backend

Set these environment variables in the backend process (or a local ignored run configuration). Never put the API key in source, a frontend `VITE_*` variable, screenshots or logs.

| Variable | Default | Purpose |
| --- | --- | --- |
| `GROQ_API_KEY` | empty | Backend secret; empty disables interpretation with 503, preserving manual scheduling |
| `GROQ_MODEL` | `openai/gpt-oss-20b` | A Groq model supporting **strict JSON schema** output |
| `GROQ_BASE_URL` | `https://api.groq.com/openai/v1` | Trusted provider base URL; HTTPS required except literal loopback/localhost HTTP for tests |
| `GROQ_CONNECT_TIMEOUT_SECONDS` | 5 | 1–30 seconds |
| `GROQ_TIMEOUT_SECONDS` | 30 | 1–120 seconds, including reading the full response |

Restart the backend after setting variables. No frontend secret or new frontend configuration is needed. The client uses Java 21 HTTP and existing Jackson. Provider replacement is through `StaffingInterpreter`; do not couple provider output to persistence services. The adapter sends `response_format: json_schema` with `strict: true`; there is no automatic fallback to a weaker output format. Model access and availability depend on your Groq account. See [Groq structured outputs](https://console.groq.com/docs/structured-outputs) and [API reference](https://console.groq.com/docs/api-reference).

## Endpoint and response

`POST /api/admin/schedules/{id}/interpret`, with the existing ADMIN JWT:

```json
{"instructions":"I need three employees Monday between 9 AM and 5 PM. Let them work different shifts."}
```

For a week starting 2026-10-26, an interpretation can return:

```json
{
  "requirements":[{"date":"2026-10-26","startTime":"09:00:00","endTime":"17:00:00","requiredEmployees":3,"mode":"FLEXIBLE"}],
  "maxWeeklyHours":null,
  "balanceHoursFairly":null,
  "clarificationQuestions":[],
  "unsupportedConstraints":[],
  "summary":"Three distinct employees working different shifts within Monday's window."
}
```

Weekday resolution is server-controlled relative to the selected Monday–Sunday week. Explicit ISO dates must belong to it. Null policies mean **unspecified**, not zero hours or false fairness. Supporting verbatim evidence for any explicit hour/fairness preference is required from the provider and checked against input; it is not exposed as a scheduling option. Partial/incomplete rows are omitted from usable requirements and produce questions instead of fabricated values. Complete rows are validated using the existing `SchedulingAlgorithm.validate`, including non-overlap and total slots.

Status codes: 200 interpretation/questions/unsupported explanation; 400 blank/oversized/malformed input; 401 missing/invalid authentication; 403 non-ADMIN; 404 missing week; 409 published week; 429 provider rate limit or four provider calls already in flight; 502 invalid model output/provider failure; 503 no configured key; 504 timeout. Provider error bodies and request text are never included in error messages. This operation creates neither shifts nor a generation preview token. A publication racing with interpretation can leave a now-stale interpretation, but subsequent preview/apply retain their existing draft/concurrency checks.

## Examples and meanings

- “Two employees working the **full 9 AM to 5 PM shift Tuesday**”: FIXED; every employee must cover that exact full interval.
- “Three employees Monday 9 AM–5 PM; **let them work different shifts**”: FLEXIBLE; distinct employees may cover different parts of a window, subject to availability and the generation form's duration settings.
- “Two Wednesday 10 AM–6 PM, maximum 35 hours per employee”: the hours cap is explicit, but FIXED versus FLEXIBLE needs clarification.
- “Three employees Monday”: ask for times and intended mode; never infer simultaneous coverage or default times.
- “Three employees continuously on duty”: unsupported. Continuous minimum staffing is not a flexible distinct-worker count. Explicit continuous wording is conservatively rejected before calling Groq, and the provider is instructed to flag equivalent wording.
- Multiple explicit days produce separate requirements. Unknown/missing details require questions. Break rules, skills, locations, payroll, time off, swaps, overnight and continuous staffing are outside this interpreter's supported scheduling contract.

## Manager confirmation

Open **Generate schedule** on a draft and choose **AI instructions**. Enter non-sensitive instructions, click **Interpret instructions**, then review the summary, questions and limitations. Refine text and interpret again or edit/add manual rows to resolve missing details. The form labels the existing 40-hour cap and enabled fairness as **form defaults** when no such policy was requested; flexible duration defaults are also shown. Review/change them explicitly. An interpretation does not automatically configure unrequested policies as AI conclusions.

Acknowledge the requirements, defaults, resolved questions and accepted limitations to enable **Generate preview**. Editing rows or policy controls invalidates that acknowledgment. Review suggestions/shortages and select assignments, click **Review selection**, then explicitly **Apply to draft**. Publishing remains a separate existing action. Manual entry remains available even when Groq is not configured or fails.

## Trust, privacy and operational limits

Model output is untrusted: exact allowed fields/types, duplicate JSON keys/trailing tokens, modes, dates, time precision/order, counts, policy bounds, requirement count and overlaps are checked independently. HTML/code is rendered as ordinary React text; no tool invocation, SQL or employee IDs are accepted from model output. Prompt injection cannot bypass the existing generator/apply validation or obtain persistence capability through interpretation.

Schema validation cannot prove that a model faithfully understood the manager's text. Even supporting quotations do not prove semantic correctness. Managers must inspect every requirement and policy; the conservative continuous-wording guard is not a complete natural-language classifier. Clarifications/limitations remain visible and must be resolved or knowingly accepted before proceeding. Do not use this feature as legal/compliance advice.

Bounds: 4000 instruction characters, 4096 completion tokens, 64 KiB total HTTP response, 32768 output characters, 100 requirements, 50 employees each, 1000 total slots, 1–168 weekly hours, four concurrent provider calls per application instance. Requests are not retried automatically. These bounds limit individual calls, not cumulative spend or multi-instance traffic: configure Groq account spend limits and gateway/user rate limits before broad rollout. Trusted endpoint configuration is an operator responsibility; credentials are sent only to that configured endpoint and redirects are never followed.

Do not include employee names, personal information or secrets in instructions. The application deliberately does not log input/output or provider bodies, and handles invalid-input errors locally to avoid default Spring validation logs containing rejected text. Keep HTTP wire logging and external request-body logging disabled. Review Groq data handling before using organizational information.

## Tests and verification

Backend unit tests mock `StaffingInterpreter`; adapter tests use an ephemeral loopback HTTP server and a test-only key. The PostgreSQL integration fixture installs a primary mock provider and verifies interpretation preserves existing shifts and account/availability/schedule counts/status. No CI call reaches Groq or incurs API charges. Existing CI commands discover the new tests without workflow changes.

Run the existing backend Maven tests with an isolated PostgreSQL `availability_test` database (see backend testing instructions), then `npm run lint`, `npm run typecheck`, `npm test`, and `npm run build` in `frontend/`. Never use a development or production database for destructive integration fixtures. A live provider smoke test was deliberately not performed; verify the selected model/account with non-sensitive sample instructions after configuring your own key and budget.
