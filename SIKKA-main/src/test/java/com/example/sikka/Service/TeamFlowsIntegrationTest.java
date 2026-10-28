package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.*;
import com.example.sikka.Model.*;
import com.example.sikka.Repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TeamFlowsIntegrationTest {
    @Autowired private EntityManager entityManager;
    @Autowired private VisitService visitService;
    @Autowired private VisitMemberService visitMemberService;
    @Autowired private FamilyMemberService familyMemberService;
    @Autowired private VisitMemberRepository visitMemberRepository;
    @Autowired private VisitMealBreakService visitMealBreakService;
    @Autowired private VisitMealBreakRepository mealBreakRepository;
    @Autowired private PaymentOrderService paymentOrderService;
    @Autowired private PlanHistoryRepository historyRepository;
    @Autowired private RideService rideService;
    @Autowired private MapService mapService;
    @Autowired private VisitPlanService visitPlanService;

    @Test
    void reuseFriendsCopiesDurationAndSelectionsButRequiresFriendsToRejoin() {
        Visit visit = createVisit("FRIENDS");
        VisitMember organizer = visitMemberRepository.findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(visit.getId(), visit.getOrganizer().getId());
        organizer.setSelectedRides("[]");
        VisitorProfile guest = createProfile(createAccount());
        VisitMember friend = new VisitMember();
        friend.setVisit(visit);
        friend.setVisitorProfile(guest);
        friend.setRsvpStatus("GOING");
        entityManager.persist(friend);
        visit.setPlannedStartTime(java.time.LocalTime.of(12, 0));
        visit.setPlannedEndTime(java.time.LocalTime.of(18, 0));
        createActiveGroupPlan(visit);
        var result = visitService.reuseVisit(visit.getOrganizer().getId(), visit.getId(), new ReuseVisitDtoIn(LocalDate.now().plusDays(3)));
        Visit reused = entityManager.find(Visit.class, result.getVisitId());
        assertEquals(360, reused.getDurationMinutes());
        assertNull(reused.getPlannedStartTime());
        assertNull(reused.getPlannedEndTime());
        assertEquals("DRAFT", reused.getStatus());
        assertNotNull(result.getInviteCode());
        assertEquals(2, visitMemberRepository.findAllByVisit_Id(reused.getId()).size());
        assertEquals("[]", visitMemberRepository.findAllByVisit_Id(reused.getId()).stream().filter(m -> m.getVisitorProfile() != null && m.getVisitorProfile().getAccount().getId().equals(visit.getOrganizer().getId())).findFirst().orElseThrow().getSelectedRides());
    }

    @Test
    void familyDeletionKeepsVisitSnapshotAndDoesNotRestoreDeletedPersonWhenReusing() {
        Visit visit = createVisit("FAMILY");
        FamilyMember family = createFamily(visit.getOrganizer());
        visitMemberService.addFamilyMembers(visit.getOrganizer().getId(), visit.getId(), new AddFamilyMembersDtoIn(List.of(family.getId())));
        visitMemberService.freezeMemberSnapshots(visit);
        VisitMember member = visitMemberRepository.findAllByFamilyMember_Id(family.getId()).get(0);
        String savedName = member.getName();
        family.setName("Updated outside the visit");
        Integer familyId = family.getId();
        familyMemberService.deleteFamilyMember(visit.getOrganizer().getId(), familyId);
        entityManager.flush();
        entityManager.clear();
        assertNull(entityManager.find(FamilyMember.class, familyId));
        VisitMember saved = entityManager.find(VisitMember.class, member.getId());
        assertEquals(savedName, saved.getName());
        assertNull(saved.getFamilyMember());
        createActiveGroupPlan(visit);
        var result = visitService.reuseVisit(visit.getOrganizer().getId(), visit.getId(), new ReuseVisitDtoIn(LocalDate.now().plusDays(3)));
        assertEquals(1, visitMemberRepository.findAllByVisit_Id(result.getVisitId()).size());
    }

    @Test
    void reusingFamilyRetainsExistingSavedFamilyMembers() {
        Visit visit = createVisit("FAMILY");
        FamilyMember family = createFamily(visit.getOrganizer());
        visitMemberService.addFamilyMembers(visit.getOrganizer().getId(), visit.getId(), new AddFamilyMembersDtoIn(List.of(family.getId())));
        createActiveGroupPlan(visit);
        var result = visitService.reuseVisit(visit.getOrganizer().getId(), visit.getId(), new ReuseVisitDtoIn(LocalDate.now().plusDays(3)));
        assertEquals(2, visitMemberRepository.findAllByVisit_Id(result.getVisitId()).size());
        assertNull(result.getInviteCode());
    }

    @Test
    void snapshotsUseCurrentProfileAtFirstGenerationThenRemainStable() {
        Visit visit = createVisit("SOLO");
        VisitMember member = visitMemberRepository.findAllByVisit_Id(visit.getId()).get(0);
        member.getVisitorProfile().setHeightCm(new BigDecimal("175"));
        visitMemberService.freezeMemberSnapshots(visit);
        member.getVisitorProfile().setHeightCm(new BigDecimal("185"));
        visitMemberService.freezeMemberSnapshots(visit);
        assertEquals(0, member.getHeightCm().compareTo(new BigDecimal("175")));
    }

    @Test
    void oneMealPreferenceRequestCreatesTwoSharedBreaksWithSeparateTimings() {
        Visit visit = createVisit("FAMILY");
        FamilyMember family = createFamily(visit.getOrganizer());
        visitMemberService.addFamilyMembers(visit.getOrganizer().getId(), visit.getId(), new AddFamilyMembersDtoIn(List.of(family.getId())));
        visitMealBreakService.saveMealPreferences(visit.getOrganizer().getId(), visit.getId(), new MealPreferencesDtoIn("TWO_MEALS", List.of("START", "END")));
        assertEquals(4, mealBreakRepository.findVisitMealBreaksByVisitId(visit.getId()).size());
        Map<String, Object> policy = visitMealBreakService.getMealBreakPolicy(visit.getOrganizer().getId(), visit.getId());
        assertEquals("TWO_MEALS", policy.get("choice"));
        List<?> definitions = (List<?>) policy.get("breaks");
        assertEquals(2, definitions.size());
        assertEquals(60, ((Map<?, ?>) definitions.get(0)).get("durationMinutes"));
        assertEquals(2, ((List<?>) ((Map<?, ?>) definitions.get(0)).get("participantVisitMemberIds")).size());
        assertThrows(ApiException.class, () -> visitMealBreakService.saveMealPreferences(visit.getOrganizer().getId(), visit.getId(), new MealPreferencesDtoIn("TWO_MEALS", List.of("START"))));
        assertEquals(4, mealBreakRepository.findVisitMealBreaksByVisitId(visit.getId()).size());
    }

    @Test
    void draftRestaurantSelectionPersistsInPlanningPolicyAndRejectsClosedPlaces() {
        Visit visit = createVisit("SOLO");
        Place restaurant = new Place();
        restaurant.setName("QA Restaurant");
        restaurant.setKind("RESTAURANT");
        restaurant.setStatus("OPEN");
        entityManager.persist(restaurant);
        MealPreferencesDtoIn dto = new MealPreferencesDtoIn("ONE_MEAL", List.of("MIDDLE"));
        dto.setRestaurantIds(List.of(restaurant.getId()));
        visitMealBreakService.saveMealPreferences(visit.getOrganizer().getId(), visit.getId(), dto);
        Map<String, Object> policy = visitMealBreakService.getMealBreakPolicy(visit.getOrganizer().getId(), visit.getId());
        assertEquals(restaurant.getId(), ((Map<?, ?>) ((List<?>) policy.get("breaks")).get(0)).get("restaurantId"));
        restaurant.setStatus("CLOSED");
        assertThrows(ApiException.class, () -> visitMealBreakService.saveMealPreferences(visit.getOrganizer().getId(), visit.getId(), dto));
        assertEquals(restaurant.getId(), mealBreakRepository.findVisitMealBreaksByVisitId(visit.getId()).get(0).getPlace().getId());
    }

    @Test
    void deferredFoodPreferenceIsExplicitAndDoesNotCreateAnActualFoodActivity() {
        Visit visit = createVisit("SOLO");
        visitMealBreakService.saveMealPreferences(visit.getOrganizer().getId(), visit.getId(), new MealPreferencesDtoIn("DEFERRED", List.of()));
        Map<String, Object> policy = visitMealBreakService.getMealBreakPolicy(visit.getOrganizer().getId(), visit.getId());
        assertEquals("DEFERRED", policy.get("choice"));
        assertTrue(((List<?>) policy.get("breaks")).isEmpty());
    }

    @Test
    void largePlanningSnapshotsKeepAccountHistoryOrderWithoutSortingJsonRows() {
        Visit visit = createVisit("SOLO");
        Account account = visit.getOrganizer();
        String snapshot = "{\"payload\":\"" + "x".repeat(150000) + "\"}";
        List<Integer> ids = new java.util.ArrayList<>();
        for (int index = 0; index < 3; index++) {
            PlanHistory history = new PlanHistory();
            history.setAccount(account);
            history.setVisit(visit);
            history.setRequestKey("large-snapshot-" + index);
            history.setOperationType("INITIAL");
            history.setStatus("FAILED");
            history.setBillingMode("FREE");
            history.setPlanningInputSnapshot(snapshot);
            history.setGenerationResultSnapshot(snapshot);
            entityManager.persist(history);
            ids.add(history.getId());
        }
        entityManager.flush();
        entityManager.clear();
        List<PlanHistory> histories = historyRepository.findAllByAccount_IdOrderByStartedAtDescIdDesc(account.getId());
        assertEquals(List.of(ids.get(2), ids.get(1), ids.get(0)), histories.stream().map(PlanHistory::getId).toList());
        assertTrue(histories.get(0).getPlanningInputSnapshot().contains("x".repeat(150000)));
        assertTrue(historyRepository.findAllByAccount_IdOrderByStartedAtDescIdDesc(-1).isEmpty());
    }

    @Test
    void freeReservationsCannotExceedTwoAndFailureReleasesQuota() {
        Visit firstVisit = createVisit("SOLO");
        Integer accountId = firstVisit.getOrganizer().getId();
        Visit secondVisit = createQuotaVisit(firstVisit.getOrganizer());
        Visit thirdVisit = createQuotaVisit(firstVisit.getOrganizer());
        Integer first = paymentOrderService.reserveEntitlement(accountId, firstVisit.getId(), "first", "INITIAL");
        assertEquals(first, paymentOrderService.reserveEntitlement(accountId, firstVisit.getId(), "first", "INITIAL"));
        paymentOrderService.reserveEntitlement(accountId, secondVisit.getId(), "second", "INITIAL");
        assertThrows(ApiException.class, () -> paymentOrderService.reserveEntitlement(accountId, thirdVisit.getId(), "third", "INITIAL"));
        paymentOrderService.releaseEntitlement(accountId, first);
        assertNotNull(paymentOrderService.reserveEntitlement(accountId, thirdVisit.getId(), "retry", "INITIAL"));
        assertFalse(historyRepository.findById(first).orElseThrow().getCountsTowardQuota());
    }

    @Test
    void cancelledUnstartedFreeVisitReleasesSlotButStartedVisitDoesNot() {
        Visit first = createVisit("SOLO");
        Integer accountId = first.getOrganizer().getId();
        Visit second = createQuotaVisit(first.getOrganizer());
        Visit third = createQuotaVisit(first.getOrganizer());
        paymentOrderService.reserveEntitlement(accountId, first.getId(), "cancel-one", "INITIAL");
        paymentOrderService.reserveEntitlement(accountId, second.getId(), "cancel-two", "INITIAL");
        assertThrows(ApiException.class, () -> paymentOrderService.reserveEntitlement(accountId, third.getId(), "cancel-three", "INITIAL"));
        visitService.cancel(accountId, first.getId());
        assertNotNull(paymentOrderService.reserveEntitlement(accountId, third.getId(), "cancel-after", "INITIAL"));
        assertTrue(Boolean.TRUE.equals(second.getFreeSlotReserved()));
    }

    @Test
    void copiedVisitIsFreeToReuseButGenerationRequiresPayment() {
        Visit original = createVisit("SOLO");
        createActiveGroupPlan(original);
        Integer accountId = original.getOrganizer().getId();
        var result = visitService.reuseVisit(accountId, original.getId(), new ReuseVisitDtoIn(LocalDate.now().plusDays(3)));
        Visit copied = entityManager.find(Visit.class, result.getVisitId());
        assertEquals(original.getId(), copied.getReusedFromVisitId());
        assertFalse(Boolean.TRUE.equals(copied.getFreeSlotReserved()));
        var access = paymentOrderService.getPlanningAccess(accountId, copied.getId());
        assertTrue(access.getPaymentRequired());
        assertThrows(ApiException.class, () -> paymentOrderService.reserveEntitlement(accountId, copied.getId(), "repeat-ai", "REGENERATE"));
        assertNotNull(entityManager.find(Visit.class, copied.getId()));
    }

    @Test
    void repeatedVisitMemberEditsRequireVerifiedPayment() {
        Visit original = createVisit("SOLO");
        createActiveGroupPlan(original);
        Integer accountId = original.getOrganizer().getId();
        var result = visitService.reuseVisit(accountId, original.getId(), new ReuseVisitDtoIn(LocalDate.now().plusDays(3)));
        assertThrows(ApiException.class, () -> visitMemberService.updateSelectedRides(accountId, result.getVisitId(), new SelectedRidesDtoIn()));
    }

    @Test
    void eligibilityUsesMemberSnapshotAndAgeOnVisitDate() {
        Visit visit = createVisit("SOLO");
        VisitMember member = visitMemberRepository.findAllByVisit_Id(visit.getId()).get(0);
        member.setBirthDate(visit.getVisitDate().minusYears(17).minusDays(1));
        member.setHeightCm(new BigDecimal("180"));
        Ride ride = new Ride();
        ride.setName("Age restricted ride");
        ride.setMinAgeYears(18);
        entityManager.persist(ride);
        assertEquals("NOT_ELIGIBLE", rideService.checkEligibilityForMember(visit.getOrganizer().getId(), member.getId(), ride.getId()).get("status"));
    }

    @Test
    void planningReadsSelectedRideObjectsWithoutMakingPreferredRidesMandatory() {
        Visit visit = createVisit("SOLO");
        VisitMember member = visitMemberRepository.findAllByVisit_Id(visit.getId()).get(0);
        member.setAnswerStatus("ANSWERED");
        member.setSelectedRides("[{\"rideId\":1,\"priority\":\"PREFERRED\"},{\"rideId\":2,\"priority\":\"MUST_RIDE\"}]");
        visitMemberService.freezeMemberSnapshots(visit);
        List<Map<String, Object>> contexts = org.springframework.test.util.ReflectionTestUtils.invokeMethod(visitPlanService, "buildGoingMemberPlanningContext", visit);
        assertNotNull(contexts);
        assertEquals(List.of(2), contexts.get(0).get("selectedRideIds"));
        tools.jackson.databind.JsonNode selections = (tools.jackson.databind.JsonNode) contexts.get(0).get("selectedRides");
        assertEquals("PREFERRED", selections.get(0).path("priority").asString());
    }

    @Test
    void walkingMinutesUseTheMapRouteDistanceAndConfiguredSpeed() {
        int previousEntrance = (Integer) org.springframework.test.util.ReflectionTestUtils.getField(mapService, "entranceNodeId");
        try {
            org.springframework.test.util.ReflectionTestUtils.setField(mapService, "entranceNodeId", 0);
            Ride from = new Ride();
            from.setName("Route start");
            from.setSourceRideId("adrena-line");
            from.setStatus("OPEN");
            entityManager.persist(from);
            Ride to = new Ride();
            to.setName("Route end");
            to.setSourceRideId("falcons-flight");
            to.setStatus("OPEN");
            entityManager.persist(to);
            double meters = mapService.getRoute("RIDE", from.getId(), "RIDE", to.getId()).getDistanceMeters();
            Map<String, Object> context = mapService.getWalkingContext();
            List<Map<String, Object>> routes = (List<Map<String, Object>>) context.get("routes");
            Map<String, Object> route = routes.stream().filter(row -> row.get("fromNodeKey").equals("RIDE:" + from.getId()) && row.get("toNodeKey").equals("RIDE:" + to.getId())).findFirst().orElseThrow();
            assertEquals((int) Math.ceil(meters / 70), route.get("minutes"));
            assertEquals(true, context.get("estimated"));
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(mapService, "entranceNodeId", previousEntrance);
        }
    }

    private Visit createQuotaVisit(Account account) {
        Visit visit = new Visit();
        visit.setOrganizer(account);
        visit.setTripType("SOLO");
        visit.setVisitDate(LocalDate.now().plusDays(2));
        visit.setMaxParticipants(1);
        visit.setName("Quota test visit");
        visit.setDurationMinutes(360);
        visit.setStatus("DRAFT");
        visit.setMembersLocked(false);
        entityManager.persist(visit);
        entityManager.flush();
        return visit;
    }

    private void createActiveGroupPlan(Visit visit) {
        VisitPlan group = new VisitPlan();
        group.setVisit(visit);
        group.setPlanKind("GROUP");
        group.setTimingMode("RELATIVE");
        group.setIsActive(true);
        entityManager.persist(group);
        entityManager.flush();
    }

    @Test
    void friendConfirmingAfterMealsAreSavedJoinsTheSameFoodBreak() {
        Visit visit = createVisit("FRIENDS");
        visitMealBreakService.saveMealPreferences(visit.getOrganizer().getId(), visit.getId(), new MealPreferencesDtoIn("ONE_MEAL", List.of("END")));
        VisitorProfile profile = createProfile(createAccount());
        VisitMember friend = new VisitMember();
        friend.setVisit(visit);
        friend.setVisitorProfile(profile);
        friend.setRsvpStatus("INVITED");
        entityManager.persist(friend);
        visitMemberService.updateRsvp(profile.getAccount().getId(), visit.getId(), 1);
        var policy = visitMealBreakService.getMealBreakPolicy(visit.getOrganizer().getId(), visit.getId());
        List<?> breaks = (List<?>) policy.get("breaks");
        assertEquals(1, breaks.size());
        assertEquals(2, ((List<?>) ((Map<?, ?>) breaks.get(0)).get("participantVisitMemberIds")).size());
    }

    @Test
    void togetherChoicesAndExclusionsAreSharedAndMembersCannotOverrideThem() {
        Visit visit = createVisit("FRIENDS");
        visit.setTogetherMode("TOGETHER");
        VisitorProfile profile = createProfile(createAccount());
        VisitMember friend = new VisitMember();
        friend.setVisit(visit);
        friend.setVisitorProfile(profile);
        friend.setRsvpStatus("GOING");
        entityManager.persist(friend);
        Ride ride = new Ride();
        ride.setName("Selection regression");
        ride.setStatus("OPEN");
        entityManager.persist(ride);
        VisitMember organizer = visitMemberRepository.findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(visit.getId(), visit.getOrganizer().getId());
        MemberPreferencesDtoIn dto = new MemberPreferencesDtoIn(List.of(), List.of());
        dto.setExcludedRideIds(List.of(ride.getId()));
        dto.setExcludedRideTypes(List.of("KIDS"));
        visitMemberService.saveMemberPreferences(visit.getOrganizer().getId(), organizer.getId(), dto, 1);
        assertEquals(organizer.getSelectedRides(), friend.getSelectedRides());
        assertEquals(organizer.getPreferences(), friend.getPreferences());
        assertEquals("ANSWERED", friend.getAnswerStatus());
        assertTrue(visitMemberService.getMemberPreferences(visit.getOrganizer().getId(), organizer.getId()).get("excludedRideIds").toString().contains(ride.getId().toString()));
        assertThrows(ApiException.class, () -> visitMemberService.saveMemberPreferences(profile.getAccount().getId(), friend.getId(), dto, 1));
    }

    @Test
    void failedGenerationUnlocksDraftForEditing() {
        Visit visit = createVisit("SOLO");
        Integer historyId = paymentOrderService.reserveEntitlement(visit.getOrganizer().getId(), visit.getId(), UUID.randomUUID().toString(), "INITIAL");
        visit.setStatus("PLANNING");
        visit.setMembersLocked(true);
        entityManager.flush();
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(visitPlanService, "failInitialGeneration", visit.getOrganizer().getId(), visit.getId(), historyId, "No feasible plan", null);
        assertEquals("DRAFT", visit.getStatus());
        assertFalse(visit.getMembersLocked());
        assertEquals("FAILED", historyRepository.findById(historyId).orElseThrow().getStatus());
    }

    @Test
    void existingFailedVisitCanSaveAmendedDetails() {
        Visit visit = createVisit("FAMILY");
        visit.setStatus("PLANNING");
        visit.setMembersLocked(false);
        CreateVisitDtoIn dto = new CreateVisitDtoIn();
        dto.setName("Amended visit");
        dto.setVisitDate(visit.getVisitDate());
        dto.setDurationMinutes(420);
        dto.setTripType("FAMILY");
        dto.setFamilyCount(3);
        dto.setGoals(visit.getGoals());
        dto.setPrimaryGoal(visit.getPrimaryGoal());
        dto.setIncludePrayer(false);
        Integer id = visit.getId();
        visitService.updateByOrganizer(visit.getOrganizer().getId(), id, dto);
        entityManager.flush();
        entityManager.clear();
        Visit saved = entityManager.find(Visit.class, id);
        assertEquals("DRAFT", saved.getStatus());
        assertEquals("Amended visit", saved.getName());
        assertEquals(420, saved.getDurationMinutes());
        assertEquals(4, saved.getMaxParticipants());
    }

    @Test
    void familyAdditionOnlyRaisesCapacityAfterExplicitConfirmationAndNeverDuplicates() {
        Visit visit = createVisit("FAMILY");
        visit.setMaxParticipants(2);
        FamilyMember first = createFamily(visit.getOrganizer());
        FamilyMember second = createFamily(visit.getOrganizer());
        Integer owner = visit.getOrganizer().getId();
        visitMemberService.addFamilyMembers(owner, visit.getId(), new AddFamilyMembersDtoIn(List.of(first.getId())));
        visitMemberService.addFamilyMembers(owner, visit.getId(), new AddFamilyMembersDtoIn(List.of(first.getId())));
        assertEquals(2, visitMemberRepository.findAllByVisit_Id(visit.getId()).size());
        assertTrue(assertThrows(ApiException.class, () -> visitMemberService.addFamilyMembers(owner, visit.getId(), new AddFamilyMembersDtoIn(List.of(second.getId())))).getMessage().contains("2"));
        assertEquals(2, visit.getMaxParticipants());
        visitMemberService.addFamilyMembers(owner, visit.getId(), new AddFamilyMembersDtoIn(List.of(second.getId()), 3));
        assertEquals(3, visit.getMaxParticipants());
        assertEquals(3, visitMemberRepository.findAllByVisit_Id(visit.getId()).size());
        assertTrue(visitMemberService.getSummary(owner, visit.getId()).getMembers().stream().anyMatch(member -> first.getId().equals(member.getFamilyMemberId())));
    }

    @Test
    void previewExplainsMissingMealsAndFoodDurationBeforeGeneration() {
        Visit visit = createVisit("SOLO");
        mealBreakRepository.deleteAll(mealBreakRepository.findVisitMealBreaksByVisitId(visit.getId()));
        entityManager.flush();
        var missing = visitService.getVisitPreview(visit.getOrganizer().getId(), visit.getId());
        assertFalse(missing.getReadyToGenerate());
        assertTrue(missing.getMissingFields().stream().anyMatch(field -> field.startsWith("meal:") && field.contains("احفظ")));
        visitMealBreakService.saveMealPreferences(visit.getOrganizer().getId(), visit.getId(), new MealPreferencesDtoIn("TWO_MEALS", List.of("MIDDLE", "END")));
        visit.setDurationMinutes(60);
        var conflicting = visitService.getVisitPreview(visit.getOrganizer().getId(), visit.getId());
        assertFalse(conflicting.getReadyToGenerate());
        assertTrue(conflicting.getMissingFields().stream().anyMatch(field -> field.contains("120") && field.contains("60")));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void missingMealsPreviewCommitsWithoutMarkingTheOuterTransactionRollbackOnly() {
        var manager = entityManager.getEntityManagerFactory();
        var transactionManager = new org.springframework.orm.jpa.JpaTransactionManager(manager);
        var template = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        Integer[] ids = template.execute(status -> {
            Visit visit = createVisit("SOLO");
            mealBreakRepository.deleteAll(mealBreakRepository.findVisitMealBreaksByVisitId(visit.getId()));
            entityManager.flush();
            return new Integer[]{visit.getOrganizer().getId(), visit.getId()};
        });
        var preview = assertDoesNotThrow(() -> visitService.getVisitPreview(ids[0], ids[1]));
        assertFalse(preview.getReadyToGenerate());
        assertTrue(preview.getMissingFields().stream().anyMatch(field -> field.startsWith("meal:")));
    }

    @Test
    void togetherVisitDoesNotRemindGoingMembersToChooseRides() {
        Visit visit = createVisit("FRIENDS");
        visit.setTogetherMode("TOGETHER");
        VisitMember member = new VisitMember();
        member.setVisit(visit);
        member.setVisitorProfile(createProfile(createAccount()));
        member.setRsvpStatus("GOING");
        member.setAnswerStatus("PENDING");
        entityManager.persist(member);
        var error = assertThrows(ApiException.class, () -> visitMemberService.remindVisitMember(visit.getOrganizer().getId(), member.getId()));
        assertTrue(error.getMessage().contains("المنظّم"));
    }

    private Visit createVisit(String type) {
        Account account = createAccount();
        createProfile(account);
        CreateVisitDtoIn dto = new CreateVisitDtoIn();
        dto.setName("Integration visit");
        dto.setVisitDate(LocalDate.now().plusDays(2));
        dto.setDurationMinutes(360);
        dto.setTripType(type);
        dto.setTogetherMode("FRIENDS".equals(type) ? "SPLIT" : null);
        dto.setMaxParticipants(5);
        dto.setFamilyCount("FAMILY".equals(type) ? 2 : null);
        dto.setGoals("[\"RIDES\"]");
        dto.setPrimaryGoal("RIDES");
        dto.setIncludePrayer(false);
        visitService.create(account.getId(), dto);
        return visitMemberRepository.findAllByVisitorProfile_Account_Id(account.getId()).get(0).getVisit();
    }

    private Account createAccount() {
        Account account = new Account();
        account.setName("Integration organizer");
        account.setEmail(UUID.randomUUID() + "@example.com");
        account.setPasswordHash("test-password");
        account.setStatus("ACTIVE");
        account.setEmailVerified(true);
        account.setRole("USER");
        entityManager.persist(account);
        return account;
    }

    private VisitorProfile createProfile(Account account) {
        VisitorProfile profile = new VisitorProfile();
        profile.setAccount(account);
        profile.setName(account.getName());
        profile.setBirthDate(LocalDate.of(2000, 1, 1));
        entityManager.persist(profile);
        return profile;
    }

    private FamilyMember createFamily(Account account) {
        FamilyMember family = new FamilyMember();
        family.setOwnerAccount(account);
        family.setName("Family participant");
        family.setBirthDate(LocalDate.of(2010, 1, 1));
        family.setGender("MALE");
        entityManager.persist(family);
        return family;
    }
}
