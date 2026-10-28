# SIKKA — FRIENDS + SPLIT (Local)

## Coverage
Two NEW USER accounts, leader + member, registration, manual email activation through local MySQL, session login, create visit, invite/join, RSVP, individual ride priorities, preview, gated AI generation, group approval authorization, map, basic during-visit/replan/progress, optional completion. No ADMIN account required.

## Order
1. Import Postman collection and environment. Fill `leaderPassword` and `memberPassword` locally (>=8 chars) and keep them secret. Pick unique unused test emails if re-running, and change them in SQL too.
2. Run folder 00 (registration); do not treat an existing-account 400 as successful registration.
3. Run `SQL/01_Verify_New_Accounts.sql`.
4. Run `SQL/02_Activate_New_Accounts.sql` on **local** DB. Script rolls back by default: inspect, then change ROLLBACK to COMMIT and rerun after approval. The emails in the SQL must match environment emails.
5. Run folder 01. Verify session login. For login switching, Postman maintains one cookie jar per localhost origin; requests must log in as their named account before actions.
6. Run folder 02. Before 02.05 verify inviteCode in environment; before subsequent calls verify visitId.
7. Run `SQL/03_Rides_And_Places_ReadOnly.sql` and fill `rideA`, `rideB` with distinct valid OPEN ride IDs. Run folder 03. If IDs fail, check response and selected-ride schema.
8. Before folder 04, run SQL/05_Planning_Entitlement_ReadOnly.sql and inspect preview. Backend might require an actual confirmed Moyasar payment or a legitimately available test entitlement. No automatic payment simulation is included. AI generation may take time and may require credentials. If blocked, record the failure instead of faking success.
9. Run folders 05 and 06 only when plans exist and are in correct states. Keep 06.07 manual.
10. Run read-only QA SQL. No destructive cleanup runs automatically.

## Security and limitations
- Use only local DB. Never run these mutations against Railway production.
- Folder 00 registration expects response code 200 (a 400 means inspect / account exists).
- New test accounts are USER roles; leader authority derives from organizer account, not ADMIN.
- The flow is **not a certified green end-to-end run**: no local server/database or credentials supplied for execution.
- Automatic capturing of inviteCode depends on response message `invite code: ...` as used in current VisitController. Other IDs fetched from live GET responses; if empty, pause and inspect.
- Different user sessions share cookie jar; run sequentially with the login switch steps.
- Folder 04 POST is intentionally guarded by manual entitlement verification; do not bypass payment or forge orders.
- Code derived from current main controllers and DTOs read on 2026-10-28.
- Existing sample SQL patch 09 changes many ride statuses and fixture entitlements; it is **not** reused. No prior collation error reintroduced because new SQL does not compare visit names for INSERT/deduplication.
- The initial test accounts `sikka.tester@example.test` are NOT touched; its `email_verified=0` state remains as you set it.
