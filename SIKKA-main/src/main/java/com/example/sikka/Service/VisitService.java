package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.CreateVisitDtoIn;
import com.example.sikka.DtoIn.VisitDtoIn;
import com.example.sikka.DtoOut.PreviewDtoOut;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.Visit;
import com.example.sikka.Model.VisitGroup;
import com.example.sikka.Model.VisitMember;
import com.example.sikka.Model.VisitorProfile;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.VisitGroupRepository;
import com.example.sikka.Repository.VisitMemberRepository;
import com.example.sikka.Repository.VisitRepository;
import com.example.sikka.Repository.VisitorProfileRepository;
import lombok.RequiredArgsConstructor;
import com.example.sikka.DtoIn.ReuseVisitDtoIn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.sikka.DtoOut.PlanningAccessDtoOut;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.example.sikka.Model.PlanItem;
import com.example.sikka.Model.VisitPlan;
import com.example.sikka.Repository.VisitPlanRepository;
import java.time.LocalDateTime;
import com.example.sikka.DtoIn.UpdateVisitArrivalDtoIn;
import com.example.sikka.Repository.PlanItemRepository;
import java.util.Objects;
import com.example.sikka.DtoIn.ReuseVisitDtoIn;
import com.example.sikka.DtoOut.ReuseVisitDtoOut;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Comparator;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VisitService {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private final NotificationEventService notificationEventService;
    private final VisitMealBreakService visitMealBreakService;
    private final VisitRepository visitRepository;
    private final AccountRepository accountRepository;
    private final VisitGroupRepository visitGroupRepository;
    private final PlanItemRepository planItemRepository;
    private final VisitMemberRepository visitMemberRepository;
    private final VisitorProfileRepository visitorProfileRepository;
    private final VisitMemberService visitMemberService;
    private final VisitGroupService visitGroupService;
    private final PaymentOrderService paymentOrderService;
    private final JsonMapper planningJsonMapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final VisitPlanRepository visitPlanRepository;
    private final com.example.sikka.Repository.PlanApprovalRepository planApprovalRepository;
    private final com.example.sikka.Repository.VisitMealBreakRepository visitMealBreakRepository;
    private final com.example.sikka.Repository.PlanHistoryRepository planHistoryRepository;
    private final VisitPlanService visitPlanService;
    private final org.springframework.context.ApplicationEventPublisher calendarEventPublisher;

    // ---------- CRUD ----------

    public List<Visit> get() {
        return visitRepository.findAll();
    }

    @Transactional
    public void add(VisitDtoIn dto) {
        if (dto == null) {
            throw new ApiException("Visit details are required");
        }
        Account organizer = accountRepository.findAccountById(dto.getOrganizerId());
        if (organizer == null)
            throw new ApiException("organizer not found");

        checkTimes(dto.getPlannedStartTime(), dto.getPlannedEndTime());

        VisitorProfile profile = visitorProfileRepository.findVisitorProfileByAccount_Id(organizer.getId());
        if (profile == null) {
            throw new ApiException("create your visitor profile first");
        }
        validateVisitDetails(dto.getVisitDate(), dto.getMaxParticipants());
        if (!"ACTIVE".equals(organizer.getStatus())) {
            throw new ApiException("Account is not active");
        }

        Visit visit = new Visit();
        if (dto.getGroupId() != null) {
            VisitGroup group = visitGroupRepository.findVisitGroupById(dto.getGroupId());
            if (group == null)
                throw new ApiException("group not found");
            if (!group.getOrganizer().getId().equals(organizer.getId()))
                throw new ApiException("group belongs to another organizer");
            if (group.getVisit() != null)
                throw new ApiException("group is already linked to a visit");
            visit.setGroup(group);
        }

        visit.setOrganizer(organizer);
        visit.setName(dto.getName());
        visit.setVisitDate(dto.getVisitDate());
        visit.setPlannedStartTime(dto.getPlannedStartTime());
        visit.setPlannedEndTime(dto.getPlannedEndTime());
        visit.setMaxParticipants(dto.getMaxParticipants());
        visit.setStatus("DRAFT");
        visit.setMembersLocked(false);
        visitRepository.save(visit);
        visitMemberService.addOrganizer(visit, profile);
    }

    @Transactional
    public void update(Integer id, VisitDtoIn dto) {
        if (dto == null) {
            throw new ApiException("Visit details are required");
        }
        Visit visit = visitRepository.findVisitByIdForUpdate(id);
        if (visit == null)
            throw new ApiException("visit not found");

        checkTimes(dto.getPlannedStartTime(), dto.getPlannedEndTime());

        if (!"DRAFT".equals(visit.getStatus()) || Boolean.TRUE.equals(visit.getMembersLocked())) {
            throw new ApiException("Visit details can only be changed while the visit is in DRAFT");
        }
        validateVisitDetails(dto.getVisitDate(), dto.getMaxParticipants());

        if (visit.getReusedFromVisitId() != null && !paymentOrderService.hasPaidVisit(visit.getOrganizer().getId(), visit.getId())) {
            if (!Objects.equals(dto.getName(), visit.getName()) || !Objects.equals(dto.getMaxParticipants(), visit.getMaxParticipants())) throw new ApiException("Pay before changing repeated visit details");
        }

        if (dto.getMaxParticipants() < visitMemberRepository.countByVisit(visit))
            throw new ApiException("max participants is less than current members");

        visit.setName(dto.getName());
        visit.setVisitDate(dto.getVisitDate());
        visit.setPlannedStartTime(dto.getPlannedStartTime());
        visit.setPlannedEndTime(dto.getPlannedEndTime());
        visit.setMaxParticipants(dto.getMaxParticipants());
        visitRepository.save(visit);
    }

    @Transactional
    public void delete(Integer accountId, Integer visitId) {
        Account account = accountRepository.findAccountById(accountId);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("An active account is required");
        }
        Visit reference = visitRepository.findVisitById(visitId);
        if (reference == null) {
            throw new ApiException("Visit not found");
        }
        Integer ownerId = reference.getOrganizer().getId();
        if (!ownerId.equals(accountId) && !"ADMIN".equals(account.getRole())) {
            throw new ApiException("Only the organizer or an admin can delete this visit");
        }
        entityManager.createNativeQuery("SELECT id FROM account WHERE id = :ownerId FOR UPDATE").setParameter("ownerId", ownerId).getSingleResult();
        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);
        if (visit == null) {
            throw new ApiException("Visit not found");
        }
        entityManager.refresh(visit, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!visit.getOrganizer().getId().equals(ownerId)) {
            throw new ApiException("The visit organizer changed; try again");
        }
        Number processing = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM plan_history WHERE visit_id = :visitId AND status = 'PROCESSING'").setParameter("visitId", visitId).getSingleResult();
        if (processing.longValue() > 0) {
            throw new ApiException("Wait for the current plan generation to finish before deleting the visit");
        }
        Integer groupId = visit.getGroup() == null ? null : visit.getGroup().getId();
        long usedFreeVisit = Boolean.TRUE.equals(visit.getFreeSlotReserved()) && visit.getStartedAt() != null ? 1L : 0L;
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE account SET archived_free_visits = COALESCE(archived_free_visits, 0) + :used WHERE id = :ownerId").setParameter("used", usedFreeVisit).setParameter("ownerId", ownerId).executeUpdate();
        entityManager.createNativeQuery("UPDATE payment_order SET consumed_by_plan_history_id = NULL WHERE consumed_by_plan_history_id IN (SELECT id FROM plan_history WHERE visit_id = :visitId)").setParameter("visitId", visitId).executeUpdate();
        entityManager.createNativeQuery("UPDATE payment_order SET visit_id = NULL WHERE visit_id = :visitId").setParameter("visitId", visitId).executeUpdate();
        entityManager.createNativeQuery("UPDATE plan_history SET source_visit_id = NULL WHERE source_visit_id = :visitId").setParameter("visitId", visitId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM notification WHERE visit_id = :visitId OR plan_item_id IN (SELECT i.id FROM plan_item i JOIN visit_plan p ON p.id = i.visit_plan_id WHERE p.visit_id = :visitId) OR activity_session_id IN (SELECT s.id FROM activity_session s JOIN visit_member m ON m.id = s.visit_member_id WHERE m.visit_id = :visitId)").setParameter("visitId", visitId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM visit_meal_break WHERE visit_id = :visitId").setParameter("visitId", visitId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM visit_record WHERE visit_member_id IN (SELECT id FROM visit_member WHERE visit_id = :visitId)").setParameter("visitId", visitId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM activity_session WHERE visit_member_id IN (SELECT id FROM visit_member WHERE visit_id = :visitId)").setParameter("visitId", visitId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM plan_approval WHERE visit_plan_id IN (SELECT id FROM visit_plan WHERE visit_id = :visitId)").setParameter("visitId", visitId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM plan_item WHERE visit_plan_id IN (SELECT id FROM visit_plan WHERE visit_id = :visitId)").setParameter("visitId", visitId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM visit_plan WHERE visit_id = :visitId").setParameter("visitId", visitId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM plan_history WHERE visit_id = :visitId").setParameter("visitId", visitId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM visit_member WHERE visit_id = :visitId").setParameter("visitId", visitId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM visit WHERE id = :visitId").setParameter("visitId", visitId).executeUpdate();
        if (groupId != null) {
            entityManager.createNativeQuery("DELETE FROM visit_group WHERE id = :groupId").setParameter("groupId", groupId).executeUpdate();
        }
        entityManager.clear();
    }

    // ---------- Flow 1 extras ----------

    // creates the visit and adds the organizer as GOING
    // returns the raw invite code for a FRIENDS trip, or null for SOLO and FAMILY
    @Transactional
    public String create(Integer accountId, CreateVisitDtoIn dto) {
        if (dto == null) {
            throw new ApiException("Visit details are required");
        }
        ParkOperatingHours.validateVisitWindow(dto.getVisitDate(), dto.getPlannedStartTime(), dto.getDurationMinutes());
        int max = resolveMaxParticipants(dto);
        validateVisitDetails(dto.getVisitDate(), max);
        Account organizer = accountRepository.findAccountById(accountId);
        if (organizer == null)
            throw new ApiException("account not found");

        if (!Boolean.TRUE.equals(organizer.getEmailVerified()))
            throw new ApiException("verify your account first");

        if (!"ACTIVE".equals(organizer.getStatus())) {
            throw new ApiException("Account is not active");
        }
        VisitorProfile profile = visitorProfileRepository.findVisitorProfileByAccount_Id(accountId);
        if (profile == null)
            throw new ApiException("create your visitor profile first");

        checkTimes(dto.getPlannedStartTime(), dto.getPlannedEndTime());

        Visit visit = new Visit();
        visit.setOrganizer(organizer);
        fillVisit(visit, dto, max);
        visit.setStatus("DRAFT");
        visit.setMembersLocked(false);
        visitRepository.save(visit);

        visitMemberService.addOrganizer(visit, profile);

        // only a FRIENDS trip has a group and an invite code
        if ("FRIENDS".equals(dto.getTripType())) {
            String groupName = dto.getGroupName();
            if (groupName == null || groupName.isBlank())
                groupName = (dto.getName() == null || dto.getName().isBlank()) ? "Friends group" : dto.getName();
            return visitGroupService.attachGroup(visit, groupName);
        }
        return null;
    }

    // every visit the account is a member of (the organizer is a member too)
    public List<Visit> myVisits(Integer accountId) {
        if (accountRepository.findAccountById(accountId) == null)
            throw new ApiException("account not found");

        List<Visit> visits = new ArrayList<>();
        for (VisitMember m : visitMemberRepository.findAllByVisitorProfile_Account_Id(accountId))
            visits.add(m.getVisit());
        return visits;
    }
    // visit details, only for members of the visit
    public Visit getVisitDetails(Integer accountId, Integer visitId) {
        Visit visit = visitRepository.findVisitById(visitId);
        if (visit == null)
            throw new ApiException("visit not found");
        if (visitMemberRepository.findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(visitId, accountId) == null)
            throw new ApiException("you are not a member of this visit");
        return visit;
    }

    // organizer edits the visit details (same body as create, groupName is ignored)
    @Transactional
    public void updateByOrganizer(Integer accountId, Integer visitId, CreateVisitDtoIn dto) {
        if (dto == null)
            throw new ApiException("Visit details are required");

        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);
        if (visit == null)
            throw new ApiException("visit not found");
        if (!visit.getOrganizer().getId().equals(accountId))
            throw new ApiException("only the organizer can do this");

        checkTimes(dto.getPlannedStartTime(), dto.getPlannedEndTime());

        if ("PLANNING".equals(visit.getStatus()) && !Boolean.TRUE.equals(visit.getMembersLocked()) && visitPlanRepository.findAllByVisit_Id(visitId).isEmpty() && planHistoryRepository.findAllByAccount_IdOrderByStartedAtDescIdDesc(accountId).stream().noneMatch(history -> history.getVisit().getId().equals(visitId) && "PROCESSING".equals(history.getStatus()))) {
            visit.setStatus("DRAFT");
        }
        if (!"DRAFT".equals(visit.getStatus()) || Boolean.TRUE.equals(visit.getMembersLocked()))
            throw new ApiException("تعديل التفاصيل متاح قبل توليد الخطة؛ بعد الاعتماد استخدم تعديل الموعد والوجبات من الخطة.");

        if (visit.getReusedFromVisitId() != null && !paymentOrderService.hasPaidVisit(accountId, visitId)) {
            if (!Objects.equals(visit.getName(), dto.getName()) || !Objects.equals(visit.getTripType(), dto.getTripType()) || !Objects.equals(visit.getMaxParticipants(), resolveMaxParticipants(dto)) || !Objects.equals(visit.getDurationMinutes(), dto.getDurationMinutes()) || !Objects.equals(visit.getGoals(), dto.getGoals()) || !Objects.equals(visit.getPrimaryGoal(), dto.getPrimaryGoal()) || !Objects.equals(visit.getTogetherMode(), dto.getTogetherMode()) || !Objects.equals(visit.getIncludePrayer(), dto.getIncludePrayer())) throw new ApiException("Payment is required before changing repeated visit preferences");
        }

        if (visit.getTripType() != null && !visit.getTripType().equals(dto.getTripType()))
            throw new ApiException("trip type cannot be changed, create a new visit");

        ParkOperatingHours.validateVisitWindow(dto.getVisitDate(), dto.getPlannedStartTime(), dto.getDurationMinutes());
        int max = resolveMaxParticipants(dto);
        validateVisitDetails(dto.getVisitDate(), max);

        if (max < visitMemberRepository.countByVisit(visit))
            throw new ApiException("max participants is less than current members");

        fillVisit(visit, dto, max);
        visitRepository.save(visit);
    }

    @Transactional
    public void cancel(Integer accountId, Integer visitId) {
        checkOrganizer(accountId, visitId);
        changeStatus(visitId, "CANCELLED");
        calendarEventPublisher.publishEvent(new CalendarSyncOnApproval.CancelledVisitChanged(accountId, visitId));
    }

    @Transactional
    public void startVisit(Integer accountId, Integer visitId) {

        if (accountId == null || accountId <= 0 || visitId == null || visitId <= 0) {
            throw new ApiException("Positive account and visit IDs are required");
        }

        Account account = accountRepository.findAccountById(accountId);

        if (account == null) {
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("Account is not active");
        }

        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);

        if (visit == null) {
            throw new ApiException("Visit not found");
        }

        if (!visit.getOrganizer().getId().equals(accountId)) {
            throw new ApiException("Only the visit organizer can start the visit");
        }

        if (!"READY".equals(visit.getStatus())) {
            throw new ApiException("The visit must be READY before starting");
        }

        if (!LocalDate.now(ZoneId.of("Asia/Riyadh")).equals(visit.getVisitDate())) {
            throw new ApiException("The visit can only start on its scheduled date");
        }

        if (visit.getPlannedStartTime() == null || visit.getPlannedEndTime() == null) {
            throw new ApiException("Set the arrival time and prepare the schedule before starting");
        }

        VisitPlan activeGroupPlan = null;

        for (VisitPlan plan : visitPlanRepository.findAllByVisit_IdAndPlanKind(visitId, "GROUP")) {

            if (!Boolean.TRUE.equals(plan.getIsActive())) {
                continue;
            }

            if (activeGroupPlan != null) {
                throw new ApiException("Multiple active group plans were found");
            }

            activeGroupPlan = plan;
        }

        if (activeGroupPlan == null || activeGroupPlan.getPlanApproval() == null || !"APPROVED".equals(activeGroupPlan.getPlanApproval().getStatus())) {
            throw new ApiException("An active approved group plan is required");
        }

        if (!"SCHEDULED".equals(activeGroupPlan.getTimingMode())) {
            throw new ApiException("Prepare actual activity times before starting the visit");
        }

        if (activeGroupPlan.getProposedItems() != null || activeGroupPlan.getPendingScope() != null) {
            throw new ApiException("Resolve the pending plan proposal before starting");
        }

        if (activeGroupPlan.getPlanItems() == null || activeGroupPlan.getPlanItems().isEmpty()) {
            throw new ApiException("An empty plan cannot be started");
        }

        for (PlanItem item : activeGroupPlan.getPlanItems()) {

            if (item.getStartsAt() == null || item.getEndsAt() == null || !item.getEndsAt().isAfter(item.getStartsAt())) {
                throw new ApiException("All activities must have valid scheduled times");
            }
        }

        if (visit.getReusedFromVisitId() == null && !Boolean.TRUE.equals(visit.getFreeSlotReserved()) && !paymentOrderService.hasPaidVisit(accountId, visitId)) {
            throw new ApiException("Visit payment or free entitlement is required");
        }
        ParkOperatingHours.validateVisitWindow(visit.getVisitDate(), java.time.LocalTime.now(ZoneId.of("Asia/Riyadh")), 1);
        changeStatus(visitId, "IN_PROGRESS");

        visit.setStartedAt(LocalDateTime.now(ZoneId.of("Asia/Riyadh")));

        visitRepository.save(visit);
    }

    @Transactional
    public void completeVisit(Integer accountId, Integer visitId) {
        checkOrganizer(accountId, visitId);
        changeStatus(visitId, "COMPLETED");
    }

    // ---------- contract methods for the other flows ----------

    public Visit getVisit(Integer visitId) {
        Visit visit = visitRepository.findVisitById(visitId);
        if (visit == null)
            throw new ApiException("visit not found");
        return visit;
    }

    public boolean isOrganizer(Integer visitId, Integer accountId) {
        Visit visit = visitRepository.findVisitById(visitId);
        return visit != null && visit.getOrganizer().getId().equals(accountId);
    }

    // the only place where Visit.status changes
    @Transactional
    public void changeStatus(Integer visitId, String status) {
        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);
        if (visit == null)
            throw new ApiException("visit not found");

        if (status == null || visit.getStatus() == null) {
            throw new ApiException("Visit status is required");
        }
        if (!isAllowed(visit.getStatus(), status))
            throw new ApiException("cannot change status from " + visit.getStatus() + " to " + status);

        visit.setStatus(status);
        visitRepository.save(visit);
        notificationEventService.notifyVisitStatus(visit);
    }

    // called when plan generation starts, members list becomes fixed
    @Transactional
    public void lockMembers(Integer visitId) {
        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);
        if (visit == null)
            throw new ApiException("visit not found");
        if (!visit.getStatus().equals("PLANNING"))
            throw new ApiException("visit is not in planning");

        visit.setMembersLocked(true);
        visitRepository.save(visit);
    }

    // ---------- helpers ----------

    private boolean isAllowed(String from, String to) {
        return switch (from) {
            case "DRAFT" -> to.equals("PLANNING") || to.equals("CANCELLED");
            case "PLANNING" -> to.equals("READY") || to.equals("CANCELLED");
            case "READY" -> to.equals("IN_PROGRESS") || to.equals("CANCELLED");
            case "IN_PROGRESS" -> to.equals("COMPLETED");
            default -> false;
        };
    }

    private void checkOrganizer(Integer accountId, Integer visitId) {
        Visit visit = visitRepository.findVisitById(visitId);
        if (visit == null)
            throw new ApiException("visit not found");
        if (!visit.getOrganizer().getId().equals(accountId))
            throw new ApiException("only the organizer can do this");
    }

    private void validateVisitDetails(java.time.LocalDate visitDate, Integer maxParticipants) {
        if (visitDate == null || visitDate.isBefore(java.time.LocalDate.now())) {
            throw new ApiException("Visit date must be today or later");
        }
        if (maxParticipants == null || maxParticipants <= 0) {
            throw new ApiException("Maximum participants must be positive");
        }
    }

    private void checkTimes(LocalTime start, LocalTime end) {
        if (start != null && end != null && !end.isAfter(start))
            throw new ApiException("end time must be after start time");
    }

    @Transactional(readOnly = true)
    public List<PreviewDtoOut.MemberPreview> getPlanningMemberPreviews(Integer accountId, Integer visitId) {

        if (accountId == null || accountId <= 0 || visitId == null || visitId <= 0) {
            throw new ApiException("Positive account and visit IDs are required");
        }

        Account account = accountRepository.findAccountById(accountId);

        if (account == null) {
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("Account is not active");
        }

        Visit visit = visitRepository.findVisitById(visitId);

        if (visit == null) {
            throw new ApiException("Visit not found");
        }

        if (!visit.getOrganizer().getId().equals(accountId)) {
            throw new ApiException("Only the visit organizer can preview planning details");
        }

        List<VisitMember> members = visitMemberRepository.findAllByVisit_IdAndRsvpStatus(visitId, "GOING");
        members.sort(Comparator.comparing(VisitMember::getId));

        List<PreviewDtoOut.MemberPreview> results = new ArrayList<>();

        for (VisitMember member : members) {
            results.add(createPlanningMemberPreview(member));
        }

        return results;
    }

    private PreviewDtoOut.MemberPreview createPlanningMemberPreview(VisitMember member) {

        boolean hasProfile = member.getVisitorProfile() != null;
        boolean hasFamilyMember = member.getFamilyMember() != null;

        if (hasProfile == hasFamilyMember) {
            throw new ApiException("A visit member must reference exactly one person identity");
        }

        List<String> missingFields = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String name;
        LocalDate birthDate;

        if (hasProfile) {

            VisitorProfile profile = member.getVisitorProfile();

            name = member.getSnapshotAt() == null ? profile.getName() : member.getName();
            birthDate = member.getSnapshotAt() == null ? profile.getBirthDate() : member.getBirthDate();

            if (!"ACTIVE".equals(profile.getAccount().getStatus())) {
                missingFields.add("activeAccount");
            }

            if ((member.getSnapshotAt() == null ? profile.getHeightCm() : member.getHeightCm()) == null) {
                warnings.add("Height is missing; height-based ride eligibility may be unknown");
            }

            if ((member.getSnapshotAt() == null ? profile.getWeightKg() : member.getWeightKg()) == null) {
                warnings.add("Weight is missing; weight-based ride eligibility may be unknown");
            }

            if (birthDate == null) {
                warnings.add("Birth date is missing; age is unknown and the member must not be assumed to be an adult");
            }

        } else {

            name = member.getSnapshotAt() == null ? member.getFamilyMember().getName() : member.getName();
            birthDate = member.getSnapshotAt() == null ? member.getFamilyMember().getBirthDate() : member.getBirthDate();


            if (birthDate == null) {
                missingFields.add("birthDate");
            }

            if ((member.getSnapshotAt() == null ? member.getFamilyMember().getHeightCm() : member.getHeightCm()) == null) {
                warnings.add("Height is missing; height-based ride eligibility may be unknown");
            }

            if ((member.getSnapshotAt() == null ? member.getFamilyMember().getWeightKg() : member.getWeightKg()) == null) {
                warnings.add("Weight is missing; weight-based ride eligibility may be unknown");
            }
        }

        if (name == null || name.isBlank()) {
            missingFields.add("name");
        }

        LocalDate visitDate = member.getVisit().getVisitDate();

        if (birthDate != null && visitDate != null && birthDate.isAfter(visitDate)) {
            missingFields.add("validBirthDate");
        }

        String answerStatus = member.getAnswerStatus();

        if (answerStatus == null || "PENDING".equals(answerStatus)) {
            answerStatus = "PENDING";
            missingFields.add("planningAnswers");
        } else if ("SKIPPED".equals(answerStatus)) {
            warnings.add("Planning answers were skipped; available profile information will be used");
        } else if (!"ANSWERED".equals(answerStatus)) {
            throw new ApiException("Invalid member answer status");
        }

        return new PreviewDtoOut.MemberPreview(member.getId(), name, answerStatus, missingFields, warnings);
    }

    @Transactional(readOnly = true)
    public PreviewDtoOut getVisitPreview(Integer accountId, Integer visitId) {

        List<PreviewDtoOut.MemberPreview> memberPreviews = getPlanningMemberPreviews(accountId, visitId);

        Visit visit = visitRepository.findVisitById(visitId);

        if (visit == null) {
            throw new ApiException("Visit not found");
        }

        List<String> missingFields = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!"DRAFT".equals(visit.getStatus()) && !"PLANNING".equals(visit.getStatus()) && !"READY".equals(visit.getStatus())) {
            missingFields.add("visitStatus");
            warnings.add("Generation preview is only available before the visit starts");
        }

        if (visit.getVisitDate() == null || visit.getVisitDate().isBefore(LocalDate.now(ZoneId.of("Asia/Riyadh")))) {
            missingFields.add("visitDate");
        }

        String tripType = visit.getTripType();

        if (!"SOLO".equals(tripType) && !"FRIENDS".equals(tripType) && !"FAMILY".equals(tripType)) {
            missingFields.add("tripType");
        }

        if (visit.getDurationMinutes() == null || visit.getDurationMinutes() <= 0) {
            missingFields.add("durationMinutes");
        }

        if (visit.getIncludePrayer() == null) {
            missingFields.add("includePrayer");
        }

        if ("FRIENDS".equals(tripType) && (visit.getTogetherMode() == null || visit.getTogetherMode().isBlank())) {
            missingFields.add("togetherMode");
        }

        validatePreviewGoals(visit, missingFields);

        if (memberPreviews.isEmpty()) {
            missingFields.add("goingMembers");
        }

        if (visit.getMaxParticipants() == null || visit.getMaxParticipants() <= 0 || memberPreviews.size() > visit.getMaxParticipants()) {
            missingFields.add("maxParticipants");
        }

        if ("SOLO".equals(tripType) && memberPreviews.size() != 1) {
            missingFields.add("soloMemberCount");
        }

        for (PreviewDtoOut.MemberPreview member : memberPreviews) {

            for (String missingField : member.getMissingFields()) {
                missingFields.add("members[" + member.getVisitMemberId() + "]." + missingField);
            }

            for (String warning : member.getWarnings()) {
                warnings.add("Member " + member.getVisitMemberId() + ": " + warning);
            }
        }

        try {
            visitMealBreakService.getMealBreakPolicy(accountId, visitId);
        } catch (ApiException failure) {
            missingFields.add("meal:" + failure.getMessage());
        }

        PlanningAccessDtoOut access = paymentOrderService.getPlanningAccess(accountId, visitId);

        if (access == null || access.getFreeRemaining() == null || access.getFreeRemaining() < 0 || access.getPaymentRequired() == null || access.getGenerationAllowed() == null || access.getEntitlementStatus() == null || access.getEntitlementStatus().isBlank()) {
            throw new ApiException("Planning entitlement information is unavailable or invalid");
        }

        if (access.getPriceSar() != null && access.getPriceSar().signum() < 0) {
            throw new ApiException("Planning price must not be negative");
        }

        if (Boolean.TRUE.equals(access.getPaymentRequired()) && Boolean.TRUE.equals(access.getGenerationAllowed())) {
            throw new ApiException("Planning entitlement information is inconsistent");
        }

        if (Boolean.TRUE.equals(access.getPaymentRequired())) {
            warnings.add("A new generation purchase is required");

            if (access.getPriceSar() == null) {
                warnings.add("The generation price is not available yet");
            }
        } else if (!Boolean.TRUE.equals(access.getGenerationAllowed())) {
            warnings.add("Generation is currently unavailable according to the entitlement service");
        }

        boolean readyToGenerate = missingFields.isEmpty() && Boolean.TRUE.equals(access.getGenerationAllowed());

        return new PreviewDtoOut(visit.getId(), visit.getVisitDate(), tripType, visit.getDurationMinutes(), memberPreviews, missingFields, warnings, access.getFreeRemaining(), access.getPaymentRequired(), access.getPriceSar(), access.getEntitlementStatus(), readyToGenerate);
    }

    private void validatePreviewGoals(Visit visit, List<String> missingFields) {

        String primaryGoal = visit.getPrimaryGoal();

        if (primaryGoal == null || primaryGoal.isBlank()) {
            missingFields.add("primaryGoal");
        }

        if (visit.getGoals() == null || visit.getGoals().isBlank()) {
            missingFields.add("goals");
            return;
        }

        JsonNode goalsNode;

        try {
            goalsNode = planningJsonMapper.readTree(visit.getGoals());
        } catch (JacksonException exception) {
            missingFields.add("validGoals");
            return;
        }

        if (goalsNode == null || !goalsNode.isArray() || goalsNode.isEmpty()) {
            missingFields.add("validGoals");
            return;
        }

        HashSet<String> goals = new HashSet<>();

        for (JsonNode goalNode : goalsNode) {

            if (!goalNode.isTextual() || goalNode.asText().isBlank()) {
                missingFields.add("validGoals");
                return;
            }

            if (!goals.add(goalNode.asText())) {
                missingFields.add("uniqueGoals");
                return;
            }
        }

        if (primaryGoal != null && !primaryGoal.isBlank() && !goals.contains(primaryGoal)) {
            missingFields.add("primaryGoalInGoals");
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void updateVisitArrival(Integer accountId, Integer visitId, UpdateVisitArrivalDtoIn dto) {
        visitPlanService.updateVisitArrival(accountId, visitId, dto);
    }

    @Transactional
    public ReuseVisitDtoOut reuseVisit(Integer accountId, Integer sourceVisitId, ReuseVisitDtoIn dto) {

        if (accountId == null || accountId <= 0 || sourceVisitId == null || sourceVisitId <= 0) {
            throw new ApiException("Positive account and source visit IDs are required");
        }

        if (dto == null || dto.getVisitDate() == null || dto.getVisitDate().isBefore(LocalDate.now(ZoneId.of("Asia/Riyadh")))) {
            throw new ApiException("Visit date must be today or later");
        }


        Account account = accountRepository.findAccountById(accountId);

        if (account == null) {
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("Account is not active");
        }

        Visit sourceVisit = visitRepository.findVisitByIdForUpdate(sourceVisitId);

        if (sourceVisit == null) {
            throw new ApiException("Source visit not found");
        }

        if (!sourceVisit.getOrganizer().getId().equals(accountId)) {
            throw new ApiException("Only the source visit organizer can reuse its settings and members");
        }

        if (sourceVisit.getDurationMinutes() == null || sourceVisit.getDurationMinutes() <= 0) {
            throw new ApiException("Visit duration must be positive");
        }

        String tripType = sourceVisit.getTripType();

        if (!"SOLO".equals(tripType) && !"FRIENDS".equals(tripType) && !"FAMILY".equals(tripType)) {
            throw new ApiException("The source visit must have a valid trip type");
        }

        if (sourceVisit.getMaxParticipants() == null || sourceVisit.getMaxParticipants() <= 0) {
            throw new ApiException("The source visit must have a positive participant capacity");
        }

        VisitorProfile organizerProfile = visitorProfileRepository.findVisitorProfileByAccount_Id(accountId);

        if (organizerProfile == null) {
            throw new ApiException("Create your visitor profile first");
        }

        Visit newVisit = new Visit();
        newVisit.setOrganizer(account);
        newVisit.setReusedFromVisitId(sourceVisit.getId());
        newVisit.setName(sourceVisit.getName());
        newVisit.setVisitDate(dto.getVisitDate());
        newVisit.setMaxParticipants(sourceVisit.getMaxParticipants());
        newVisit.setTripType(tripType);
        newVisit.setDurationMinutes(sourceVisit.getDurationMinutes());
        newVisit.setGoals(sourceVisit.getGoals());
        newVisit.setPrimaryGoal(sourceVisit.getPrimaryGoal());
        newVisit.setTogetherMode(sourceVisit.getTogetherMode());
        newVisit.setIncludePrayer(sourceVisit.getIncludePrayer());
        newVisit.setSharedSelectedRides(sourceVisit.getSharedSelectedRides());
        newVisit.setPlannedStartTime(null);
        newVisit.setPlannedEndTime(null);
        newVisit.setStartedAt(null);
        newVisit.setStatus("DRAFT");
        newVisit.setMembersLocked(false);

        visitRepository.save(newVisit);

        LocalDateTime createdAt = LocalDateTime.now(ZoneId.of("Asia/Riyadh"));

        VisitMember organizerMember = new VisitMember();
        organizerMember.setVisit(newVisit);
        organizerMember.setVisitorProfile(organizerProfile);
        organizerMember.setName(organizerProfile.getName());
        organizerMember.setBirthDate(organizerProfile.getBirthDate());
        organizerMember.setHeightCm(organizerProfile.getHeightCm());
        organizerMember.setWeightKg(organizerProfile.getWeightKg());
        organizerMember.setGender(organizerProfile.getGender());
        organizerMember.setRsvpStatus("GOING");
        organizerMember.setAnswerStatus("PENDING");
        organizerMember.setInvitedAt(createdAt);

        VisitMember sourceOrganizer = visitMemberRepository.findVisitMemberByVisit_IdAndVisitorProfile_Account_Id(sourceVisitId, accountId);

        if (sourceOrganizer != null) {
            organizerMember.setSelectedRides(sourceOrganizer.getSelectedRides());
            organizerMember.setPreferences(sourceOrganizer.getPreferences());
        }

        List<String> warnings = new ArrayList<>();
        List<VisitMember> newMembers = new ArrayList<>();

        newMembers.add(organizerMember);

        HashSet<Integer> copiedProfileIds = new HashSet<>();
        HashSet<Integer> copiedFamilyIds = new HashSet<>();

        copiedProfileIds.add(organizerProfile.getId());

        boolean hasInvitedMembers = false;

        if ("FAMILY".equals(tripType) || "FRIENDS".equals(tripType)) {

            List<VisitMember> sourceMembers = visitMemberRepository.findAllByVisit_IdAndRsvpStatus(sourceVisitId, "GOING");
            sourceMembers.sort(Comparator.comparing(VisitMember::getId));

            for (VisitMember sourceMember : sourceMembers) {

                VisitMember copiedMember = copyReusableVisitMember(accountId, sourceMember, newVisit, copiedProfileIds, copiedFamilyIds, warnings, createdAt);

                if (copiedMember == null) {
                    continue;
                }

                if ("INVITED".equals(copiedMember.getRsvpStatus())) {
                    hasInvitedMembers = true;
                }

                newMembers.add(copiedMember);
            }
        }

        if (newMembers.size() > newVisit.getMaxParticipants()) {
            throw new ApiException("The copied members exceed the source participant capacity");
        }

        visitMemberRepository.saveAll(newMembers);

        String inviteCode = null;

        boolean needsGroup = "FRIENDS".equals(tripType);

        if (needsGroup) {

            String groupName = sourceVisit.getGroup() == null ? newVisit.getName() : sourceVisit.getGroup().getName();

            if (groupName == null || groupName.isBlank()) {
                groupName = "Sikka visit";
            }

            inviteCode = visitGroupService.attachGroup(newVisit, groupName);
        }

        if (hasInvitedMembers) {
            warnings.add("Account members must confirm participation in the new visit");
        }

        java.util.Map<Integer, PlanItem> copiedItems = copyReusablePlans(sourceVisit, newVisit, newMembers);
        copyReusableMeals(sourceVisit, newVisit, newMembers, copiedItems);
        warnings.add("Review members and arrival schedule on the visit day");

        return new ReuseVisitDtoOut(newVisit.getId(), sourceVisit.getId(), newVisit.getStatus(), inviteCode, warnings);
    }

    private java.util.Map<Integer, PlanItem> copyReusablePlans(Visit sourceVisit, Visit newVisit, List<VisitMember> newMembers) {
        java.util.Map<Integer, PlanItem> copiedItems = new java.util.HashMap<>();
        List<VisitPlan> activePlans = visitPlanRepository.findAllByVisit_Id(sourceVisit.getId()).stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive())).toList();
        if (activePlans.stream().noneMatch(p -> "GROUP".equals(p.getPlanKind()))) {
            throw new ApiException("Source visit must have an active group plan to repeat without AI");
        }
        com.example.sikka.Model.PlanHistory reuse = new com.example.sikka.Model.PlanHistory();
        reuse.setAccount(newVisit.getOrganizer());
        reuse.setVisit(newVisit);
        reuse.setRequestKey(java.util.UUID.randomUUID().toString());
        reuse.setOperationType("REUSE");
        reuse.setStatus("SUCCEEDED");
        reuse.setBillingMode("FREE");
        reuse.setCountsTowardQuota(false);
        reuse.setCompletedAt(LocalDateTime.now(ZoneId.of("Asia/Riyadh")));
        planHistoryRepository.save(reuse);
        for (VisitPlan oldPlan : activePlans) {
            VisitPlan plan = new VisitPlan();
            plan.setGeneratedByHistory(reuse);
            plan.setVisit(newVisit);
            plan.setPlanKind(oldPlan.getPlanKind());
            plan.setTimingMode("RELATIVE");
            plan.setIsActive(true);
            if (oldPlan.getOwnerVisitMember() != null) {
                VisitMember oldOwner = oldPlan.getOwnerVisitMember();
                VisitMember owner = newMembers.stream()
                        .filter(m -> oldOwner.getVisitorProfile() != null && m.getVisitorProfile() != null && oldOwner.getVisitorProfile().getId().equals(m.getVisitorProfile().getId())
                                || oldOwner.getFamilyMember() != null && m.getFamilyMember() != null && oldOwner.getFamilyMember().getId().equals(m.getFamilyMember().getId()))
                        .findFirst().orElseThrow(() -> new ApiException("Repeat requires the same visit members"));
                plan.setOwnerVisitMember(owner);
            }
            visitPlanRepository.save(plan);
            for (PlanItem oldItem : planItemRepository.findAllByVisitPlan_Id(oldPlan.getId())) {
                PlanItem item = new PlanItem();
                item.setVisitPlan(plan);
                item.setActivityKind(oldItem.getActivityKind());
                item.setTitle(oldItem.getTitle());
                item.setRelativeStartMinutes(oldItem.getRelativeStartMinutes());
                item.setRelativeEndMinutes(oldItem.getRelativeEndMinutes());
                item.setWalkingMinutes(oldItem.getWalkingMinutes());
                item.setWaitingMinutes(oldItem.getWaitingMinutes());
                item.setActivityMinutes(oldItem.getActivityMinutes());
                item.setBreakType(oldItem.getBreakType());
                item.setIsRateable(oldItem.getIsRateable());
                item.setSortOrder(oldItem.getSortOrder());
                item.setPriority(oldItem.getPriority());
                item.setPinned(oldItem.getPinned());
                item.setSubgroupKey(oldItem.getSubgroupKey());
                item.setRide(oldItem.getRide());
                item.setPlace(oldItem.getPlace());
                item.setParticipantVisitMemberIds(remapParticipantIds(oldItem.getParticipantVisitMemberIds(), sourceVisit, newMembers));
                planItemRepository.save(item);
                copiedItems.put(oldItem.getId(), item);
            }
            com.example.sikka.Model.PlanApproval approval = new com.example.sikka.Model.PlanApproval();
            approval.setVisitPlan(plan);
            approval.setApprovalScope(oldPlan.getPlanKind());
            approval.setStatus("PENDING");
            approval.setRequiredApproverAccount(newVisit.getOrganizer());
            planApprovalRepository.save(approval);
        }
        return copiedItems;
    }

    private void copyReusableMeals(Visit sourceVisit, Visit newVisit, List<VisitMember> newMembers, java.util.Map<Integer, PlanItem> copiedItems) {
        for (com.example.sikka.Model.VisitMealBreak oldBreak : visitMealBreakRepository.findVisitMealBreaksByVisitId(sourceVisit.getId())) {
            VisitMember oldMember = oldBreak.getVisitMember();
            VisitMember copied = newMembers.stream().filter(m -> oldMember.getVisitorProfile() != null && m.getVisitorProfile() != null && oldMember.getVisitorProfile().getId().equals(m.getVisitorProfile().getId()) || oldMember.getFamilyMember() != null && m.getFamilyMember() != null && oldMember.getFamilyMember().getId().equals(m.getFamilyMember().getId())).findFirst().orElseThrow(() -> new ApiException("Repeat requires the same meal participants"));
            com.example.sikka.Model.VisitMealBreak b = new com.example.sikka.Model.VisitMealBreak();
            b.setVisit(newVisit);
            b.setVisitMember(copied);
            b.setBreakKey(oldBreak.getBreakKey());
            b.setMealChoice(oldBreak.getMealChoice());
            b.setPreferredTiming(oldBreak.getPreferredTiming());
            b.setPlace(oldBreak.getPlace());
            if (oldBreak.getPlanItem() != null) {
                PlanItem mapped = copiedItems.get(oldBreak.getPlanItem().getId());
                if (mapped == null) throw new ApiException("Cannot reuse meal schedule without its copied plan item");
                b.setPlanItem(mapped);
            }
            visitMealBreakRepository.save(b);
        }
    }

    private String remapParticipantIds(String json, Visit sourceVisit, List<VisitMember> newMembers) {
        if (json == null || json.isBlank()) return json;
        try {
            JsonNode ids = planningJsonMapper.readTree(json);
            if (!ids.isArray()) throw new ApiException("Invalid source plan participant IDs");
            List<Integer> copiedIds = new ArrayList<>();
            for (JsonNode id : ids) {
                VisitMember old = visitMemberRepository.findVisitMemberById(id.asInt());
                if (old == null || !old.getVisit().getId().equals(sourceVisit.getId())) throw new ApiException("Source plan references an unknown member");
                VisitMember copied = newMembers.stream()
                        .filter(m -> old.getVisitorProfile() != null && m.getVisitorProfile() != null && old.getVisitorProfile().getId().equals(m.getVisitorProfile().getId())
                                || old.getFamilyMember() != null && m.getFamilyMember() != null && old.getFamilyMember().getId().equals(m.getFamilyMember().getId()))
                        .findFirst().orElseThrow(() -> new ApiException("Every planned participant must be copied"));
                copiedIds.add(copied.getId());
            }
            return planningJsonMapper.writeValueAsString(copiedIds);
        } catch (JacksonException e) {
            throw new ApiException("Unable to copy plan participants");
        }
    }

    private VisitMember copyReusableVisitMember(Integer accountId, VisitMember sourceMember, Visit newVisit, HashSet<Integer> copiedProfileIds, HashSet<Integer> copiedFamilyIds, List<String> warnings, LocalDateTime createdAt) {

        boolean hasProfile = sourceMember.getVisitorProfile() != null;
        boolean hasFamilyMember = sourceMember.getFamilyMember() != null;

        if (!hasProfile && !hasFamilyMember) {
            return null;
        }
        if (hasProfile && hasFamilyMember) {
            throw new ApiException("A source visit member must reference exactly one person identity");
        }

        VisitorProfile profile = sourceMember.getVisitorProfile();

        if (hasFamilyMember) {

            if (!sourceMember.getFamilyMember().getOwnerAccount().getId().equals(accountId)) {
                warnings.add("Member " + sourceMember.getId() + " was not copied because its family record belongs to another account");
                return null;
            }


            profile = sourceMember.getFamilyMember().getLinkedVisitorProfile();
        }

        VisitMember copiedMember = new VisitMember();
        copiedMember.setVisit(newVisit);
        copiedMember.setAnswerStatus("PENDING");
        copiedMember.setInvitedAt(createdAt);

        if (profile != null) {

            if (!"ACTIVE".equals(profile.getAccount().getStatus())) {
                warnings.add("Member " + sourceMember.getId() + " was not copied because its account is not active");
                return null;
            }

            if (!copiedProfileIds.add(profile.getId())) {
                return null;
            }

            copiedMember.setVisitorProfile(profile);
            copiedMember.setRsvpStatus("INVITED");

        } else {

            Integer familyMemberId = sourceMember.getFamilyMember().getId();

            if (!copiedFamilyIds.add(familyMemberId)) {
                return null;
            }

            copiedMember.setFamilyMember(sourceMember.getFamilyMember());
            copiedMember.setRsvpStatus("GOING");
            copiedMember.setSelectedRides(sourceMember.getSelectedRides());
            copiedMember.setPreferences(sourceMember.getPreferences());
        }

        if (copiedMember.getVisitorProfile() != null) {
            copiedMember.setName(profile.getName());
            copiedMember.setBirthDate(profile.getBirthDate());
            copiedMember.setHeightCm(profile.getHeightCm());
            copiedMember.setWeightKg(profile.getWeightKg());
            copiedMember.setGender(profile.getGender());
        } else {
            copiedMember.setName(sourceMember.getFamilyMember().getName());
            copiedMember.setBirthDate(sourceMember.getFamilyMember().getBirthDate());
            copiedMember.setHeightCm(sourceMember.getFamilyMember().getHeightCm());
            copiedMember.setWeightKg(sourceMember.getFamilyMember().getWeightKg());
            copiedMember.setGender(sourceMember.getFamilyMember().getGender());
        }
        return copiedMember;
    }


    // checks the rules of each trip type and returns the real maxParticipants
    private int resolveMaxParticipants(CreateVisitDtoIn dto) {
        String type = dto.getTripType();
        if ("SOLO".equals(type)) {
            if (dto.getTogetherMode() != null || dto.getFamilyCount() != null)
                throw new ApiException("SOLO trip has no group mode or family count");
            return 1;
        }
        if ("FRIENDS".equals(type)) {
            if (dto.getTogetherMode() == null)
                throw new ApiException("group mode is required for FRIENDS trip");
            if (dto.getFamilyCount() != null)
                throw new ApiException("family count is only for FAMILY trip");
            if (dto.getMaxParticipants() == null)
                throw new ApiException("max participants is required for FRIENDS trip");
            return dto.getMaxParticipants();
        }
        if ("FAMILY".equals(type)) {
            if (dto.getTogetherMode() != null)
                throw new ApiException("group mode is only for FRIENDS trip");
            if (dto.getFamilyCount() == null)
                throw new ApiException("family count is required for FAMILY trip");
            return dto.getFamilyCount() + 1;
        }
        throw new ApiException("invalid trip type");
    }

    // copies the visit details from the dto (used by create and update)
    private void fillVisit(Visit visit, CreateVisitDtoIn dto, int max) {
        visit.setDurationMinutes(dto.getDurationMinutes());
        visit.setName(dto.getName());
        visit.setVisitDate(dto.getVisitDate());
        visit.setExpectedArrivalTime(dto.getPlannedStartTime());
        visit.setPlannedStartTime(null);
        visit.setPlannedEndTime(null);
        visit.setMaxParticipants(max);
        visit.setTripType(dto.getTripType());
        visit.setTogetherMode(dto.getTogetherMode());
        visit.setGoals(dto.getGoals());
        visit.setPrimaryGoal(dto.getPrimaryGoal());
        visit.setIncludePrayer(Boolean.TRUE.equals(dto.getIncludePrayer()));
    }


}