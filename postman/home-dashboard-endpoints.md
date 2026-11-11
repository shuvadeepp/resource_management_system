# Home dashboard endpoints (read-only)

Auth: `Authorization: Bearer <JWT>` - same rules as `GET /api/**` (USER, ADMIN, PROJECT_MANAGER, LK_ADMIN, RESOURCE_MANAGER, SKILL_REVIEWER). No new tables or columns. Timestamps are IST (Asia/Kolkata).

## 1. GET /api/dashboard/bench-skills?limit=4

Top skills among employees free in the next 30 days (today .. today+30).

Free means:
- current `resource_availability` row (max `availability_id`) is `BENCH` or `PARTIALLY_AVAILABLE` (same rule as `available_employees` on `/api/dashboard/summary`)
- no `PROPOSED` / `PLANNED` / `ACTIVE` allocation overlapping the window
- no `APPROVED_PENDING` (latest decision per employee+requirement is `SELECTED` with `allocation_id` null)

Counted via `employee_skills` with `is_active = 1`. `limit` default 4, clamped to 1..20. Ordered by count desc, then skill name.

```
GET {{baseUrl}}/api/dashboard/bench-skills?limit=4
```
```json
{ "skills": [ { "skill_id": 2, "skill_name": "Java", "available_count": 4 } ] }
```

## 2. GET /api/decisions/pending?limit=5

People needing a human action, newest `waiting_since` first.
- `SHORTLISTED`: `matching_results.recommendation_status = RECOMMENDED` and no `allocation_decisions` row. `waiting_since` = `matching_results.created_at`.
- `APPROVED_PENDING`: latest decision per (employee, requirement) is `SELECTED` with `allocation_id` null. `waiting_since` = decision `created_at`.

`limit` default 5, clamped to 1..50. `total` = count before limit. `oldest_waiting_days` = whole days from the oldest waiting item to now (IST), `null` if none.

```
GET {{baseUrl}}/api/decisions/pending?limit=5
```
```json
{
  "items": [
    {
      "matching_result_id": 41,
      "requirement_id": 7,
      "employee_id": 12,
      "employee_name": "Asha Rao",
      "role_name": "Java Developer",
      "overall_score": 87.5,
      "status": "SHORTLISTED",
      "waiting_since": "2026-11-09T14:32:10"
    }
  ],
  "total": 9,
  "oldest_waiting_days": 6
}
```
