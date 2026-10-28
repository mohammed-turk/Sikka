# SIKKA — Deferred Integration Test Plan

Status: TESTS DEFERRED by project owner. This file is a checklist, not proof of successful execution.
Branch: feature/four-updates
Do not merge into main until every critical case is verified.

## Environment and preparation
1. Configure MySQL, Spring Boot environment, OpenAI credentials, and sample park data.
2. Use test accounts: organizer, adult group member, family member with minor, and unrelated account.
3. Create SOLO, FRIENDS (together and split), and FAMILY visits.
4. Record the visit IDs, member IDs, generated plan IDs, and test ride IDs.
5. Confirm park statuses, directed walking routes, planning wait estimates, eligibility, meals, and prayer data.
6. Run `./mvnw clean test` (Windows: `.\mvnw.cmd clean test`) after implementation is complete.
7. Check DB migrations / schema updates for VisitPlan.excludedRides.

## Task 1 — MUST_RIDE
- [ ] A feasible MUST_RIDE is scheduled once for its selected participant.
- [ ] If all selected MUST_RIDE do not fit, return the best feasible subset for MAX_RIDES.
- [ ] MIN_WALKING / walking reduction goal prefers the lower walking feasible option.
- [ ] MORE_REST goal preserves chosen rest intervals rather than adding distant rides.
- [ ] Missing or untrusted waiting estimates do not become zero-minute waits.
- [ ] Missing directed route is not replaced with fictional straight-line walking.
- [ ] CLOSED ride is excluded with grounded reason; do not incorrectly reject entire plan.
- [ ] NOT_ELIGIBLE participant cannot be scheduled. UNKNOWN is provisional only with warnings.
- [ ] AVOID, disliked ride features and minor supervision remain enforced.
- [ ] Meals have 60 minutes, snacks 30 minutes, initial prayer reserve 15 minutes.
- [ ] No overlapping participant activities and no visit-deadline overflow.
- [ ] Every omitted MUST_RIDE has exactly one reason record per participant.
- [ ] No participant's ride is simultaneously scheduled and excluded.
- [ ] GET existing plan-details endpoint returns excluded rides for GROUP and correct filtered INDIVIDUAL view.
- [ ] Existing original selections persist after generation and regeneration.
- [ ] Infeasible empty plan is not persisted as a valid plan.
- [ ] Free entitlement is per VISIT, not per generate/re-generate request. Verify billing separately before launch.

## Task 2 — Replanning
- [ ] Member can request an INDIVIDUAL proposal for their own solo editable items.
- [ ] Member cannot edit another member's activities or shared group itinerary.
- [ ] Organizer can request a GROUP proposal.
- [ ] Closed MUST_RIDE can be replaced when authorized without failure during snapshot preparation.
- [ ] Earlier completion is detected from recorded execution release time, not assumed.
- [ ] Previously excluded MUST_RIDE is reconsidered when time genuinely becomes available.
- [ ] Finished, ongoing, pinned, meal, prayer and meeting point items are preserved.
- [ ] Alter no more than 25% of remaining substantive activities unless approved one-item exception.
- [ ] Proposal stays pending until the proper approver responds.
- [ ] Rejection leaves approved itinerary unchanged.
- [ ] If no real improvement is feasible, keep the existing itinerary without unnecessary alerts.

## Task 3 — Map API
- [ ] GET /api/v1/map/get/data returns park image and mapped ride/place coordinates.
- [ ] GET /api/v1/map/get/walking-context returns the same directed network used during planning.
- [ ] GET /api/v1/map/route/{fromType}/{fromId}/{toType}/{toId} returns a navigable path, not an arbitrary straight line.
- [ ] Missing routing data returns an explicit unavailable/error state.
- [ ] Plan-details PlanItems have usable rideId/placeId, chronology, startsAt/endsAt and participant identity.
- [ ] Frontend can focus map marker from its matching schedule card.
- [ ] For closed/missing place, do not invent a marker or entrance coordinate.
- [ ] Guest public map endpoints remain accessible, protected edit endpoints remain protected.
- [ ] Verify the Nawaf image and node mapping physically match park locations.

## Task 4 — Google Calendar
- [ ] OAuth connect and status work with correct account.
- [ ] Syncing approved plan creates one event.
- [ ] Syncing the same visit again updates the existing Google event (no duplicate).
- [ ] Updating visit time/date and explicitly re-syncing changes the existing event.
- [ ] Cancelled visit is denied new sync.
- [ ] DELETE /api/v1/google-calendar/sync/cancelled/{visitId} removes an existing cancelled event, or returns success when already absent.
- [ ] Unauthorized user cannot remove a different person's event.
- [ ] Token expiry is refreshed; revoked refresh tokens produce actionable error.
- [ ] Google network failure never cancels or rolls back the core visit plan.
- [ ] Disconnect deletes local stored authorization and event mappings.
- [ ] Check if remote events persist after disconnect and communicate policy clearly.
- [ ] Automatic sync after plan approval/change is NOT assumed until actually wired and confirmed.

## Acceptance record
| Area | Tester | Date | Outcome | Issues |
| --- | --- | --- | --- | --- |
| MUST_RIDE | | | NOT RUN | |
| Replanning | | | NOT RUN | |
| Map API | | | NOT RUN | |
| Google Calendar | | | NOT RUN | |
| Security / approvals | | | NOT RUN | |
| Entitlements / billing | | | NOT RUN | |

## Release gate
Do not merge to main until compile, automated tests, manual API flows, park map coordinates, and visit-based billing are verified.

## Current implementation and verification status
- Initial generation and regeneration now use ServerItineraryService for the final schedule rather than AI subset hints. MUST_RIDE count, then PREFERRED count, then distinct ride count determine priority. Equal-count schedules favor lower walking for MIN_WALKING / LESS_WALKING / MINIMIZE_WALKING / مشي أقل; other goals, including MORE_REST, favor lower occupied time and thus more free time. The bounded beam search does not claim globally optimal results.
- The server uses only supplied OPEN rides, explicit ELIGIBLE/UNKNOWN eligibility, non-missing queue estimates and directed walking routes. Avoided rides and disliked features are rejected. Meals retain their supplied placement/windows and 60/30-minute durations; initial prayer reserve is 15 minutes. Verified arrival prayer scheduling remains unchanged.
- A split itinerary can use independent adult groups; shared food participants are grouped together, and minors remain supervised. Incompatible overlapping food groups fail explicitly rather than inventing travel or supervision.
- Excluded MUST_RIDE pairs are derived by the server and persisted through existing VisitPlan.excludedRides. Schedule conflicts describe a bounded selection decision, never a proof that all possible schedules are impossible.
- REPLAN can insert eligible, unplayed rides into verified gaps or after existing commitments when there is remaining time. Original activities retain identity, order and frozen execution data. New activities have null IDs in the pending proposal, are saved only on approval, and are removed from excludedRides only after approval. Rejection leaves the active plan and exclusions intact.
- Inserted activities are revalidated at approval against exact server proposal snapshots, current resources, wait estimates, eligibility, participant scope, duration and directed routes, including the unchanged approach walk to the following fixed activity. Individual proposals remain restricted to that adult's scope; group proposals require organizer approval.
- GET /api/v1/visit-plan/get/map-route/{visitPlanId} returns PER_MEMBER segments including an attempted entrance segment for each member. Each segment includes visitMemberId, fromPlanItemId, toPlanItemId, fromEntrance, estimated, entrancesVerified and available. Missing routes have no invented geometry. The graph's unverified entrance status is preserved; field calibration still requires Nawaf's confirmation.
- Calendar after-commit events synchronize all connected GOING accounts separately, with descriptions filtered to each account's participating activities. One account's failure does not prevent the next account's synchronization. Cancellation removes linked members' saved events too. Calendar writes use independent transactions after commit.
- Existing endpoint paths and approval request bodies remain compatible. Frontend consumers must handle proposed new activities with id=null and PER_MEMBER route segments.

## Verification in this environment
- Maven test execution was attempted but the Spring Boot 4.1.1 parent is unavailable in the local cache and Maven Central was unreachable. No successful build or JUnit execution is claimed.
- Added focused tests for server subset selection, optional filling, insufficient duration, missing data, avoidance/closures, early-release additions, lower walking, independent split groups, food durations and prayer reserve.
- Added tests for per-member route chains, missing route geometry, all-member calendar dispatch, isolated provider failures and personal activity filtering.
- Java syntax parsing and git diff whitespace checks are run separately; they are not substitutes for compilation, integration tests or real OAuth verification.

## Remaining deployment checks
- Run ./mvnw test in an environment that can resolve dependencies.
- Verify a pending appended ride does not appear in active PlanItems before approval; rejection changes nothing; approval creates the new item and clears its exclusion.
- Verify stale proposal keys, changed wait snapshots, started activities and out-of-scope member additions are rejected.
- Confirm Google OAuth environment variables and n8n/Moyasar credentials, then exercise provider success/failure paths.
- Confirm map entrance calibration and the frontend's handling of the extended route/proposal contract.
