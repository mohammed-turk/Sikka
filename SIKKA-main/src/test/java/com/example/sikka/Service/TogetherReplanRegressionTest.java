package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.PlanApprovalDtoIn;
import com.example.sikka.DtoIn.SuggestReplanDtoIn;
import com.example.sikka.DtoOut.ReplanProposalDtoOut;
import com.example.sikka.Model.*;
import com.example.sikka.Repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:sikka_together_qa;MODE=MySQL;DB_CLOSE_DELAY=-1", "spring.jpa.properties.hibernate.max_fetch_depth=0", "sikka.park.latitude=24.7", "sikka.park.longitude=46.6"})
class TogetherReplanRegressionTest {
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private VisitPlanService visitPlanService;
    @Autowired private PlanApprovalService approvalService;
    @Autowired private VisitPlanRepository planRepository;
    @Autowired private PlanItemRepository itemRepository;
    @Autowired private RideRepository rideRepository;
    @Autowired private VisitMemberRepository memberRepository;
    @Autowired private VisitRepository visitRepository;
    @MockitoSpyBean private RideService rideService;
    @MockitoBean private PlaceService placeService;
    @MockitoBean private PrayerTimeService prayerTimeService;
    @MockitoBean private NotificationEventService notificationEventService;
    @MockitoSpyBean private AiPlanService aiPlanService;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private Fixture fixture;

    private record Fixture(Integer visitId, Integer planId, Integer organizerId, Integer memberAccountId, Integer outsiderId, List<Integer> memberIds, List<Integer> itemIds, List<Integer> rideIds) {}

    private <T> T transaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    @BeforeEach
    void configureProviders() {
        // H2 JSON quotes JDBC String bindings, unlike MySQL. Keep these String-backed JSON fixtures as text across transactions.
        transaction(() -> {
            for (String column : List.of("visit.goals", "visit.shared_selected_rides", "visit_member.selected_rides", "visit_member.preferences", "plan_item.participant_visit_member_ids", "visit_plan.proposed_items", "visit_plan.excluded_rides", "visit_plan.affected_member_ids", "visit_plan.proposal_base_snapshot", "plan_history.planning_input_snapshot", "plan_history.generation_result_snapshot")) {
                String[] name = column.split("\\.");
                entityManager.createNativeQuery("ALTER TABLE " + name[0] + " ALTER COLUMN " + name[1] + " CHARACTER LARGE OBJECT").executeUpdate();
            }
            return null;
        });
        // Only eligibility and calibrated geometry are fixtures. Waiting estimates are read from actual persisted rides.
        doReturn(Map.of("status", "UNKNOWN", "missingFields", List.of())).when(rideService).checkEligibilityForMember(anyInt(), anyInt(), anyInt());
    }

    private Fixture prepareVisit(String mode, boolean completedFirst) {
        return prepareVisit(mode, completedFirst, false, false);
    }

    private Fixture prepareVisit(String mode, boolean completedFirst, boolean solo, boolean earlyStart) {
        Fixture result = transaction(() -> {
            rideRepository.findRidesByStatus("OPEN").forEach(ride -> ride.setStatus("CLOSED"));
            Account organizer = account("Leader");
            Account participant = account("Member");
            Account outsider = account("Outsider");
            LocalDateTime starts = LocalDateTime.now(ZoneId.of("Asia/Riyadh")).withSecond(0).withNano(0).plusMinutes(earlyStart ? 20 : -5);
            Visit visit = new Visit();
            visit.setName("Together regression");
            visit.setOrganizer(organizer);
            visit.setVisitDate(starts.toLocalDate());
            visit.setTripType(solo ? "SOLO" : "FRIENDS");
            visit.setTogetherMode(mode);
            visit.setMaxParticipants(solo ? 1 : 2);
            visit.setDurationMinutes(180);
            visit.setGoals("[\"MAX_RIDES\"]");
            visit.setPrimaryGoal("MAX_RIDES");
            visit.setIncludePrayer(false);
            visit.setStatus("IN_PROGRESS");
            visit.setStartedAt(earlyStart ? LocalDateTime.now(ZoneId.of("Asia/Riyadh")).minusMinutes(1) : starts);
            visit.setPlannedStartTime(starts.toLocalTime());
            visit.setPlannedEndTime(starts.plusMinutes(180).toLocalTime());
            visit.setMembersLocked(true);
            entityManager.persist(visit);
            List<VisitMember> members = solo ? List.of(member(visit, organizer)) : List.of(member(visit, organizer), member(visit, participant));
            for (VisitMember member : members) {
                VisitMealBreak meal = new VisitMealBreak();
                meal.setVisit(visit);
                meal.setVisitMember(member);
                meal.setMealChoice("NONE");
                meal.setBreakKey(UUID.randomUUID().toString());
                entityManager.persist(meal);
            }
            List<Ride> rides = new ArrayList<>();
            for (int index = 0; index < 3; index++) {
                Ride ride = new Ride();
                ride.setName("QA ride " + index);
                ride.setStatus("OPEN");
                ride.setRideDurationMinutes(5);
                ride.setEntryExitBufferMinutes(3);
                ride.setWaitMinutes(index == 0 ? 15 : index == 1 ? 10 : 20);
                ride.setWaitObservedAt(LocalDateTime.now());
                ride.setWaitSource("REGRESSION_ESTIMATE");
                entityManager.persist(ride);
                rides.add(ride);
            }
            PlanHistory history = new PlanHistory();
            history.setAccount(organizer);
            history.setVisit(visit);
            history.setRequestKey(UUID.randomUUID().toString());
            history.setOperationType("INITIAL");
            history.setStatus("SUCCEEDED");
            history.setBillingMode("FREE");
            entityManager.persist(history);
            VisitPlan plan = new VisitPlan();
            plan.setVisit(visit);
            plan.setPlanKind("GROUP");
            plan.setTimingMode("SCHEDULED");
            plan.setIsActive(true);
            plan.setGeneratedByHistory(history);
            entityManager.persist(plan);
            PlanApproval approval = new PlanApproval();
            approval.setVisitPlan(plan);
            approval.setStatus("APPROVED");
            approval.setApprovalScope("GROUP");
            approval.setRequestedByAccount(organizer);
            approval.setRequiredApproverAccount(organizer);
            approval.setApprovedByAccount(organizer);
            approval.setRespondedAt(starts);
            entityManager.persist(approval);
            plan.setPlanApproval(approval);
            List<PlanItem> items = new ArrayList<>();
            int offset = 0;
            for (int index = 0; index < rides.size(); index++) {
                Ride ride = rides.get(index);
                PlanItem item = new PlanItem();
                item.setVisitPlan(plan);
                item.setActivityKind("RIDE");
                item.setRide(ride);
                item.setTitle(ride.getName());
                item.setRelativeStartMinutes(offset);
                item.setWalkingMinutes(index == 0 ? 0 : 1);
                item.setWaitingMinutes(ride.getWaitMinutes());
                item.setActivityMinutes(8);
                offset += item.getWalkingMinutes() + item.getWaitingMinutes() + 8;
                item.setRelativeEndMinutes(offset);
                item.setStartsAt(starts.plusMinutes(item.getRelativeStartMinutes()));
                item.setEndsAt(starts.plusMinutes(offset));
                item.setParticipantVisitMemberIds(mapper.writeValueAsString(members.stream().map(VisitMember::getId).toList()));
                item.setSubgroupKey("all");
                item.setSortOrder(index);
                item.setPriority("MUST_RIDE");
                entityManager.persist(item);
                items.add(item);
            }
            for (VisitMember member : members) {
                if (completedFirst) {
                    VisitRecord record = new VisitRecord();
                    record.setVisitMember(member);
                    record.setPlanItem(items.get(0));
                    record.setRide(rides.get(0));
                    record.setRecordedByAccount(organizer);
                    record.setRequestKey(UUID.randomUUID().toString());
                    record.setStatus("COMPLETED");
                    record.setCompletedAt(starts.plusMinutes(2));
                    entityManager.persist(record);
                } else {
                    ActivitySession session = new ActivitySession();
                    session.setVisitMember(member);
                    session.setPlanItem(items.get(0));
                    session.setRide(rides.get(0));
                    session.setStatus("ACTIVE");
                    session.setTrackingMode("MANUAL");
                    entityManager.persist(session);
                }
            }
            entityManager.flush();
            return new Fixture(visit.getId(), plan.getId(), organizer.getId(), participant.getId(), outsider.getId(), members.stream().map(VisitMember::getId).toList(), items.stream().map(PlanItem::getId).toList(), rides.stream().map(Ride::getId).toList());
        });
        List<Map<String, Object>> routes = new ArrayList<>();
        List<String> nodes = new ArrayList<>(List.of("ENTRANCE"));
        result.rideIds().forEach(id -> nodes.add("RIDE:" + id));
        for (String from : nodes) for (String to : nodes) routes.add(Map.of("fromNodeKey", from, "toNodeKey", to, "minutes", from.equals(to) || from.equals("ENTRANCE") ? 0 : 1));
        when(placeService.getWalkingContext(anyInt(), eq(result.visitId()))).thenReturn(Map.of("entranceNodeKey", "ENTRANCE", "revision", "REGRESSION_V1", "places", List.of(), "routes", routes));
        return result;
    }

    private Account account(String name) {
        Account account = new Account();
        account.setName(name);
        account.setEmail(UUID.randomUUID() + "@example.test");
        account.setPasswordHash("regression-hash");
        account.setRole("USER");
        account.setStatus("ACTIVE");
        account.setEmailVerified(true);
        entityManager.persist(account);
        return account;
    }

    private VisitMember member(Visit visit, Account account) {
        VisitorProfile profile = new VisitorProfile();
        profile.setAccount(account);
        profile.setName(account.getName());
        profile.setBirthDate(LocalDate.of(1998, 1, 1));
        entityManager.persist(profile);
        VisitMember member = new VisitMember();
        member.setVisit(visit);
        member.setVisitorProfile(profile);
        member.setName(profile.getName());
        member.setBirthDate(profile.getBirthDate());
        member.setRsvpStatus("GOING");
        member.setAnswerStatus("ANSWERED");
        member.setPreferences("{\"dislikedFeatures\":[]}");
        member.setSelectedRides("[]");
        member.setSnapshotAt(LocalDateTime.now());
        entityManager.persist(member);
        return member;
    }

    private Fixture prepareApprovedVisit() {
        Fixture prepared = prepareVisit("TOGETHER", false);
        transaction(() -> {
            entityManager.createQuery("delete from ActivitySession s where s.visitMember.visit.id = :visitId").setParameter("visitId", prepared.visitId()).executeUpdate();
            Visit visit = visitRepository.findVisitById(prepared.visitId());
            visit.setStatus("READY");
            visit.setStartedAt(null);
            visit.setExpectedArrivalTime(LocalTime.of(16, 0));
            return null;
        });
        return prepared;
    }

    @Test
    void approvedVisitCanMoveDateWithoutReplacingItsRides() {
        Fixture prepared = prepareApprovedVisit();
        LocalDate nextDate = LocalDate.now(ZoneId.of("Asia/Riyadh")).with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
        visitPlanService.updateVisitArrival(prepared.organizerId(), prepared.visitId(), new com.example.sikka.DtoIn.UpdateVisitArrivalDtoIn(LocalTime.of(16, 0), null, nextDate));
        transaction(() -> {
            assertEquals(nextDate, visitRepository.findVisitById(prepared.visitId()).getVisitDate());
            assertEquals(prepared.itemIds(), itemRepository.findAllByVisitPlan_Id(prepared.planId()).stream().sorted(Comparator.comparing(PlanItem::getSortOrder)).map(PlanItem::getId).toList());
            assertTrue(itemRepository.findAllByVisitPlan_Id(prepared.planId()).stream().allMatch(item -> item.getStartsAt().toLocalDate().equals(nextDate)));
            assertTrue(planRepository.findVisitPlanById(prepared.planId()).getIsActive());
            return null;
        });
    }

    @Test
    void changingApprovedDateRebuildsEveryVerifiedPrayerWithoutDroppingRides() {
        Fixture prepared = prepareApprovedVisit();
        LocalDate nextDate = LocalDate.now(ZoneId.of("Asia/Riyadh")).with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
        Integer roomId = transaction(() -> {
            visitRepository.findVisitById(prepared.visitId()).setIncludePrayer(true);
            Place room = new Place();
            room.setName("QA prayer room");
            room.setKind("PRAYER_ROOM");
            room.setStatus("OPEN");
            entityManager.persist(room);
            return room.getId();
        });
        List<String> nodes = new ArrayList<>(List.of("ENTRANCE", "PLACE:" + roomId));
        prepared.rideIds().forEach(id -> nodes.add("RIDE:" + id));
        List<Map<String, Object>> routes = new ArrayList<>();
        for (String from : nodes) for (String to : nodes) routes.add(Map.of("fromNodeKey", from, "toNodeKey", to, "minutes", from.equals(to) || from.equals("ENTRANCE") ? 0 : 1));
        when(placeService.getWalkingContext(anyInt(), eq(prepared.visitId()))).thenReturn(Map.of("entranceNodeKey", "ENTRANCE", "revision", "PRAYER_QA", "places", List.of(Map.of("id", roomId, "name", "QA prayer room", "kind", "PRAYER_ROOM", "status", "OPEN", "rateable", false, "nodeKey", "PLACE:" + roomId)), "routes", routes));
        when(prayerTimeService.getPrayerTimes(eq(nextDate), anyDouble(), anyDouble())).thenReturn(Map.of("Fajr", nextDate.atTime(5, 0), "Dhuhr", nextDate.atTime(12, 0), "Asr", nextDate.atTime(15, 0), "Maghrib", nextDate.atTime(17, 0), "Isha", nextDate.atTime(18, 0)));
        visitPlanService.updateVisitArrival(prepared.organizerId(), prepared.visitId(), new com.example.sikka.DtoIn.UpdateVisitArrivalDtoIn(LocalTime.of(16, 0), null, nextDate));
        transaction(() -> {
            List<PlanItem> items = itemRepository.findAllByVisitPlan_Id(prepared.planId());
            assertEquals(2, items.stream().filter(item -> "PRAYER".equals(item.getBreakType())).count());
            assertEquals(prepared.rideIds(), items.stream().filter(item -> item.getRide() != null).sorted(Comparator.comparing(PlanItem::getSortOrder)).map(item -> item.getRide().getId()).toList());
            assertTrue(items.stream().allMatch(item -> item.getEndsAt().toLocalDate().equals(nextDate)));
            return null;
        });
        verify(aiPlanService, never()).generatePlanCandidate(anyString());
    }

    @Test
    void approvedMealEditKeepsEveryRideAndIsAtomicWhenItDoesNotFit() {
        Fixture prepared = prepareApprovedVisit();
        LocalDate nextDate = LocalDate.now(ZoneId.of("Asia/Riyadh")).with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
        var meal = new com.example.sikka.DtoIn.MealPreferencesDtoIn("ONE_MEAL", List.of("END"));
        visitPlanService.updateVisitArrival(prepared.organizerId(), prepared.visitId(), new com.example.sikka.DtoIn.UpdateVisitArrivalDtoIn(LocalTime.of(16, 0), meal, nextDate));
        transaction(() -> {
            assertEquals(prepared.rideIds(), itemRepository.findAllByVisitPlan_Id(prepared.planId()).stream().sorted(Comparator.comparing(PlanItem::getSortOrder)).filter(item -> item.getRide() != null).map(item -> item.getRide().getId()).toList());
            assertEquals(1, itemRepository.findAllByVisitPlan_Id(prepared.planId()).stream().filter(item -> "MEAL".equals(item.getBreakType())).count());
            return null;
        });
        var excessive = new com.example.sikka.DtoIn.MealPreferencesDtoIn("TWO_MEALS", List.of("START", "END"));
        assertThrows(ApiException.class, () -> visitPlanService.updateVisitArrival(prepared.organizerId(), prepared.visitId(), new com.example.sikka.DtoIn.UpdateVisitArrivalDtoIn(LocalTime.of(16, 0), excessive, nextDate)));
        transaction(() -> {
            assertEquals(1, itemRepository.findAllByVisitPlan_Id(prepared.planId()).stream().filter(item -> "MEAL".equals(item.getBreakType())).count());
            assertEquals(prepared.rideIds(), itemRepository.findAllByVisitPlan_Id(prepared.planId()).stream().sorted(Comparator.comparing(PlanItem::getSortOrder)).filter(item -> item.getRide() != null).map(item -> item.getRide().getId()).toList());
            return null;
        });
    }

    @Test
    void regeneratedPlanWaitsForLeaderWithoutReplacingApprovedPlan() {
        Fixture prepared = prepareApprovedVisit();
        var generated = visitPlanService.regeneratePlan(prepared.organizerId(), prepared.visitId(), new com.example.sikka.DtoIn.GeneratePlanDtoIn(UUID.randomUUID().toString()));
        assertEquals("SUCCEEDED", generated.getRequestStatus(), generated.getFailureReason());
        transaction(() -> {
            assertTrue(planRepository.findVisitPlanById(prepared.planId()).getIsActive());
            VisitPlan replacement = planRepository.findVisitPlanById(generated.getGroupPlanId());
            assertFalse(replacement.getIsActive());
            assertEquals("PENDING", replacement.getPlanApproval().getStatus());
            return null;
        });
        approvalService.respondToPlanApproval(prepared.organizerId(), new PlanApprovalDtoIn(generated.getGroupPlanId(), null), 1);
        transaction(() -> {
            assertFalse(planRepository.findVisitPlanById(prepared.planId()).getIsActive());
            assertTrue(planRepository.findVisitPlanById(generated.getGroupPlanId()).getIsActive());
            return null;
        });
    }

    private void updateWait(Integer minutes) {
        transaction(() -> {
            Ride ride = rideRepository.findRideById(fixture.rideIds().get(1));
            ride.setWaitMinutes(minutes);
            ride.setWaitObservedAt(minutes == null ? null : LocalDateTime.now());
            return null;
        });
    }

    private ReplanProposalDtoOut request(Integer accountId) {
        SuggestReplanDtoIn dto = new SuggestReplanDtoIn();
        dto.setReason("Regression: updated future wait");
        dto.setReplaceUnavailableRides(false);
        return visitPlanService.suggestReplan(accountId, fixture.visitId(), dto);
    }

    private void respond(Integer accountId, ReplanProposalDtoOut proposal, int action) {
        approvalService.respondToPlanApproval(accountId, new PlanApprovalDtoIn(fixture.planId(), proposal.getProposalKey()), action);
    }

    private List<Integer> storedWaits() {
        return transaction(() -> fixture.itemIds().stream().map(id -> itemRepository.findPlanItemById(id).getWaitingMinutes()).toList());
    }

    @Test
    void memberRequestsSharedProposalLeaderRejectsThenApprovesUsingCurrentWaits() {
        fixture = prepareVisit("TOGETHER", false);
        updateWait(25);
        ReplanProposalDtoOut proposal = transaction(() -> request(fixture.memberAccountId()));
        assertEquals("GROUP", proposal.getApprovalScope());
        assertEquals(fixture.organizerId(), proposal.getRequiredApproverAccountId());
        assertEquals(new HashSet<>(fixture.memberIds()), new HashSet<>(proposal.getAffectedMemberIds()));
        assertEquals(List.of(15, 10, 20), storedWaits());
        transaction(() -> {
            JsonNode proposed = mapper.readTree(planRepository.findVisitPlanById(fixture.planId()).getProposedItems());
            assertEquals(25, proposed.get(1).path("waitingMinutes").asInt());
            assertEquals(23, proposed.get(0).path("relativeEndMinutes").asInt());
            assertEquals(57, proposed.get(1).path("relativeEndMinutes").asInt());
            return null;
        });
        assertThrows(ApiException.class, () -> respond(fixture.memberAccountId(), proposal, 1));
        assertThrows(ApiException.class, () -> respond(fixture.outsiderId(), proposal, 2));
        respond(fixture.organizerId(), proposal, 2);
        assertEquals(List.of(15, 10, 20), storedWaits());
        ReplanProposalDtoOut retry = request(fixture.memberAccountId());
        assertNotEquals(proposal.getProposalKey(), retry.getProposalKey());
        assertThrows(ApiException.class, () -> respond(fixture.organizerId(), proposal, 1));
        respond(fixture.organizerId(), retry, 1);
        assertEquals(List.of(15, 25, 20), storedWaits());
        assertNull(transaction(() -> planRepository.findVisitPlanById(fixture.planId()).getProposedItems()));
        verify(aiPlanService, never()).generatePlanCandidate(anyString());
    }

    @Test
    void initialPlanningSnapshotIncludesUnknownRidesWithoutRewritingTheirStatuses() {
        fixture = prepareVisit("TOGETHER", false);
        transaction(() -> {
            for (Integer id : fixture.rideIds()) {
                Ride ride = rideRepository.findRideById(id);
                ride.setStatus("UNAVAILABLE");
                ride.setAvailabilitySource(null);
                ride.setWaitMinutes(null);
                ride.setWaitObservedAt(null);
            }
            return null;
        });
        String inputJson = transaction(() -> {
            Object snapshot = org.springframework.test.util.ReflectionTestUtils.invokeMethod(visitPlanService, "collectInitialGenerationSnapshot", fixture.organizerId(), visitRepository.findVisitById(fixture.visitId()));
            return org.springframework.test.util.ReflectionTestUtils.invokeMethod(snapshot, "inputJson");
        });
        JsonNode input = mapper.readTree(inputJson);
        for (JsonNode ride : input.path("rides")) {
            assertEquals("UNKNOWN", ride.path("operationalStatus").asString());
            assertTrue(ride.path("availabilityIsEstimate").asBoolean());
            assertEquals(15, ride.path("planningWaitMinutes").asInt());
        }
        com.example.sikka.DtoOut.AiPlanDtoOut plan = new ServerItineraryService().selectInitialPlan(input);
        assertTrue(plan.getFeasible());
        assertFalse(plan.getActivities().isEmpty());
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(visitPlanService, "mergeGenerationWarnings", plan, input);
        assertTrue(plan.getWarnings().stream().anyMatch(warning -> warning.contains("افتراضية")));
        assertTrue(transaction(() -> fixture.rideIds().stream().allMatch(id -> "UNAVAILABLE".equals(rideRepository.findRideById(id).getStatus()))));
    }

    @Test
    void unknownRideCanUseAProvisionalProposalWithoutChangingItsOperationalStatus() {
        fixture = prepareVisit("TOGETHER", false);
        transaction(() -> {
            Ride ride = rideRepository.findRideById(fixture.rideIds().get(1));
            ride.setStatus("UNAVAILABLE");
            ride.setAvailabilitySource(null);
            ride.setWaitMinutes(null);
            ride.setWaitObservedAt(null);
            return null;
        });
        ReplanProposalDtoOut proposal = request(fixture.memberAccountId());
        assertEquals("GROUP", proposal.getApprovalScope());
        assertEquals(List.of(15, 10, 20), storedWaits());
        respond(fixture.organizerId(), proposal, 1);
        assertEquals(List.of(15, 15, 20), storedWaits());
        assertEquals("UNAVAILABLE", transaction(() -> rideRepository.findRideById(fixture.rideIds().get(1)).getStatus()));
    }

    @Test
    void leaderCanRequestSharedChangeWithoutChangingCompletedActivity() {
        fixture = prepareVisit("TOGETHER", true);
        updateWait(25);
        ReplanProposalDtoOut proposal = request(fixture.organizerId());
        respond(fixture.organizerId(), proposal, 1);
        assertEquals(List.of(15, 25, 20), storedWaits());
        assertEquals(23, transaction(() -> itemRepository.findPlanItemById(fixture.itemIds().get(0)).getRelativeEndMinutes()));
    }

    @Test
    void soloVisitorCanReplanAndApproveAfterStartingBeforeExpectedArrival() {
        fixture = prepareVisit("TOGETHER", false, true, true);
        updateWait(25);
        ReplanProposalDtoOut proposal = request(fixture.organizerId());
        respond(fixture.organizerId(), proposal, 1);
        assertEquals(List.of(15, 25, 20), storedWaits());
    }

    @Test
    void unchangedWaitsDoNotProduceUnnecessaryProposal() {
        fixture = prepareVisit("TOGETHER", false);
        assertTrue(assertThrows(ApiException.class, () -> request(fixture.memberAccountId())).getMessage().contains("No schedule change"));
        assertEquals(List.of(15, 10, 20), storedWaits());
    }

    @Test
    void splitMemberCannotEscalateSharedActivitiesToGroupChange() {
        fixture = prepareVisit("SPLIT", false);
        updateWait(25);
        assertTrue(assertThrows(ApiException.class, () -> request(fixture.memberAccountId())).getMessage().contains("No schedule change"));
        assertNull(transaction(() -> planRepository.findVisitPlanById(fixture.planId()).getPendingScope()));
    }

    @Test
    void defaultWaitProposalAndDeadlineOverflowDoNotMutateApprovedPlan() {
        fixture = prepareVisit("TOGETHER", false);
        updateWait(null);
        ReplanProposalDtoOut estimated = request(fixture.memberAccountId());
        assertEquals(List.of(15, 10, 20), storedWaits());
        respond(fixture.organizerId(), estimated, 2);
        updateWait(200);
        assertTrue(assertThrows(ApiException.class, () -> request(fixture.memberAccountId())).getMessage().contains("exceed"));
        assertEquals(List.of(15, 10, 20), storedWaits());
    }

    @Test
    void updatedSnapshotAfterProposalCannotBeSilentlyApproved() {
        fixture = prepareVisit("TOGETHER", false);
        updateWait(25);
        ReplanProposalDtoOut proposal = request(fixture.memberAccountId());
        updateWait(30);
        assertThrows(ApiException.class, () -> respond(fixture.organizerId(), proposal, 1));
        assertEquals(List.of(15, 10, 20), storedWaits());
        respond(fixture.organizerId(), proposal, 2);
    }

    @Test
    void nonGoingOrUnrelatedAccountCannotRequestGroupProposal() {
        fixture = prepareVisit("TOGETHER", false);
        updateWait(25);
        assertThrows(ApiException.class, () -> request(fixture.outsiderId()));
        transaction(() -> {
            memberRepository.findVisitMemberById(fixture.memberIds().get(1)).setRsvpStatus("NOT_GOING");
            return null;
        });
        assertThrows(ApiException.class, () -> request(fixture.memberAccountId()));
    }

    @Test
    void togetherMemberCannotSelectOtherMembersOrRequestAsMinor() {
        fixture = prepareVisit("TOGETHER", false);
        updateWait(25);
        SuggestReplanDtoIn dto = new SuggestReplanDtoIn();
        dto.setReason("Unauthorized explicit participant scope");
        dto.setAffectedMemberIds(fixture.memberIds());
        assertThrows(ApiException.class, () -> visitPlanService.suggestReplan(fixture.memberAccountId(), fixture.visitId(), dto));
        transaction(() -> {
            memberRepository.findVisitMemberById(fixture.memberIds().get(1)).setBirthDate(LocalDate.now().minusYears(12));
            return null;
        });
        assertTrue(assertThrows(ApiException.class, () -> request(fixture.memberAccountId())).getMessage().contains("minors"));
    }
}
