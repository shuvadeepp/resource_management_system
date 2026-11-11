# RMS-frontend: API changes + Angular prompt

Backend: `http://localhost:8050` (Spring Boot, JWT Bearer, snake_case JSON). Frontend: `D:\JAVA-Projects\RMS-frontend`.

Part 1 is the API reference. Part 2 is a prompt you can paste into an AI coding tool opened on `RMS-frontend`.
Do the work in the 4 phases listed in the prompt, one phase at a time.

---------------------------------------------------------------------------------------------------

# PART 1 - API LIST

## 1.1 Who may call what (use this to show or hide buttons)

The login response gives `role` (may arrive as `ROLE_ADMIN`; strip the `ROLE_` prefix).
The backend enforces this. A wrong role gets **403** `{"message":"You do not have permission to do this."}`.

| Action | Roles |
|---|---|
| Any GET below | USER, ADMIN, PROJECT_MANAGER, LK_ADMIN, RESOURCE_MANAGER, SKILL_REVIEWER |
| Review a learning recommendation (PUT review) | ADMIN, LK_ADMIN, SKILL_REVIEWER  **(was open to everyone before)** |
| Generate learning recommendations (POST generate) | USER, ADMIN, PROJECT_MANAGER, LK_ADMIN, SKILL_REVIEWER |
| Create / edit / approve catalog resources | ADMIN, LK_ADMIN |
| Record evidence | ADMIN, LK_ADMIN, SKILL_REVIEWER, RESOURCE_MANAGER |
| Rematch | USER, ADMIN, PROJECT_MANAGER, RESOURCE_MANAGER, LK_ADMIN |
| Backfill old runs (no UI needed) | ADMIN |

## 1.2 Summary

| # | Method + path | Status | UI impact |
|---|---|---|---|
| 1 | `GET /api/requirements/{id}/skill-gaps` | CHANGED (new fields + `?runId=`) | Skill Growth page |
| 2 | `GET /api/matching-results/{id}/skill-gaps` | CHANGED (new fields) | Candidate page |
| 3 | `POST /api/matching-results/{id}/learning-recommendations/generate` | CHANGED (catalog first, longer `note`) | Generate button |
| 4 | `PUT /api/learning-recommendations/{id}/review` | CHANGED (roles, more fields, IN_PROGRESS/COMPLETED) | Review buttons |
| 5 | `GET /api/requirements/{id}/matches` | CHANGED (new fields per match) | Matches / candidate list |
| 6 | `GET /api/matching-runs/{id}` | CHANGED (3 new fields) | Run polling |
| 7 | `GET /api/learning-resources` | NEW | Catalog page |
| 8 | `GET /api/learning-resources/{id}` | NEW | Catalog page |
| 9 | `POST /api/learning-resources` | NEW | Catalog page |
| 10 | `PUT /api/learning-resources/{id}` | NEW | Catalog page |
| 11 | `PUT /api/learning-resources/{id}/approval` | NEW | Catalog page |
| 12 | `GET /api/skill-gaps/{id}/resources` | NEW | Gap row: "approved resources" |
| 13 | `GET /api/skill-gaps` | NEW | Gap list/filter |
| 14 | `GET /api/skill-gaps/summary` | NEW | Dashboard cards |
| 15 | `GET /api/matching-results/{id}/learning-pathway` | NEW | Pathway view |
| 16 | `GET /api/employees/{id}/learning-pathway?requirementId=` | NEW | Pathway view |
| 17 | `POST /api/learning-recommendations/{id}/evidence` | NEW | "Record evidence" form |
| 18 | `GET /api/learning-recommendations/{id}/evidence` | NEW | Evidence list |
| 19 | `POST /api/requirements/{id}/rematch` | NEW | "Rematch" button |
| 20 | `GET /api/matching-runs/{id}/compare` | NEW | Before/after page |
| 21 | `POST /api/admin/skill-gaps/backfill` | NEW (admin tool) | none (use Postman) |

All changes to existing endpoints only ADD fields. Nothing was renamed or removed, so the current UI keeps working.

## 1.3 Shared value lists

- `priority`: `CRITICAL | HIGH | MEDIUM | LOW`  (sort in this order; CRITICAL first)
- `gap_type`: `MISSING | BELOW_REQUIRED`
- `recommendation_source`: `LK_REPOSITORY | AI_GENERATED | RULE_BASED | MANUAL`
  - `LK_REPOSITORY` = from the approved L&K catalog. `AI_GENERATED` = written by AI. `RULE_BASED` = fixed template text (AI was off, failed, or gave nothing). `MANUAL` is reserved.
  - FRD rule: AI-written items must be reviewed by L&K, so **always show the source badge**.
- `resource_type`: `COURSE | CERTIFICATION | DOCUMENTATION | MENTORING | PRACTICAL_ASSIGNMENT | OTHER | ASSESSMENT`  (ASSESSMENT is new)
- `validation_method`: `KNOWLEDGE_ASSESSMENT | PRACTICAL_ASSIGNMENT | DEMONSTRATION | TECHNICAL_REVIEW | CERTIFICATE | NONE`
- `review_status`: `PENDING | APPROVED | REJECTED | IN_PROGRESS | COMPLETED`
- `run_trigger`: `NEW_SEARCH | REMATCH_AFTER_LEARNING | MANUAL_REMATCH`
- `evidence_type`: `ASSESSMENT | CERTIFICATION | PROJECT_EXPERIENCE | PRACTICAL_ASSIGNMENT | OTHER`
- `gaps_source`: `STORED` (saved when the run finished) | `LIVE_LEGACY` (old run, worked out from today's skills)

## 1.4 Error shapes (already handled by `toApiError`, which reads `message`)

- 400 validation: `{"status":"error","message":"Validation failed","errors":{"resource_title":"resource_title is required"}}`
- 400/404/409/503: `{"status":"error","message":"..."}`
- 403: `{"message":"You do not have permission to do this."}`
- 401: `{"message":"You are not authorized. Please login first."}`

---------------------------------------------------------------------------------------------------

## 1.5 CHANGED endpoints (details)

### 1) GET `/api/requirements/{requirementId}/skill-gaps`   (Skill Growth page)
Query: `runId` (optional). Default = latest finished run. Use it to view the run before a rematch.
Response: array, one item per candidate in that run, best rank first:
```json
[{
  "matching_result_id": 412,
  "matching_run_id": 70,
  "employee_id": 8,
  "employee_code": "E1008",
  "resource_name": "Asha Rao",
  "match_category": "GOOD",
  "gaps_source": "STORED",
  "gaps": [{
    "skill_gap_id": 901, "skill_id": 12, "skill_name": "Docker",
    "gap_type": "MISSING", "required_level": 3, "current_level": null, "gap_size": 3,
    "mandatory": true, "priority": "CRITICAL",
    "priority_reason": "Mandatory skill is missing; raised one level because the requirement priority is HIGH",
    "gap_description": "Missing mandatory skill Docker (needs level 3)"
  }],
  "learning_recommendations": [ /* see "Recommendation object" below */ ]
}]
```
- `gaps` is already sorted: most urgent first, then bigger gap, then name. Keep that order.
- `current_level` is `null` when the skill is missing. The current `gapLabel()` uses `!g.current_level`, which still works.
- NEW fields on a gap: `skill_gap_id, skill_id, gap_type, gap_size, priority, priority_reason`.
- NEW fields on the item: `matching_run_id, gaps_source`.

### 2) GET `/api/matching-results/{matchingResultId}/skill-gaps`   (Candidate page)
Same shape as ONE item of endpoint 1.

### 3) POST `/api/matching-results/{matchingResultId}/learning-recommendations/generate`
No body. Always 200. Same shape as endpoint 2 plus `note`.
- Gaps that have an approved L&K resource get it directly (no AI call). The AI is used only for the rest.
- `note` examples:
  - `"Generated 3 learning recommendation(s): 2 from the approved L&K repository, 1 written by AI, 0 from the rule-based template. AI and template suggestions must be reviewed by L&K against approved learning resources."`
  - `"AI is turned off (...); no recommendations generated for 2 gap(s) that have no approved L&K resource"`
  - `"Every gap already has a learning recommendation"` / `"No skill gaps for this match"`
- The UI should show `note` in a toast (already done) and reload the match.

### 4) PUT `/api/learning-recommendations/{id}/review`
Body: `{"review_status":"APPROVED","review_comments":"..."}`  (`review_status` one of APPROVED, REJECTED, IN_PROGRESS, COMPLETED).
Response: the recommendation object plus `matching_result_id, reviewed_by, reviewed_at`.
- **Roles changed** (see 1.1). Hide the buttons for other roles, and show a clear toast on 403.
- `started_at` is set the first time it becomes IN_PROGRESS or COMPLETED. `completed_at` is set on COMPLETED.
- Suggested UI: for APPROVED items show a **Start learning** button (sends IN_PROGRESS). Completion normally happens by recording evidence (endpoint 17), which marks it COMPLETED automatically.

### Recommendation object (used inside endpoints 1, 2, 3, 4, 5, 15)
```json
{
  "learning_recommendation_id": 77,
  "skill_name": "Docker",
  "current_proficiency": null,
  "target_proficiency": 3,
  "learning_action": "Complete the approved L&K learning resource: Docker containerization exercises",
  "learning_resource": "https://lms.example.com/docker",
  "resource_type": "PRACTICAL_ASSIGNMENT",
  "review_status": "PENDING",
  "review_comments": null,
  "skill_gap_id": 901,
  "learning_resource_id": 5,
  "recommendation_source": "LK_REPOSITORY",
  "priority": "CRITICAL",
  "sequence_order": 1,
  "estimated_hours": 10.0,
  "estimated_weeks": 2,
  "validation_method": "DEMONSTRATION",
  "started_at": null,
  "completed_at": null
}
```
New fields: `skill_gap_id, learning_resource_id, recommendation_source, priority, sequence_order, estimated_hours, estimated_weeks, validation_method, started_at, completed_at`.
`estimated_hours/weeks` are `null` unless the item comes from the L&K catalog (we do not guess hours for AI text). `learning_resource_id` and `skill_gap_id` can be `null` (old data).

### 5) GET `/api/requirements/{requirementId}/matches`
Each match gets: `run_trigger`, `previous_run_id`, and `gap_details` (same objects as `gaps` above, may be `[]` for old runs).
The old `skill_gaps` JSON stays. `learning_suggestions` items now use the Recommendation object above.

### 6) GET `/api/matching-runs/{id}`
New: `run_trigger`, `previous_run_id`, `rematch_note`. Use them to label a run "Rematch after learning (replaces run 70)".

---------------------------------------------------------------------------------------------------

## 1.6 NEW endpoints (details)

### 7) GET `/api/learning-resources`   (L&K catalog list)
Query (all optional): `skillId`, `approved` (true/false), `active` (true/false), `q` (text in title or provider).
Response: array of resource objects (below), sorted by skill then title.

Resource object:
```json
{
  "learning_resource_id": 5, "skill_id": 12, "skill_name": "Docker",
  "resource_title": "Docker containerization exercises", "resource_type": "PRACTICAL_ASSIGNMENT",
  "provider": "L&K Training Repository", "resource_url": null, "resource_description": null,
  "from_proficiency": 1, "to_proficiency": 3, "estimated_hours": 10.0, "estimated_weeks": 2,
  "validation_method": "DEMONSTRATION",
  "is_approved": true, "approved_by": 3, "approved_at": "2026-11-10T14:30:00",
  "is_active": true, "created_by": 3, "created_at": "...", "updated_at": "..."
}
```
`estimated_weeks` is calculated by the backend (hours / 5 per week, rounded up). Do not calculate it in the UI.

### 8) GET `/api/learning-resources/{id}`  -> one resource object. 404 if missing.

### 9) POST `/api/learning-resources`  (ADMIN, LK_ADMIN)  -> 201 + resource object
```json
{
  "skill_id": 12,
  "resource_title": "Docker containerization exercises",
  "resource_type": "PRACTICAL_ASSIGNMENT",
  "provider": "L&K Training Repository",
  "resource_url": "https://...",
  "resource_description": "optional",
  "from_proficiency": 1,
  "to_proficiency": 3,
  "estimated_hours": 10,
  "validation_method": "DEMONSTRATION",
  "is_active": true
}
```
Required: `skill_id`, `resource_title`, `to_proficiency`. Defaults: `resource_type` COURSE, `from_proficiency` 1, `validation_method` KNOWLEDGE_ASSESSMENT, `is_active` true.
Rules (400 if broken): levels 1-5, `from_proficiency` < `to_proficiency`, `estimated_hours` > 0, valid enum values. 409 if the same skill already has that title.
**A new resource is always NOT approved.** Show it as "Awaiting approval" until endpoint 11 is called.

### 10) PUT `/api/learning-resources/{id}`  (ADMIN, LK_ADMIN)  -> resource object
Same body as 9 (full replace; `skill_id` and `resource_title` always required).
If an approved resource's content changes, approval is removed and the response has `"note": "...L&K must approve it again."`. Show that note.

### 11) PUT `/api/learning-resources/{id}/approval`  (ADMIN, LK_ADMIN)  -> resource object
Body: `{"approved": true}` or `{"approved": false}`. Sets or clears `approved_by` / `approved_at`.
To retire a resource without deleting it, use PUT (10) with `"is_active": false`.

### 12) GET `/api/skill-gaps/{skillGapId}/resources`
Approved + active catalog items that fit this gap (cheapest hours first).
```json
{"skill_gap_id":901,"skill_id":12,"skill_name":"Docker","gap_type":"MISSING","current_level":null,"required_level":3,
 "resources":[ /* resource objects */ ],
 "note":"No approved and active L&K resource fits this gap. ..."}   // note only when resources is empty
```
Use it for a "See approved resources" expander on a gap row.

### 13) GET `/api/skill-gaps`   (filterable gap list)
Query (all optional): `requirementId`, `runId`, `employeeId`, `priority`, `gapType`, `mandatory` (true/false), `skillId`, `hasRecommendation` (true/false), `limit` (1-1000, default 200).
Scope: `requirementId` = its latest finished run (or `runId`); only `runId` = that run; nothing = latest finished run of every requirement.
```json
{"total": 37, "returned": 37, "gaps": [{
  "skill_gap_id": 901, "matching_result_id": 412, "matching_run_id": 70, "requirement_id": 9,
  "employee_id": 8, "employee_code": "E1008", "resource_name": "Asha Rao",
  "skill_id": 12, "skill_name": "Docker", "gap_type": "MISSING", "mandatory": true,
  "required_level": 3, "current_level": null, "gap_size": 3,
  "priority": "CRITICAL", "priority_reason": "...",
  "learning_recommendation_count": 1, "recommendation_sources": ["LK_REPOSITORY"]
}]}
```
Sorted most urgent first. `total` can be larger than `returned` when `limit` cuts the list.

### 14) GET `/api/skill-gaps/summary`   (dashboard cards)
Same filters as 13 (no `limit`), plus `topN` (default 10).
```json
{
  "runs_covered": [70, 71], "total_gaps": 37, "employees_with_gaps": 12, "mandatory_gaps": 15,
  "gaps_without_recommendation": 9,
  "by_priority": {"CRITICAL": 5, "HIGH": 10, "MEDIUM": 12, "LOW": 10},
  "by_gap_type": {"MISSING": 20, "BELOW_REQUIRED": 17},
  "most_common_gap_skills": [{"skill_id":12,"skill_name":"Docker","gap_count":6,"missing_count":4,
      "below_required_count":2,"mandatory_count":3,"employees_affected":5}]
}
```

### 15) GET `/api/matching-results/{matchingResultId}/learning-pathway`
### 16) GET `/api/employees/{employeeId}/learning-pathway?requirementId=9[&runId=70]`   (`requirementId` is required)
```json
{
  "matching_result_id": 412, "matching_run_id": 70, "requirement_id": 9, "requirement_role": "Backend Developer",
  "employee_id": 8, "employee_code": "E1008", "resource_name": "Asha Rao", "match_category": "GOOD",
  "steps": [{ "step": 1, /* all Recommendation object fields */ "gap_type": "MISSING", "gap_size": 3, "mandatory": true }],
  "totals": {
    "steps": 3, "steps_counted": 2, "rejected_steps": 1, "estimated_steps": 2, "unestimated_steps": 0,
    "total_estimated_hours": 18.0, "total_estimated_weeks": 4,
    "awaiting_lk_review": 2, "ai_generated_steps": 1,
    "by_review_status": {"PENDING": 2, "REJECTED": 1}, "by_source": {"AI_GENERATED": 1, "LK_REPOSITORY": 2}
  },
  "notes": ["Hours and weeks are known only for steps from the L&K repository; 1 step(s) have no estimate yet, ..."]
}
```
- `steps` are ordered: priority (CRITICAL first), then `sequence_order`. `step` is 1..n for display.
- Totals skip REJECTED steps. Weeks are summed as if steps are done one after another.
- `notes` is present only when something needs saying. Show each note as a small info line.
- 404 if the employee is not in that run.

### 17) POST `/api/learning-recommendations/{id}/evidence`   (ADMIN, LK_ADMIN, SKILL_REVIEWER, RESOURCE_MANAGER) -> 201
```json
{
  "evidence_type": "ASSESSMENT",
  "evidence_title": "Docker practical assessment",
  "evidence_description": "optional",
  "score": 82, "maximum_score": 100,
  "evidence_date": "2026-11-10",
  "valid_until": "2027-11-10",
  "file_reference": "optional link or file id",
  "new_proficiency": 3,
  "mark_completed": true
}
```
Required: `evidence_type`, `evidence_title`. Everything else optional.
Rules (400): `score` needs `maximum_score` and cannot exceed it; `evidence_date` cannot be in the future; `valid_until` not before `evidence_date`; `new_proficiency` 1-5.
- `new_proficiency` updates the employee's rating. If the employee has no rating for the skill, it is created. Without `new_proficiency`, and with no existing rating, you get 400: "send new_proficiency (1-5) to create it".
- `mark_completed` (default true) sets the recommendation to COMPLETED.
- 409 if the recommendation was REJECTED.
Response:
```json
{
  "evidence": { "evidence_id": 4, "verification_status": "VERIFIED", "...": "..." },
  "employee_skill": { "employee_skill_id": 55, "employee_id": 8, "skill_name": "Docker", "created": true, "changed": true,
      "old": {"proficiency": null, "validation_status": null, "information_source": null},
      "new": {"proficiency": 3, "validation_status": "ASSESSMENT_VERIFIED", "information_source": "ASSESSMENT"} },
  "learning_recommendation": { "learning_recommendation_id": 77, "review_status": "COMPLETED", "completed_at": "..." },
  "next_step": "Run POST /api/requirements/9/rematch to score again with the updated skill. ..."
}
```
After success, show old -> new rating and offer a **Rematch now** button.

### 18) GET `/api/learning-recommendations/{id}/evidence`  -> array of evidence objects (newest first).

### 19) POST `/api/requirements/{requirementId}/rematch`   (see roles) 
Query: `async` (default **true**). Body optional:
```json
{"run_trigger":"REMATCH_AFTER_LEARNING","previous_run_id":70,"rematch_note":"After Docker training","designation":null,"department":null}
```
- Default `run_trigger` = REMATCH_AFTER_LEARNING. `previous_run_id` default = latest finished run.
- Async -> **202**: `{"status":"accepted","run_status":"QUEUED","requirement_id":9,"matching_run_id":71,"previous_run_id":70,"run_trigger":"REMATCH_AFTER_LEARNING","employees_sent":14,"message":"..."}`
  Then poll `GET /api/matching-runs/71` every 2 seconds (same as the existing scan console) until `run_status` is COMPLETED, COMPLETED_WITH_ERRORS or FAILED.
- Sync (`?async=false`) -> **201** with `run_status` and `result_count`. Can take up to a minute, so prefer async.
- 409 if the requirement has no finished run, or a run is still running. 503 if the server is busy.
- Old results are NOT changed. The new run is a separate run.

### 20) GET `/api/matching-runs/{newRunId}/compare`   (before/after)
Query: `withRunId` (optional; default is the run's `previous_run_id`; must be a run of the same requirement). 400 if there is no previous run.
```json
{
  "requirement_id": 9,
  "new_run": {"matching_run_id":71,"run_trigger":"REMATCH_AFTER_LEARNING","run_status":"COMPLETED","execution_date":"...","completed_at":"...","previous_run_id":70,"rematch_note":"After Docker training"},
  "old_run": { "...": "same shape" },
  "summary": {"employees_compared":14,"improved":4,"declined":1,"unchanged":9,"average_score_change":2.35,
              "gaps_closed":5,"new_gaps":1,"only_in_new_run":0,"only_in_old_run":1},
  "employees": [{
    "employee_id": 8, "employee_code": "E1008", "resource_name": "Asha Rao", "only_in": null,
    "old_matching_result_id": 412, "new_matching_result_id": 431,
    "old_score": 61.5, "new_score": 78.0, "score_change": 16.5,
    "old_category": "PARTIAL", "new_category": "GOOD", "category_changed": true,
    "old_rank": 5, "new_rank": 2, "rank_change": 3,
    "gaps_comparable": true,
    "gap_changes": [{"skill_id":12,"skill_name":"Docker","status":"CLOSED","mandatory":true,"required_level":3,
        "old_gap_type":"MISSING","old_level":null,"new_gap_type":null,"new_level":null,
        "old_priority":"CRITICAL","new_priority":null}]
  }]
}
```
- `employees` is sorted by `score_change`, biggest improvement first.
- `rank_change` > 0 means the person moved UP (better rank). `only_in` is `NEW_RUN` / `OLD_RUN` when a person is in just one run (no score_change then).
- `gap_changes[].status`: `CLOSED` (gap gone), `IMPROVED`, `UNCHANGED`, `WORSENED`, `NEW`.
  `old_gap_type`/`new_gap_type` null = that run had no gap for the skill. `*_level` null with `MISSING` = skill not held.
- `gaps_comparable: false` -> scores still compare, but gap details are unavailable (old run has no saved gaps). Show "Gap details not available for the older run."

### 21) POST `/api/admin/skill-gaps/backfill`  (ADMIN)  - admin tool, call from Postman, no UI needed
Query: `dryRun` (default **true**), `runId` or `requirementId` (optional). `dryRun=true` only counts.

---------------------------------------------------------------------------------------------------

# PART 2 - PROMPT FOR THE ANGULAR PROJECT (copy everything between the lines)

======================== COPY FROM HERE ========================

You are changing an existing Angular app: `D:\JAVA-Projects\RMS-frontend` (Resource Management System UI).
The backend (Spring Boot, `http://localhost:8050`) added Module 5 features: stored skill gaps with priority,
an L&K learning-resource catalog, learning pathways with hours and weeks, skill evidence, rematch and
before/after comparison. Existing endpoints only gained fields, so nothing breaks. Do the work in phases.
**Finish and show me Phase 1 before starting Phase 2**, and so on.

## Conventions you MUST follow (this is how the project is built today)
- Standalone components, `ChangeDetectionStrategy.OnPush`, signals (`signal`, `computed`, `effect`, `input()`, `output()`), `inject()`. New control flow (`@if`, `@for`). Follow the style of `pages/skill-growth/skill-growth.component.ts` and `shared/learning-list.component.ts`.
- All HTTP goes through `core/api.service.ts` (`get/post/put` with relative paths and optional query object). Do not use `HttpClient` directly. `ApiService.put` has no query parameter; add one only if needed.
- Services live in `src/app/services`, models in `src/app/models/requirement.model.ts` (or a new model file next to it), shared pieces in `src/app/shared`.
- Errors: `toApiError(err).title` from `core/error-message.ts` (it reads the backend `message`), shown with `ToastService` or `<app-error-box>`.
- Backend JSON is snake_case. Keep snake_case property names in the TypeScript interfaces, as the existing models do.
- Auth: `services/auth.service.ts` exposes `user().role` and only `isAdmin`. The role can arrive as `ROLE_ADMIN`; strip `ROLE_`. Add a helper `hasAnyRole(...roles: string[])` (computed-friendly) there and use it to show or hide buttons. The backend enforces roles and answers 403 `{"message":"You do not have permission to do this."}`, so the UI must hide forbidden actions AND handle a 403 with a clear toast.
- Reuse existing look: `.card`, `.pill`, `.chip`, `.btn`, `.state`, `humanise()` and `statusPill()` from `shared/labels`. Do not add a UI library.
- In `core/error-message.ts` the "Cannot reach the server" text says port 8090; the backend is on 8050. Fix the text.

## Role rules (hide the control when the role is missing)
- Review learning item (Approve / Reject / Start): ADMIN, LK_ADMIN, SKILL_REVIEWER. (Before, everyone could. Now others get 403.)
- Generate learning plan: USER, ADMIN, PROJECT_MANAGER, LK_ADMIN, SKILL_REVIEWER.
- Create / edit / approve catalog resources: ADMIN, LK_ADMIN.
- Record evidence: ADMIN, LK_ADMIN, SKILL_REVIEWER, RESOURCE_MANAGER.
- Rematch: USER, ADMIN, PROJECT_MANAGER, RESOURCE_MANAGER, LK_ADMIN.
- Every GET below works for USER, ADMIN, PROJECT_MANAGER, LK_ADMIN, RESOURCE_MANAGER, SKILL_REVIEWER.

## Value lists (put these in the models file as union types and in `shared/labels` for display)
- `GapPriority = 'CRITICAL'|'HIGH'|'MEDIUM'|'LOW'` (order CRITICAL first). Pill colours: CRITICAL red, HIGH orange, MEDIUM amber, LOW grey.
- `GapType = 'MISSING'|'BELOW_REQUIRED'` (labels: "Missing", "Below required level").
- `RecommendationSource = 'LK_REPOSITORY'|'AI_GENERATED'|'RULE_BASED'|'MANUAL'`. Labels: "L&K approved", "AI suggestion", "Standard template", "Manual". **Always show this badge on every learning item**, with a tooltip for AI_GENERATED: "Written by AI. L&K must review it against approved learning resources."
- `ResourceType` add `'ASSESSMENT'`. `ValidationMethod = 'KNOWLEDGE_ASSESSMENT'|'PRACTICAL_ASSIGNMENT'|'DEMONSTRATION'|'TECHNICAL_REVIEW'|'CERTIFICATE'|'NONE'`.
- `RunTrigger = 'NEW_SEARCH'|'REMATCH_AFTER_LEARNING'|'MANUAL_REMATCH'`. `EvidenceType = 'ASSESSMENT'|'CERTIFICATION'|'PROJECT_EXPERIENCE'|'PRACTICAL_ASSIGNMENT'|'OTHER'`.

## PHASE 1 - Update models and the existing screens (no new pages)

### 1A. Models (`models/requirement.model.ts`)
Replace/extend these interfaces (keep existing fields, all new ones optional/nullable so old data still type-checks):
```ts
export interface SkillGap {
  skill_gap_id?: number | null;          // null for old runs shown from live data
  skill_id?: number | null;
  skill_name: string;
  gap_type?: GapType | null;
  required_level: number | null;
  current_level: number | null;          // null = skill missing
  gap_size?: number | null;
  mandatory: boolean;
  priority?: GapPriority | null;
  priority_reason?: string | null;
  gap_description?: string | null;
}
export interface LearningRecommendation {
  learning_recommendation_id: number;
  skill_name: string;
  current_proficiency?: number | null;
  target_proficiency?: number | null;
  learning_action?: string | null;
  learning_resource?: string | null;
  resource_type?: ResourceType | string | null;
  review_status: ReviewStatus | string;
  review_comments?: string | null;
  skill_gap_id?: number | null;
  learning_resource_id?: number | null;
  recommendation_source?: RecommendationSource | null;
  priority?: GapPriority | null;
  sequence_order?: number | null;
  estimated_hours?: number | null;       // only for L&K catalog items
  estimated_weeks?: number | null;
  validation_method?: ValidationMethod | null;
  started_at?: string | null;
  completed_at?: string | null;
}
export interface SkillGapReport {
  matching_result_id: number;
  matching_run_id?: number;
  employee_id?: number;
  employee_code: string;
  resource_name: string;
  match_category?: string;
  gaps_source?: 'STORED' | 'LIVE_LEGACY';
  gaps: SkillGap[];
  learning_recommendations: LearningRecommendation[];
  note?: string | null;
}
export interface MatchingRun {  // add to the existing interface
  run_trigger?: RunTrigger | null;
  previous_run_id?: number | null;
  rematch_note?: string | null;
}
// MatchResult (existing) add:  run_trigger?: RunTrigger|null; previous_run_id?: number|null; gap_details?: SkillGap[];
// ReviewRequest.review_status stays: 'APPROVED'|'REJECTED'|'IN_PROGRESS'|'COMPLETED'
```

### 1B. `services/requirement.service.ts`
`getSkillGaps(requirementId: number, runId?: number | null)` -> `GET /api/requirements/{id}/skill-gaps` with query `{ runId }`.
(`ApiService` already drops empty query values.)

### 1C. `shared/learning-list.component.ts` (each learning item)
- Show the **source badge** (`recommendation_source`) next to the resource-type pill. Show a priority pill (`priority`).
- Show extra info when present: "~{{estimated_hours}} h, about {{estimated_weeks}} week(s)", "Validation: {{humanise(validation_method)}}", and "Step {{sequence_order}}".
- If `recommendation_source === 'LK_REPOSITORY'` and `learning_resource` looks like a URL, render it as a link (`target="_blank" rel="noopener"`). Otherwise show text as now.
- Show `started_at` / `completed_at` as small dates when present.
- Order items by priority (CRITICAL first), then `sequence_order`, using a `computed` sorted list.
- Buttons by role and status:
  - `PENDING`: Approve, Reject (comment required for Reject) - only if `hasAnyRole('ADMIN','LK_ADMIN','SKILL_REVIEWER')`.
  - `APPROVED`: new **Start learning** button -> `review(id, {review_status:'IN_PROGRESS', review_comments:''})`, same role rule.
  - `IN_PROGRESS`: no review button; show "In progress since <date>" and (Phase 4) a "Record evidence" button.
  - `COMPLETED` / `REJECTED`: no buttons.
- When the user lacks the role, show no buttons (not disabled buttons).
- On a 403 from review, toast "Only L&K admins and skill reviewers can review learning plans."

### 1D. `pages/skill-growth/skill-growth.component.ts`
- Gap chips: show priority colour, and for `gap_type` MISSING "missing, needs N", for BELOW_REQUIRED "have X of N". Put `priority_reason` in the chip `title` tooltip. Keep the backend order (already sorted).
- Add a **Run** selector next to the Project selector: list the runs of the chosen requirement. If a run list endpoint is not available, add an input "Run id" only shown after a rematch (Phase 4) and pass it as `runId`. For now: add the optional `runId` query param (`?runId=`) read from the route and pass it to `getSkillGaps`.
- If a report has `gaps_source === 'LIVE_LEGACY'`, show a small info line under the name: "Older run: gaps are worked out from current skills." 
- The "Generate with AI" button: rename to "Generate learning plan" (it now uses the approved L&K catalog first). Show it only when `hasAnyRole('USER','ADMIN','PROJECT_MANAGER','LK_ADMIN','SKILL_REVIEWER')`. Keep showing `res.note` in a toast. Also show the button when SOME gaps have no recommendation, not only when there are zero recommendations: a gap is uncovered when no recommendation has the same `skill_gap_id` (or same `skill_name` for old data).
- Add small filters above the cards: priority (All/Critical/High/Medium/Low) and "Mandatory only". Filter on the client (data is already loaded).

### 1E. `pages/candidate/candidate.component.ts/.html`
Same gap chips and learning list changes as 1C/1D (it already uses `<app-learning-list>`). Show the run label if available: "Run {{matching_run_id}}".

### 1F. Run label helper
Wherever a run is shown (scan console, results header), if `run_trigger` is `REMATCH_AFTER_LEARNING` or `MANUAL_REMATCH` show a pill "Rematch" and "replaces run {{previous_run_id}}" (use `rematch_note` as tooltip).

## PHASE 2 - L&K catalog page (new)

New service `services/learning-catalog.service.ts` (providedIn root, uses `ApiService`):
- `list(filters: {skillId?, approved?, active?, q?})` -> `GET /api/learning-resources`
- `get(id)` -> `GET /api/learning-resources/{id}`
- `create(body: LearningResourceRequest)` -> `POST /api/learning-resources` (201)
- `update(id, body)` -> `PUT /api/learning-resources/{id}`
- `setApproval(id, approved: boolean)` -> `PUT /api/learning-resources/{id}/approval` body `{approved}`
- `resourcesForGap(skillGapId)` -> `GET /api/skill-gaps/{id}/resources`

Models:
```ts
export interface LearningResource {
  learning_resource_id: number; skill_id: number; skill_name: string | null;
  resource_title: string; resource_type: ResourceType; provider: string | null;
  resource_url: string | null; resource_description: string | null;
  from_proficiency: number; to_proficiency: number;
  estimated_hours: number | null; estimated_weeks: number | null;
  validation_method: ValidationMethod;
  is_approved: boolean; approved_by: number | null; approved_at: string | null;
  is_active: boolean; created_by: number | null; created_at: string | null; updated_at: string | null;
  note?: string;                       // present on PUT when approval was removed
}
export interface LearningResourceRequest {
  skill_id: number; resource_title: string; resource_type?: ResourceType; provider?: string | null;
  resource_url?: string | null; resource_description?: string | null;
  from_proficiency?: number; to_proficiency: number; estimated_hours?: number | null;
  validation_method?: ValidationMethod; is_active?: boolean;
}
export interface GapResources {
  skill_gap_id: number; skill_id: number; skill_name: string | null; gap_type: GapType;
  current_level: number | null; required_level: number; resources: LearningResource[]; note?: string;
}
```
New page `pages/learning-catalog/learning-catalog.component.ts` at route `learning-catalog` (add to `app.routes.ts` and to the nav in `layout/shell.component.ts`; show the nav item to ADMIN, LK_ADMIN, SKILL_REVIEWER, PROJECT_MANAGER, RESOURCE_MANAGER):
- Table/cards of resources with filters: skill (use `LookupService` skills list), Approved (All/Yes/No), Active (All/Yes/No), text search `q`.
- Columns: skill, title, type, provider, levels "from -> to", hours (and weeks), validation method, status pills ("Approved" green / "Awaiting approval" amber, "Inactive" grey).
- ADMIN / LK_ADMIN only: **Add resource** button, **Edit**, **Approve / Remove approval**, **Deactivate / Activate** (PUT with `is_active`). Other roles see a read-only list.
- Add/Edit form (reactive or template-driven like the other forms in the project): skill (select), title, type (select), provider, URL, description, from level (1-5), to level (1-5), estimated hours, validation method (select), active. Client checks: title required, `from < to`, hours > 0. Show backend `errors` map (400) under the matching fields and the `message` for 409 ("already has a resource with this title").
- After creating, show a toast "Created. It must be approved before it is used in recommendations."
- After editing an approved resource, if the response has `note`, show it in a toast (approval was removed).
- In Skill Growth (Phase 1D cards), add a small "Approved resources" expander per gap that calls `resourcesForGap(skill_gap_id)` only when opened (skip when `skill_gap_id` is null). Show the list, or `note` when empty.

## PHASE 3 - Pathway view and gap summary

Models:
```ts
export interface PathwayStep extends LearningRecommendation {
  step: number; gap_type: GapType | null; gap_size: number | null; mandatory: boolean | null;
}
export interface LearningPathway {
  matching_result_id: number; matching_run_id: number; requirement_id: number; requirement_role: string | null;
  employee_id: number; employee_code: string; resource_name: string; match_category: string;
  steps: PathwayStep[];
  totals: {
    steps: number; steps_counted: number; rejected_steps: number; estimated_steps: number; unestimated_steps: number;
    total_estimated_hours: number; total_estimated_weeks: number; awaiting_lk_review: number; ai_generated_steps: number;
    by_review_status: Record<string, number>; by_source: Record<string, number>;
  };
  notes?: string[];
}
export interface GapListItem {
  skill_gap_id: number; matching_result_id: number; matching_run_id: number; requirement_id: number;
  employee_id: number; employee_code: string; resource_name: string; skill_id: number; skill_name: string;
  gap_type: GapType; mandatory: boolean; required_level: number; current_level: number | null; gap_size: number;
  priority: GapPriority; priority_reason: string | null;
  learning_recommendation_count: number; recommendation_sources: RecommendationSource[];
}
export interface GapList { total: number; returned: number; gaps: GapListItem[]; }
export interface GapSummary {
  runs_covered: number[]; total_gaps: number; employees_with_gaps: number; mandatory_gaps: number;
  gaps_without_recommendation: number;
  by_priority: Record<GapPriority, number>; by_gap_type: Record<GapType, number>;
  most_common_gap_skills: { skill_id: number; skill_name: string; gap_count: number; missing_count: number;
    below_required_count: number; mandatory_count: number; employees_affected: number; }[];
}
```
Service methods (in `learning.service.ts`):
- `getPathway(matchingResultId)` -> `GET /api/matching-results/{id}/learning-pathway`
- `getEmployeePathway(employeeId, requirementId, runId?)` -> `GET /api/employees/{id}/learning-pathway` query `{requirementId, runId}`
- `listGaps(filters)` -> `GET /api/skill-gaps` (query: requirementId, runId, employeeId, priority, gapType, mandatory, skillId, hasRecommendation, limit)
- `gapSummary(filters)` -> `GET /api/skill-gaps/summary` (same filters + topN)

UI:
- On the Candidate page and each Skill Growth card add a **Learning pathway** section/tab: numbered steps (use `step`), each with skill, priority pill, source badge, status pill, hours/weeks (or "Estimate not available"), validation method. Above the steps show totals: "{{total_estimated_hours}} h, about {{total_estimated_weeks}} weeks (steps done one after another)", "{{awaiting_lk_review}} waiting for L&K review", "{{ai_generated_steps}} written by AI". Show every string in `notes` as an info line. Rejected steps appear greyed with a "Rejected" pill and are NOT in the totals.
- Home / Reports page: add cards from `gapSummary()` (no filters = whole workforce, latest run of each requirement): total gaps, employees with gaps, mandatory gaps, gaps without recommendation, a four-bar chart/list of `by_priority`, and a "Most common gaps" list from `most_common_gap_skills` (skill, gap_count, employees_affected). Match how the existing dashboard cards/charts are built; do not add a chart library.
- Skill Growth: add a toggle "List view" using `listGaps({requirementId, ...filters})` as a sortable table (priority, employee, skill, type, levels, recommendations count + source badges). Use server filters `priority`, `gapType`, `mandatory`, `hasRecommendation`.

## PHASE 4 - Evidence, rematch and before/after

Models:
```ts
export interface EvidenceRequest {
  evidence_type: EvidenceType; evidence_title: string; evidence_description?: string | null;
  score?: number | null; maximum_score?: number | null; evidence_date?: string | null; // yyyy-MM-dd
  valid_until?: string | null; file_reference?: string | null; new_proficiency?: number | null; mark_completed?: boolean;
}
export interface EvidenceResponse {
  evidence: { evidence_id: number; evidence_type: EvidenceType; evidence_title: string; verification_status: string; [k: string]: unknown };
  employee_skill: { employee_skill_id: number; employee_id: number; skill_name: string | null; created: boolean; changed: boolean;
    old: { proficiency: number | null; validation_status: string | null; information_source: string | null };
    new: { proficiency: number | null; validation_status: string | null; information_source: string | null } };
  learning_recommendation: { learning_recommendation_id: number; review_status: string; completed_at: string | null };
  next_step: string;
}
export interface RematchRequest {
  run_trigger?: 'REMATCH_AFTER_LEARNING' | 'MANUAL_REMATCH'; previous_run_id?: number | null;
  rematch_note?: string | null; designation?: string | null; department?: string | null;
}
export interface RematchResponse {
  status: string; run_status: string; requirement_id: number; matching_run_id: number; previous_run_id: number;
  run_trigger: RunTrigger; employees_sent: number; message: string; result_count?: number;
}
export interface RunCompare {
  requirement_id: number; new_run: CompareRun; old_run: CompareRun;
  summary: { employees_compared: number; improved: number; declined: number; unchanged: number;
    average_score_change: number | null; gaps_closed: number; new_gaps: number; only_in_new_run: number; only_in_old_run: number; };
  employees: CompareEmployee[];
}
export interface CompareRun { matching_run_id: number; run_trigger: RunTrigger; run_status: string; execution_date: string | null;
  completed_at: string | null; previous_run_id: number | null; rematch_note: string | null; }
export interface CompareEmployee {
  employee_id: number; employee_code: string; resource_name: string; only_in: 'NEW_RUN' | 'OLD_RUN' | null;
  old_matching_result_id: number | null; new_matching_result_id: number | null;
  old_score: number | null; new_score: number | null; score_change?: number;
  old_category: string | null; new_category: string | null; category_changed?: boolean;
  old_rank: number | null; new_rank: number | null; rank_change?: number | null;   // > 0 = moved up
  gaps_comparable?: boolean; gap_changes?: GapChange[];
}
export interface GapChange {
  skill_id: number; skill_name: string | null; status: 'CLOSED' | 'IMPROVED' | 'UNCHANGED' | 'WORSENED' | 'NEW';
  mandatory: boolean; required_level: number; old_gap_type: GapType | null; old_level: number | null;
  new_gap_type: GapType | null; new_level: number | null; old_priority: GapPriority | null; new_priority: GapPriority | null;
}
```
Service methods:
- `recordEvidence(recId, body)` -> `POST /api/learning-recommendations/{id}/evidence` (201) returns `EvidenceResponse`
- `listEvidence(recId)` -> `GET /api/learning-recommendations/{id}/evidence`
- `rematch(requirementId, body?, async = true)` -> `POST /api/requirements/{id}/rematch` with query `{ async }`  (202 async / 201 sync)
- `compareRuns(runId, withRunId?)` -> `GET /api/matching-runs/{runId}/compare` with query `{ withRunId }`
- `getRun(runId)` already exists for polling.

UI:
1. **Record evidence** button on each learning item that is APPROVED or IN_PROGRESS (roles: ADMIN, LK_ADMIN, SKILL_REVIEWER, RESOURCE_MANAGER). Opens a form (dialog/inline like the review form): evidence type (select), title (required), description, score + maximum score (score needs maximum; score <= maximum), evidence date (use the existing `shared/date-picker.component`, not in the future), valid until, file reference/link, **New proficiency (1-5)** select ("Update the person's rating"), checkbox "Mark this learning step completed" (default on). Show backend 400 `errors` under the fields and `message` otherwise.
2. After success: toast "Evidence saved", update that item to COMPLETED (when `mark_completed`), and show a result panel: "Docker: not rated -> 3 (Assessment verified)" built from `employee_skill.old/new`, with a **Rematch now** button. Show `listEvidence` as an expandable "Evidence" list on the item (type, title, score/max, date, verified).
3. **Rematch** button (also in the Skill Growth page header for the chosen project, roles above): confirm dialog with an optional note ("After Docker training") -> `rematch(requirementId, {rematch_note})` async. On 202 reuse the existing run polling/scan console with the returned `matching_run_id` (poll `getRun` every 2 s until COMPLETED / COMPLETED_WITH_ERRORS / FAILED). Handle 409 (toast the `message`), 503 (existing busy handling). When finished, navigate to the Compare page for the new run.
4. **Compare page** `pages/run-compare/run-compare.component.ts`, route `compare/:runId` (optional `?withRunId=`); not a nav item, reached from the Rematch result, the Skill Growth page ("Compare with previous run" when the run has `previous_run_id`), and the run label (Phase 1F).
   - Header: old run vs new run (ids, dates, trigger, note).
   - Summary cards: improved / declined / unchanged, average score change (+/-), gaps closed, new gaps.
   - Table sorted as received (biggest improvement first): person, old score -> new score with +/- change colour, old category -> new category (highlight when `category_changed`), rank "5 -> 2" with an up/down arrow (`rank_change > 0` = up), and an expandable row listing `gap_changes` with a status pill each (CLOSED green, IMPROVED light green, UNCHANGED grey, WORSENED red, NEW orange) and old/new level ("missing" when level is null and gap type is MISSING).
   - If `gaps_comparable === false` show "Gap details are not available for the older run." for that row. People with `only_in` show "Only in new run" / "Only in old run" and no score change.
   - Keep the old run viewable: a link "View older run" to Skill Growth / matches with `?runId=`.

## Acceptance checklist (verify at the end of each phase; list what you checked)
- App compiles (`ng build`) with no template or type errors; no `any` added except where the existing code already uses it.
- Phase 1: gaps show priority colours; every learning item shows a source badge; hours/weeks appear only for catalog items; Approve/Reject/Start are hidden for roles that cannot review; a 403 shows a clear toast; Generate shows the backend `note`.
- Phase 2: only ADMIN/LK_ADMIN see create/edit/approve; a new resource shows "Awaiting approval"; editing an approved resource shows the "approve it again" note.
- Phase 3: pathway totals ignore rejected steps; `notes` are displayed; dashboard cards load with no filters.
- Phase 4: evidence form blocks a future date and score > maximum; rematch polls until finished and then opens Compare; rank arrow direction is correct (`rank_change > 0` = up); an older run with `gaps_comparable: false` does not crash.
- No change to existing screens beyond what is listed. Do not remove existing fields or behaviour.

At the end of each phase give me: the list of files changed/added, and any assumption you made.

======================== COPY UNTIL HERE ========================
