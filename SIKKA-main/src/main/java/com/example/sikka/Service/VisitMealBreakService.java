package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.VisitMealBreakDtoIn;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.Visit;
import com.example.sikka.Model.VisitMealBreak;
import com.example.sikka.Model.VisitMember;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.VisitMealBreakRepository;
import com.example.sikka.Repository.VisitMemberRepository;
import com.example.sikka.Repository.VisitRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.sikka.DtoIn.SharedMealBreakDtoIn;
import java.util.HashSet;
import com.example.sikka.DtoOut.VisitMealBreakDtoOut;
import java.util.ArrayList;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class VisitMealBreakService {

    private final VisitMealBreakRepository visitMealBreakRepository;
    private final VisitRepository visitRepository;
    private final PaymentOrderService paymentOrderService;
    private final VisitMemberRepository visitMemberRepository;
    private final AccountRepository accountRepository;
    private final com.example.sikka.Repository.PlanItemRepository planItemRepository;
    private final com.example.sikka.Repository.PlaceRepository placeRepository;

    private void requirePaidRepeatedEdit(Visit visit) {
        if (visit == null) throw new ApiException("Visit not found");
        if (visit.getReusedFromVisitId() != null && !paymentOrderService.hasPaidVisit(visit.getOrganizer().getId(), visit.getId())) {
            throw new ApiException("Pay before changing meal preferences of a repeated visit");
        }
    }

    @Transactional(readOnly = true)
    public List<VisitMealBreakDtoOut> getVisitMealBreaks(
            Integer accountId,
            Integer visitId){

        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())){
            throw new ApiException("Account is not active");
        }

        Visit visit = visitRepository.findVisitById(visitId);

        if (visit == null){
            throw new ApiException("Visit not found");
        }

        if (!visit.getOrganizer().getId().equals(accountId)){
            throw new ApiException("Only the organizer can view all visit meal breaks");
        }

        List<VisitMealBreak> mealBreaks =
                visitMealBreakRepository.findVisitMealBreaksByVisitId(visitId);

        List<VisitMealBreakDtoOut> result = new ArrayList<>();

        for (VisitMealBreak mealBreak : mealBreaks){
            VisitMealBreakDtoOut dto = new VisitMealBreakDtoOut();

            dto.setId(mealBreak.getId());
            dto.setBreakKey(mealBreak.getBreakKey());
            dto.setMealChoice(mealBreak.getMealChoice());
            dto.setPreferredTiming(mealBreak.getPreferredTiming());
            dto.setVisitId(mealBreak.getVisit().getId());
            dto.setVisitMemberId(mealBreak.getVisitMember().getId());

            if (mealBreak.getPlace() != null){
                dto.setPlaceId(mealBreak.getPlace().getId());
            }

            if (mealBreak.getPlanItem() != null){
                dto.setPlanItemId(mealBreak.getPlanItem().getId());
            }

            result.add(dto);
        }

        return result;
    }

    @Transactional
    public VisitMealBreak addVisitMealBreak(
            Integer accountId,
            Integer visitId,
            Integer visitMemberId,
            VisitMealBreakDtoIn dto){

        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())){
            throw new ApiException("Account is not active");
        }

        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);
        requirePaidRepeatedEdit(visit);

        if (visit == null){
            throw new ApiException("Visit not found");
        }

        if (!visit.getOrganizer().getId().equals(accountId)){
            throw new ApiException("Only the organizer can add meal choices through this operation");
        }

        if (!"DRAFT".equals(visit.getStatus())
                || Boolean.TRUE.equals(visit.getMembersLocked())){
            throw new ApiException("Meal choices can only be added before planning starts");
        }

        VisitMember visitMember = visitMemberRepository.findVisitMemberById(visitMemberId);

        if (visitMember == null){
            throw new ApiException("Visit member not found");
        }

        if (!visitMember.getVisit().getId().equals(visitId)){
            throw new ApiException("This member does not belong to this visit");
        }

        if (!"GOING".equals(visitMember.getRsvpStatus())){
            throw new ApiException("Meal choices are only available for going members");
        }

        if (dto == null || dto.getMealChoice() == null
                || !dto.getMealChoice().matches("^(MEAL|SNACK|DEFERRED|SKIPPED|NONE)$")){
            throw new ApiException("Invalid meal choice");
        }

        boolean foodBreak = "MEAL".equals(dto.getMealChoice())
                || "SNACK".equals(dto.getMealChoice());

        if (foodBreak){
            if (dto.getPreferredTiming() == null
                    || !dto.getPreferredTiming().matches("^(START|MIDDLE|END)$")){
                throw new ApiException("A meal or snack requires a valid preferred timing");
            }
        } else {
            if (dto.getPreferredTiming() != null){
                throw new ApiException("Deferred, skipped and no-food choices must not have a timing");
            }
        }

        List<VisitMealBreak> existingBreaks =
                visitMealBreakRepository.findVisitMealBreaksByVisitMemberId(visitMemberId);

        if (!foodBreak && !existingBreaks.isEmpty()){
            throw new ApiException("Replace the existing meal choices before selecting this option");
        }

        int mealCount = 0;
        int snackCount = 0;

        for (VisitMealBreak existingBreak : existingBreaks){
            if ("MEAL".equals(existingBreak.getMealChoice())){
                mealCount++;
            } else if ("SNACK".equals(existingBreak.getMealChoice())){
                snackCount++;
            } else {
                throw new ApiException("Replace the existing deferred, skipped or no-food choice first");
            }
        }

        if (foodBreak){
            if (mealCount + snackCount >= 2){
                throw new ApiException("Only two meals or one meal and one snack are supported");
            }

            if ("SNACK".equals(dto.getMealChoice()) && snackCount >= 1){
                throw new ApiException("Only one snack break is supported");
            }
        }

        VisitMealBreak mealBreak = new VisitMealBreak();
        mealBreak.setBreakKey(UUID.randomUUID().toString());
        mealBreak.setMealChoice(dto.getMealChoice());
        mealBreak.setPreferredTiming(dto.getPreferredTiming());
        mealBreak.setVisit(visit);
        mealBreak.setVisitMember(visitMember);

        return visitMealBreakRepository.save(mealBreak);    }

    @Transactional
    public void updateVisitMealBreak(
            Integer accountId,
            Integer visitId,
            Integer mealBreakId,
            VisitMealBreakDtoIn dto){

        // Checks that the account is active and owns the visit.
        getVisitMealBreaks(accountId, visitId);

        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);
        requirePaidRepeatedEdit(visit);

        if (visit == null){
            throw new ApiException("Visit not found");
        }

        if (!"DRAFT".equals(visit.getStatus())
                || Boolean.TRUE.equals(visit.getMembersLocked())){
            throw new ApiException("Meal choices can only be updated before planning starts");
        }

        VisitMealBreak mealBreak =
                visitMealBreakRepository.findVisitMealBreakById(mealBreakId);

        if (mealBreak == null){
            throw new ApiException("Visit meal break not found");
        }

        if (!mealBreak.getVisit().getId().equals(visitId)){
            throw new ApiException("This meal break does not belong to this visit");
        }

        if (!"GOING".equals(mealBreak.getVisitMember().getRsvpStatus())){
            throw new ApiException("Meal choices are only available for going members");
        }

        if (mealBreak.getPlanItem() != null){
            throw new ApiException("Cannot directly update a meal break linked to a plan");
        }

        List<VisitMealBreak> sharedBreaks =
                visitMealBreakRepository.findVisitMealBreaksByVisitIdAndBreakKey(
                        visitId, mealBreak.getBreakKey()
                );

        if (sharedBreaks.size() > 1){
            throw new ApiException("Shared meal breaks must be updated together");
        }

        if (dto == null || dto.getMealChoice() == null
                || !dto.getMealChoice().matches("^(MEAL|SNACK|DEFERRED|SKIPPED|NONE)$")){
            throw new ApiException("Invalid meal choice");
        }

        boolean foodBreak = "MEAL".equals(dto.getMealChoice())
                || "SNACK".equals(dto.getMealChoice());

        if (foodBreak){
            if (dto.getPreferredTiming() == null
                    || !dto.getPreferredTiming().matches("^(START|MIDDLE|END)$")){
                throw new ApiException("A meal or snack requires a valid preferred timing");
            }
        } else {
            if (dto.getPreferredTiming() != null){
                throw new ApiException("Deferred, skipped and no-food choices must not have a timing");
            }
        }

        List<VisitMealBreak> existingBreaks =
                visitMealBreakRepository.findVisitMealBreaksByVisitMemberId(
                        mealBreak.getVisitMember().getId()
                );

        int otherBreakCount = 0;
        int snackCount = 0;

        for (VisitMealBreak existingBreak : existingBreaks){
            if (existingBreak.getId().equals(mealBreakId)){
                continue;
            }

            otherBreakCount++;

            if (!foodBreak){
                throw new ApiException("Remove the other food break before selecting this option");
            }

            if ("SNACK".equals(existingBreak.getMealChoice())){
                snackCount++;
            } else if (!"MEAL".equals(existingBreak.getMealChoice())){
                throw new ApiException("Remove the conflicting meal choice first");
            }
        }

        if (foodBreak){
            if (otherBreakCount >= 2){
                throw new ApiException("Only two meals or one meal and one snack are supported");
            }

            if ("SNACK".equals(dto.getMealChoice()) && snackCount >= 1){
                throw new ApiException("Only one snack break is supported");
            }
        }

        mealBreak.setMealChoice(dto.getMealChoice());
        mealBreak.setPreferredTiming(dto.getPreferredTiming());

        // Restaurant selection happens later, at the food break.
        mealBreak.setPlace(null);

        visitMealBreakRepository.save(mealBreak);
    }

    @Transactional
    public void deleteVisitMealBreak(
            Integer accountId,
            Integer visitId,
            Integer mealBreakId){

        // Checks that the account is active and owns the visit.
        getVisitMealBreaks(accountId, visitId);

        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);
        requirePaidRepeatedEdit(visit);

        if (visit == null){
            throw new ApiException("Visit not found");
        }

        if (!"DRAFT".equals(visit.getStatus())
                || Boolean.TRUE.equals(visit.getMembersLocked())){
            throw new ApiException("Meal choices can only be deleted before planning starts");
        }

        VisitMealBreak mealBreak =
                visitMealBreakRepository.findVisitMealBreakById(mealBreakId);

        if (mealBreak == null){
            throw new ApiException("Visit meal break not found");
        }

        if (!mealBreak.getVisit().getId().equals(visitId)){
            throw new ApiException("This meal break does not belong to this visit");
        }

        if (mealBreak.getPlanItem() != null){
            throw new ApiException("Cannot directly delete a meal break linked to a plan");
        }

        List<VisitMealBreak> sharedBreaks =
                visitMealBreakRepository.findVisitMealBreaksByVisitIdAndBreakKey(
                        visitId, mealBreak.getBreakKey()
                );

        if (sharedBreaks.size() > 1){
            throw new ApiException("Shared meal breaks must be managed together");
        }

        visitMealBreakRepository.delete(mealBreak);
    }

    @Transactional(readOnly = true)
    public List<VisitMealBreakDtoOut> getMyMealBreaks(
            Integer accountId,
            Integer visitId){

        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())){
            throw new ApiException("Account is not active");
        }

        Visit visit = visitRepository.findVisitById(visitId);

        if (visit == null){
            throw new ApiException("Visit not found");
        }

        VisitMember visitMember =
                visitMemberRepository.findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(
                        visitId, accountId
                );

        if (visitMember == null){
            throw new ApiException("You are not a member of this visit");
        }

        List<VisitMealBreak> mealBreaks =
                visitMealBreakRepository.findVisitMealBreaksByVisitMemberId(
                        visitMember.getId()
                );

        List<VisitMealBreakDtoOut> result = new ArrayList<>();

        for (VisitMealBreak mealBreak : mealBreaks){
            VisitMealBreakDtoOut dto = new VisitMealBreakDtoOut();

            dto.setId(mealBreak.getId());
            dto.setBreakKey(mealBreak.getBreakKey());
            dto.setMealChoice(mealBreak.getMealChoice());
            dto.setPreferredTiming(mealBreak.getPreferredTiming());
            dto.setVisitId(mealBreak.getVisit().getId());
            dto.setVisitMemberId(mealBreak.getVisitMember().getId());

            if (mealBreak.getPlace() != null){
                dto.setPlaceId(mealBreak.getPlace().getId());
            }

            if (mealBreak.getPlanItem() != null){
                dto.setPlanItemId(mealBreak.getPlanItem().getId());
            }

            result.add(dto);
        }

        return result;
    }

    @Transactional
    public void addSharedMealBreak(
            Integer accountId,
            Integer visitId,
            SharedMealBreakDtoIn dto){

        if (dto == null || dto.getVisitMemberIds() == null
                || dto.getVisitMemberIds().isEmpty()){
            throw new ApiException("Visit member IDs are required");
        }

        if (new HashSet<>(dto.getVisitMemberIds()).size()
                != dto.getVisitMemberIds().size()){
            throw new ApiException("Visit member IDs must not be repeated");
        }

        for (Integer memberId : dto.getVisitMemberIds()){
            if (memberId == null || memberId <= 0){
                throw new ApiException("Visit member IDs must be positive");
            }
        }

        if (!"MEAL".equals(dto.getMealChoice())
                && !"SNACK".equals(dto.getMealChoice())){
            throw new ApiException("Shared break must be MEAL or SNACK");
        }

        if (dto.getPreferredTiming() == null
                || !dto.getPreferredTiming().matches("^(START|MIDDLE|END)$")){
            throw new ApiException("A valid preferred timing is required");
        }

        String breakKey = UUID.randomUUID().toString();

        VisitMealBreakDtoIn memberDto = new VisitMealBreakDtoIn();
        memberDto.setMealChoice(dto.getMealChoice());
        memberDto.setPreferredTiming(dto.getPreferredTiming());

        for (Integer memberId : dto.getVisitMemberIds()){
            VisitMealBreak mealBreak = addVisitMealBreak(
                    accountId, visitId, memberId, memberDto
            );

            mealBreak.setBreakKey(breakKey);
            visitMealBreakRepository.save(mealBreak);
        }
    }

    @Transactional
    public void deleteSharedMealBreak(
            Integer accountId,
            Integer visitId,
            String breakKey){

        // Checks that the account is active and owns the visit.
        getVisitMealBreaks(accountId, visitId);

        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);
        requirePaidRepeatedEdit(visit);

        if (visit == null){
            throw new ApiException("Visit not found");
        }

        if (!"DRAFT".equals(visit.getStatus())
                || Boolean.TRUE.equals(visit.getMembersLocked())){
            throw new ApiException("Meal choices can only be deleted before planning starts");
        }

        if (breakKey == null || breakKey.isBlank()){
            throw new ApiException("Break key is required");
        }

        List<VisitMealBreak> mealBreaks =
                visitMealBreakRepository.findVisitMealBreaksByVisitIdAndBreakKey(
                        visitId, breakKey
                );

        if (mealBreaks.isEmpty()){
            throw new ApiException("Meal break not found in this visit");
        }

        for (VisitMealBreak mealBreak : mealBreaks){
            if (mealBreak.getPlanItem() != null){
                throw new ApiException("Cannot directly delete a meal break linked to a plan");
            }
        }

        visitMealBreakRepository.deleteAll(mealBreaks);
    }

    @Transactional
    public void updateSharedMealBreak(
            Integer accountId,
            Integer visitId,
            String breakKey,
            VisitMealBreakDtoIn dto){

        // Checks that the account is active and owns the visit.
        getVisitMealBreaks(accountId, visitId);

        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);
        requirePaidRepeatedEdit(visit);

        if (visit == null){
            throw new ApiException("Visit not found");
        }

        if (!"DRAFT".equals(visit.getStatus())
                || Boolean.TRUE.equals(visit.getMembersLocked())){
            throw new ApiException("Meal choices can only be updated before planning starts");
        }

        if (breakKey == null || breakKey.isBlank()){
            throw new ApiException("Break key is required");
        }

        if (dto == null || (!"MEAL".equals(dto.getMealChoice())
                && !"SNACK".equals(dto.getMealChoice()))){
            throw new ApiException("Shared break must be MEAL or SNACK");
        }

        if (dto.getPreferredTiming() == null
                || !dto.getPreferredTiming().matches("^(START|MIDDLE|END)$")){
            throw new ApiException("A valid preferred timing is required");
        }

        List<VisitMealBreak> sharedBreaks =
                visitMealBreakRepository.findVisitMealBreaksByVisitIdAndBreakKey(
                        visitId, breakKey
                );

        if (sharedBreaks.isEmpty()){
            throw new ApiException("Meal break not found in this visit");
        }

        for (VisitMealBreak mealBreak : sharedBreaks){
            if (mealBreak.getPlanItem() != null){
                throw new ApiException("Cannot directly update a meal break linked to a plan");
            }

            if (!"GOING".equals(mealBreak.getVisitMember().getRsvpStatus())){
                throw new ApiException("All shared break participants must be going");
            }

            List<VisitMealBreak> memberBreaks =
                    visitMealBreakRepository.findVisitMealBreaksByVisitMemberId(
                            mealBreak.getVisitMember().getId()
                    );

            int otherBreakCount = 0;
            int snackCount = 0;

            for (VisitMealBreak memberBreak : memberBreaks){
                if (memberBreak.getId().equals(mealBreak.getId())){
                    continue;
                }

                otherBreakCount++;

                if ("SNACK".equals(memberBreak.getMealChoice())){
                    snackCount++;
                } else if (!"MEAL".equals(memberBreak.getMealChoice())){
                    throw new ApiException("A participant has a conflicting meal choice");
                }
            }

            if (otherBreakCount >= 2){
                throw new ApiException("A participant would exceed the allowed food breaks");
            }

            if ("SNACK".equals(dto.getMealChoice()) && snackCount >= 1){
                throw new ApiException("A participant already has another snack break");
            }
        }

        for (VisitMealBreak mealBreak : sharedBreaks){
            mealBreak.setMealChoice(dto.getMealChoice());
            mealBreak.setPreferredTiming(dto.getPreferredTiming());
            mealBreak.setPlace(null);
        }

        visitMealBreakRepository.saveAll(sharedBreaks);
    }

    @Transactional
    public void saveMealPreferences(Integer accountId, Integer visitId, com.example.sikka.DtoIn.MealPreferencesDtoIn dto) {
        getVisitMealBreaks(accountId, visitId);
        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);
        requirePaidRepeatedEdit(visit);
        if (!"DRAFT".equals(visit.getStatus()) || Boolean.TRUE.equals(visit.getMembersLocked())) {
            throw new ApiException("Meal preferences can only be saved before planning; deferred answers during the visit require schedule preparation");
        }
        java.util.Map<String, List<String>> choices = java.util.Map.of("NONE", List.of(), "SKIPPED", List.of(), "DEFERRED", List.of(), "ONE_MEAL", List.of("MEAL"), "TWO_MEALS", List.of("MEAL", "MEAL"), "TWO_SNACKS", List.of("SNACK", "SNACK"), "SNACK", List.of("SNACK"), "MEAL_AND_SNACK", List.of("MEAL", "SNACK"));
        if (dto == null || !choices.containsKey(dto.getChoice())) {
            throw new ApiException("A valid meal preference is required");
        }
        List<String> types = choices.get(dto.getChoice());
        List<String> timings = dto.getTimings() == null ? List.of() : dto.getTimings();
        if (timings.size() != types.size() || timings.stream().anyMatch(timing -> timing == null || !java.util.Set.of("START", "MIDDLE", "END").contains(timing))) {
            throw new ApiException("Provide one valid timing for each food break");
        }
        List<Integer> restaurantIds = dto.getRestaurantIds() == null ? List.of() : dto.getRestaurantIds();
        if (!restaurantIds.isEmpty() && restaurantIds.size() != types.size()) {
            throw new ApiException("Provide one restaurant selection for each food break");
        }
        List<com.example.sikka.Model.Place> restaurants = new ArrayList<>();
        for (Integer restaurantId : restaurantIds) {
            com.example.sikka.Model.Place restaurant = restaurantId == null ? null : placeRepository.findPlaceById(restaurantId);
            if (restaurantId != null && (restaurant == null || !"RESTAURANT".equals(restaurant.getKind()) || !PlanningAvailability.canPlanPlace(restaurant))) {
                throw new ApiException("Choose an open restaurant");
            }
            restaurants.add(restaurant);
        }
        List<VisitMember> members = visitMemberRepository.findAllByVisit_IdAndRsvpStatus(visitId, "GOING");
        if (members.isEmpty()) {
            throw new ApiException("At least one GOING participant is required");
        }
        List<VisitMealBreak> previous = visitMealBreakRepository.findVisitMealBreaksByVisitId(visitId);
        if (previous.stream().anyMatch(meal -> meal.getPlanItem() != null)) {
            throw new ApiException("Generated food breaks cannot be overwritten directly");
        }
        visitMealBreakRepository.deleteAll(previous);
        for (int index = 0; index < Math.max(1, types.size()); index++) {
            String breakKey = UUID.randomUUID().toString();
            for (VisitMember member : members) {
                VisitMealBreak meal = new VisitMealBreak();
                meal.setVisit(visit);
                meal.setVisitMember(member);
                meal.setBreakKey(breakKey);
                meal.setMealChoice(types.isEmpty() ? dto.getChoice() : types.get(index));
                meal.setPreferredTiming(types.isEmpty() ? null : timings.get(index));
                meal.setPlace(restaurants.isEmpty() ? null : restaurants.get(index));
                visitMealBreakRepository.save(meal);
            }
        }
    }

    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public java.util.Map<String, Object> getMealBreakPolicy(Integer accountId, Integer visitId) {
        getVisitMealBreaks(accountId, visitId);
        Visit visit = visitRepository.findVisitById(visitId);
        List<VisitMember> members = visitMemberRepository.findAllByVisit_IdAndRsvpStatus(visitId, "GOING");
        List<VisitMealBreak> rows = visitMealBreakRepository.findVisitMealBreaksByVisitId(visitId);
        String choice = null;
        java.util.Map<String, List<VisitMealBreak>> grouped = new java.util.LinkedHashMap<>();
        for (VisitMember member : members) {
            List<VisitMealBreak> own = rows.stream().filter(meal -> meal.getVisitMember().getId().equals(member.getId())).toList();
            long meals = own.stream().filter(meal -> "MEAL".equals(meal.getMealChoice())).count();
            long snacks = own.stream().filter(meal -> "SNACK".equals(meal.getMealChoice())).count();
            String memberChoice;
            if (own.size() == 1 && java.util.Set.of("NONE", "SKIPPED", "DEFERRED").contains(own.get(0).getMealChoice())) {
                memberChoice = own.get(0).getMealChoice();
            } else if (meals + snacks == own.size() && meals <= 2 && snacks <= 2 && own.size() <= 2 && !own.isEmpty()) {
                memberChoice = snacks == 2 ? "TWO_SNACKS" : meals == 2 ? "TWO_MEALS" : meals == 1 && snacks == 1 ? "MEAL_AND_SNACK" : meals == 1 ? "ONE_MEAL" : "SNACK";
            } else {
                throw new ApiException("احفظ تفضيلات الوجبات لكل المشاركين، حتى لو اخترت بدون أكل؛ تأكد أن الاختيارات محفوظة ومتسقة للجميع.");
            }
            if (choice != null && !choice.equals(memberChoice)) {
                throw new ApiException("The shared visit must use the same food break choice for all participants");
            }
            choice = memberChoice;
            for (VisitMealBreak meal : own) {
                if (java.util.Set.of("MEAL", "SNACK").contains(meal.getMealChoice())) {
                    grouped.computeIfAbsent(meal.getBreakKey(), key -> new ArrayList<>()).add(meal);
                }
            }
        }
        if (choice == null || visit.getDurationMinutes() == null || visit.getDurationMinutes() <= 0) {
            throw new ApiException("Participants, meal preferences and visit duration are required");
        }
        int foodMinutes = grouped.values().stream().mapToInt(group -> "MEAL".equals(group.get(0).getMealChoice()) ? 60 : 30).sum();
        if (foodMinutes > 0 && foodMinutes >= visit.getDurationMinutes()) throw new ApiException("مدة زيارتك " + visit.getDurationMinutes() + " دقيقة، والوجبات والسناكات تحتاج " + foodMinutes + " دقيقة، قبل حساب الألعاب والمشي والصلاة. قلّل عدد الوجبات أو زِد مدة الزيارة.");
        List<java.util.Map<String, Object>> breaks = new ArrayList<>();
        for (java.util.Map.Entry<String, List<VisitMealBreak>> entry : grouped.entrySet()) {
            VisitMealBreak meal = entry.getValue().get(0);
            int duration = "MEAL".equals(meal.getMealChoice()) ? 60 : 30;
            if (visit.getDurationMinutes() < duration) {
                throw new ApiException("Increase the visit duration or choose fewer food breaks");
            }
            java.util.Map<String, Object> definition = new java.util.LinkedHashMap<>();
            definition.put("breakKey", entry.getKey());
            definition.put("breakType", meal.getMealChoice());
            definition.put("timingPreference", meal.getPreferredTiming());
            definition.put("durationMinutes", duration);
            definition.put("earliestStartMinutes", 0);
            definition.put("latestStartMinutes", visit.getDurationMinutes() - duration);
            definition.put("restaurantId", meal.getPlace() == null ? null : meal.getPlace().getId());
            definition.put("participantVisitMemberIds", entry.getValue().stream().map(row -> row.getVisitMember().getId()).sorted().toList());
            breaks.add(definition);
        }
        return java.util.Map.of("ready", true, "choice", choice, "breaks", breaks);
    }

    @Transactional
    public void bindGeneratedBreak(Integer accountId, Integer visitId, String breakKey, Integer planItemId) {
        getVisitMealBreaks(accountId, visitId);
        com.example.sikka.Model.PlanItem item = planItemRepository.findPlanItemById(planItemId);
        if (item == null || !item.getVisitPlan().getVisit().getId().equals(visitId)) {
            throw new ApiException("Generated meal activity must belong to the visit");
        }
        List<VisitMealBreak> rows = visitMealBreakRepository.findVisitMealBreaksByVisitIdAndBreakKey(visitId, breakKey);
        if (rows.isEmpty()) {
            throw new ApiException("Saved food break not found");
        }
        for (VisitMealBreak meal : rows) {
            meal.setPlanItem(item);
        }
        visitMealBreakRepository.saveAll(rows);
    }

}