package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoIn.UpdateRideStatusDtoIn;
import com.example.sikka.DtoIn.UpdateRideWaitDtoIn;
import com.example.sikka.DtoOut.RideWaitTimeDtoOut;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.Ride;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.RideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.sikka.Model.VisitorProfile;
import com.example.sikka.Repository.VisitorProfileRepository;
import java.util.HashSet;
import com.example.sikka.Model.VisitMember;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Set;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RideService {

    private final RideRepository rideRepository;
    private final com.example.sikka.Repository.VisitMemberRepository visitMemberRepository;
    private final VisitorProfileRepository visitorProfileRepository;
    private final AccountRepository accountRepository;
    private final PrivacyConsentService privacyConsentService;
    private final JsonMapper jsonMapper;
    private static final Set<String> HEALTH_CONDITION_CODES = Set.of(
            "HEART_CONDITION",
            "HIGH_BLOOD_PRESSURE",
            "BACK_CONDITION",
            "NECK_CONDITION",
            "PREGNANCY",
            "RECENT_SURGERY",
            "MOTION_SICKNESS",
            "VERTIGO",
            "FEAR_OF_HEIGHTS",
            "ARM_OR_LEG_CAST",
            "UNDER_INFLUENCE"
    );

    @org.springframework.beans.factory.annotation.Value("${sikka.park.wait-freshness-minutes:15}")
    private long waitFreshnessMinutes = 15;

    @org.springframework.beans.factory.annotation.Value("${sikka.park.default-planning-wait-minutes:15}")
    private int defaultPlanningWaitMinutes = 15;

    public List<Ride> getRides() {
        return rideRepository.findAll();
    }

    public Ride getRideById(Integer id) {
        Ride ride = rideRepository.findRideById(id);

        if (ride == null) {
            throw new ApiException("Ride not found");
        }

        return ride;
    }

    public void addRide(Ride ride) {
        validateRide(ride);

        if (ride.getHealthRuleStatus() == null){
            ride.setHealthRuleStatus("UNKNOWN");
        }

        if (!ride.getHealthRuleStatus().matches("^(UNKNOWN|NONE|SPECIFIED)$")){
            throw new ApiException("Invalid health rule status");
        }

        if (ride.getHealthRestrictions() == null){
            ride.setHealthRestrictions(new HashSet<>());
        }

        if ("SPECIFIED".equals(ride.getHealthRuleStatus())
                && ride.getHealthRestrictions().isEmpty()){
            throw new ApiException("Health restrictions are required when health rule status is SPECIFIED");
        }

        if (!"SPECIFIED".equals(ride.getHealthRuleStatus())
                && !ride.getHealthRestrictions().isEmpty()){
            throw new ApiException("Health restrictions must be empty when health rule status is UNKNOWN or NONE");
        }

        for (String condition : ride.getHealthRestrictions()){
            if (condition == null || !HEALTH_CONDITION_CODES.contains(condition)){
                throw new ApiException("Unsupported health restriction code: " + condition);
            }
        }

        if (!"UNKNOWN".equals(ride.getHealthRuleStatus())){
            if (ride.getSourceUrl() == null || ride.getSourceUrl().isBlank()
                    || ride.getRequirementsVerifiedAt() == null){
                throw new ApiException("Source URL and verification time are required for documented health rules");
            }

            if (ride.getRequirementsVerifiedAt().isAfter(LocalDateTime.now())){
                throw new ApiException("Requirements verification time cannot be in the future");
            }
        }

        Ride newRide = new Ride();
        copyRideFields(newRide, ride);
        newRide.setHealthRuleStatus(ride.getHealthRuleStatus());
        newRide.setHealthRestrictions(new HashSet<>(ride.getHealthRestrictions()));

        newRide.setAdditionalSafetyReviewRequired(
                !Boolean.FALSE.equals(ride.getAdditionalSafetyReviewRequired())
        );

        rideRepository.save(newRide);
    }

    @Transactional
    public void updateRide(Integer id, Ride ride) {
        Ride oldRide = rideRepository.findRideById(id);

        if (oldRide == null) {
            throw new ApiException("Ride not found");
        }

        validateRide(ride);
        if (ride.getHealthRuleStatus() == null){
            ride.setHealthRuleStatus("UNKNOWN");
        }

        if (!ride.getHealthRuleStatus().matches("^(UNKNOWN|NONE|SPECIFIED)$")){
            throw new ApiException("Invalid health rule status");
        }

        if (ride.getHealthRestrictions() == null){
            ride.setHealthRestrictions(new HashSet<>());
        }

        if ("SPECIFIED".equals(ride.getHealthRuleStatus())
                && ride.getHealthRestrictions().isEmpty()){
            throw new ApiException("Health restrictions are required when health rule status is SPECIFIED");
        }

        if (!"SPECIFIED".equals(ride.getHealthRuleStatus())
                && !ride.getHealthRestrictions().isEmpty()){
            throw new ApiException("Health restrictions must be empty when health rule status is UNKNOWN or NONE");
        }

        for (String condition : ride.getHealthRestrictions()){
            if (condition == null || !HEALTH_CONDITION_CODES.contains(condition)){
                throw new ApiException("Unsupported health restriction code: " + condition);
            }
        }

        if (!"UNKNOWN".equals(ride.getHealthRuleStatus())){
            if (ride.getSourceUrl() == null || ride.getSourceUrl().isBlank()
                    || ride.getRequirementsVerifiedAt() == null){
                throw new ApiException("Source URL and verification time are required for documented health rules");
            }

            if (ride.getRequirementsVerifiedAt().isAfter(LocalDateTime.now())){
                throw new ApiException("Requirements verification time cannot be in the future");
            }
        }

        copyRideFields(oldRide, ride);
        oldRide.setHealthRuleStatus(ride.getHealthRuleStatus());
        oldRide.getHealthRestrictions().clear();
        oldRide.getHealthRestrictions().addAll(ride.getHealthRestrictions());

        oldRide.setAdditionalSafetyReviewRequired(
                !Boolean.FALSE.equals(ride.getAdditionalSafetyReviewRequired())
        );

        rideRepository.save(oldRide);
    }

    @Transactional
    public void deleteRide(Integer id) {
        Ride ride = rideRepository.findRideById(id);

        if (ride == null) {
            throw new ApiException("Ride not found");
        }

        if (!ride.getPlanItems().isEmpty() || !ride.getVisitRecords().isEmpty() || !ride.getRidePreferences().isEmpty()) {
            throw new ApiException( "Cannot delete a ride linked to plans, records or preferences" );
        }

        rideRepository.delete(ride);
    }

    private void validateRide(Ride ride){
        if (ride == null){
            throw new ApiException("Ride details are required");
        }

        if (ride.getThrillLevel() != null && !ride.getThrillLevel().matches("^(LOW|MILD|EXTREME)$")){
            throw new ApiException("Thrill level must be LOW, MILD or EXTREME");
        }

        String heightRuleStatus = ride.getHeightRuleStatus();

        if (heightRuleStatus == null){
            heightRuleStatus = "UNKNOWN";
            ride.setHeightRuleStatus(heightRuleStatus);
        }

        if (!heightRuleStatus.matches("^(UNKNOWN|NONE|SPECIFIED)$")){
            throw new ApiException("Invalid height rule status");
        }

        if ("NONE".equals(heightRuleStatus) && (ride.getMinHeightCm() != null || ride.getMaxHeightCm() != null)){
            throw new ApiException("Height limits must be empty when height rule status is NONE");
        }

        if ("SPECIFIED".equals(heightRuleStatus) && ride.getMinHeightCm() == null && ride.getMaxHeightCm() == null){
            throw new ApiException("At least one height limit is required when height rule status is SPECIFIED");
        }

        String weightRuleStatus = ride.getWeightRuleStatus();

        if (weightRuleStatus == null){
            weightRuleStatus = "UNKNOWN";
            ride.setWeightRuleStatus(weightRuleStatus);
        }

        if (!weightRuleStatus.matches("^(UNKNOWN|NONE|SPECIFIED)$")){
            throw new ApiException("Invalid weight rule status");
        }

        if ("NONE".equals(weightRuleStatus) && (ride.getMinWeightKg() != null || ride.getMaxWeightKg() != null)){
            throw new ApiException("Weight limits must be empty when weight rule status is NONE");
        }

        if ("SPECIFIED".equals(weightRuleStatus) && ride.getMinWeightKg() == null && ride.getMaxWeightKg() == null){
            throw new ApiException("At least one weight limit is required when weight rule status is SPECIFIED");
        }

        String ageRuleStatus = ride.getAgeRuleStatus();

        if (ageRuleStatus == null){
            ageRuleStatus = "UNKNOWN";
            ride.setAgeRuleStatus(ageRuleStatus);
        }

        if (!ageRuleStatus.matches("^(UNKNOWN|NONE|SPECIFIED)$")){
            throw new ApiException("Invalid age rule status");
        }

        if ("NONE".equals(ageRuleStatus) && (ride.getMinAgeYears() != null || ride.getMaxAgeYears() != null)){
            throw new ApiException("Age limits must be empty when age rule status is NONE");
        }

        if ("SPECIFIED".equals(ageRuleStatus) && ride.getMinAgeYears() == null && ride.getMaxAgeYears() == null){
            throw new ApiException("At least one age limit is required when age rule status is SPECIFIED");
        }

        if (!"UNKNOWN".equals(heightRuleStatus)
                || !"UNKNOWN".equals(weightRuleStatus)
                || !"UNKNOWN".equals(ageRuleStatus)
                || ride.getAdultCompanionRequired() != null
                || Boolean.FALSE.equals(ride.getAdditionalSafetyReviewRequired())){

            if (ride.getSourceUrl() == null || ride.getSourceUrl().isBlank()
                    || ride.getRequirementsVerifiedAt() == null){
                throw new ApiException("Source URL and verification time are required for documented rules");
            }
        }

        if (ride.getRequirementsVerifiedAt() != null
                && ride.getRequirementsVerifiedAt().isAfter(LocalDateTime.now())){
            throw new ApiException("Requirements verification time cannot be in the future");
        }

        if (ride.getCompanionRequiredBelowHeightCm() != null && !Boolean.TRUE.equals(ride.getAdultCompanionRequired())){
            throw new ApiException("Adult companion must be required when a companion height threshold is provided");
        }

        if (ride.getCompanionRequiredBelowHeightCm() != null && ride.getMinHeightCm() != null && ride.getCompanionRequiredBelowHeightCm().compareTo(ride.getMinHeightCm()) < 0){
            throw new ApiException("Companion height threshold cannot be below minimum ride height");
        }

        if (ride.getMinHeightCm() != null && ride.getMaxHeightCm() != null && ride.getMinHeightCm().compareTo(ride.getMaxHeightCm()) > 0){
            throw new ApiException("Minimum height cannot exceed maximum height");
        }

        if (ride.getMinAgeYears() != null && ride.getMaxAgeYears() != null && ride.getMinAgeYears() > ride.getMaxAgeYears()){
            throw new ApiException("Minimum age cannot exceed maximum age");
        }

        if (ride.getMinWeightKg() != null && ride.getMaxWeightKg() != null && ride.getMinWeightKg().compareTo(ride.getMaxWeightKg()) > 0){
            throw new ApiException("Minimum weight cannot exceed maximum weight");
        }

        if (ride.getWaitMinutes() != null && (ride.getWaitSource() == null || ride.getWaitSource().isBlank())){
            throw new ApiException("Wait source is required when waiting time is provided");
        }

        if (ride.getWaitMinutes() != null && ride.getWaitObservedAt() == null){
            throw new ApiException("Wait observation time is required when waiting time is provided");
        }

        if (ride.getWaitMinutes() == null && ride.getWaitObservedAt() != null){
            throw new ApiException("Waiting time is required when wait observation time is provided");
        }

        if ((ride.getMapX() == null) != (ride.getMapY() == null)){
            throw new ApiException("Both map coordinates must be provided together");
        }
    }

    private void copyRideFields(Ride target, Ride source) {
        target.setSourceRideId(source.getSourceRideId());
        target.setRideDurationMinutes(source.getRideDurationMinutes());
        target.setEntryExitBufferMinutes(source.getEntryExitBufferMinutes());
        target.setTrackingArea(source.getTrackingArea());
        target.setName(source.getName());
        target.setLand(source.getLand());
        target.setRideFeatures(source.getRideFeatures());
        target.setAdultCompanionRequired(source.getAdultCompanionRequired());
        target.setCompanionRequiredBelowHeightCm(source.getCompanionRequiredBelowHeightCm());
        target.setRecommendedMinAgeYears(source.getRecommendedMinAgeYears());
        target.setRecommendedUnaccompaniedAgeYears(source.getRecommendedUnaccompaniedAgeYears());
        target.setRequirementsVerifiedAt(source.getRequirementsVerifiedAt());
        target.setCategory(source.getCategory());
        target.setThrillLevel(source.getThrillLevel());
        target.setStatus(source.getStatus());
        target.setMinHeightCm(source.getMinHeightCm());
        target.setMaxHeightCm(source.getMaxHeightCm());
        target.setMinAgeYears(source.getMinAgeYears());
        target.setMaxAgeYears(source.getMaxAgeYears());
        target.setMinWeightKg(source.getMinWeightKg());
        target.setMaxWeightKg(source.getMaxWeightKg());
        target.setWarnings(source.getWarnings());
        target.setWaitMinutes(source.getWaitMinutes());
        target.setWaitObservedAt(source.getWaitObservedAt());
        target.setSourceUrl(source.getSourceUrl());
        target.setWaitSource(source.getWaitSource());
        target.setMapX(source.getMapX());
        target.setMapY(source.getMapY());
        target.setHeightRuleStatus(source.getHeightRuleStatus());
        target.setWeightRuleStatus(source.getWeightRuleStatus());
        target.setAgeRuleStatus(source.getAgeRuleStatus());
    }

    public List<Ride> getOpenRides(){
        return rideRepository.findRidesByStatus("OPEN").stream().filter(ride -> "OPEN".equals(PlanningAvailability.rideStatus(ride, LocalDate.now()))).toList();
    }

    public boolean validateRideIds(List<Integer> rideIds){
        if (rideIds == null){
            return false;
        }

        for (Integer rideId : rideIds){
            if (rideId == null || rideId <= 0){
                return false;
            }
        }

        List<Integer> uniqueRideIds = new ArrayList<>();

        for (Integer rideId : rideIds){
            if (!uniqueRideIds.contains(rideId)){
                uniqueRideIds.add(rideId);
            }
        }

        return rideRepository.findAllById(uniqueRideIds).size() == uniqueRideIds.size();
    }

    // Legacy physical-only check using today's date.
    // Use checkEligibilityForMember for visit-date, health and companion checks.
    // This method never confirms full eligibility.
    @Deprecated
    public String checkEligibility(Integer rideId, Integer profileId){
        Ride ride = rideRepository.findRideById(rideId);

        if (ride == null){
            throw new ApiException("Ride not found");
        }

        VisitorProfile profile = visitorProfileRepository.findVisitorProfileById(profileId);

        if (profile == null){
            throw new ApiException("Visitor profile not found");
        }

        if (profile.getHeightCm() != null && profile.getHeightCm().signum() > 0){
            if (ride.getMinHeightCm() != null && profile.getHeightCm().compareTo(ride.getMinHeightCm()) < 0){
                return "NOT_ELIGIBLE";
            }

            if (ride.getMaxHeightCm() != null && profile.getHeightCm().compareTo(ride.getMaxHeightCm()) > 0){
                return "NOT_ELIGIBLE";
            }
        }

        if (profile.getWeightKg() != null && profile.getWeightKg().signum() > 0){
            if (ride.getMinWeightKg() != null && profile.getWeightKg().compareTo(ride.getMinWeightKg()) < 0){
                return "NOT_ELIGIBLE";
            }

            if (ride.getMaxWeightKg() != null && profile.getWeightKg().compareTo(ride.getMaxWeightKg()) > 0){
                return "NOT_ELIGIBLE";
            }
        }

        LocalDate today = LocalDate.now();

        if (profile.getBirthDate() != null && !profile.getBirthDate().isAfter(today)){
            int age = Period.between(profile.getBirthDate(), today).getYears();

            if (ride.getMinAgeYears() != null && age < ride.getMinAgeYears()){
                return "NOT_ELIGIBLE";
            }

            if (ride.getMaxAgeYears() != null && age > ride.getMaxAgeYears()){
                return "NOT_ELIGIBLE";
            }
        }

        return "UNKNOWN";
    }

    public List<RideWaitTimeDtoOut> getRideWaitTimes(){
        List<Ride> rides = rideRepository.findAll();
        List<RideWaitTimeDtoOut> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Ride ride : rides){
            String freshness = "UNKNOWN";

            if (ride.getWaitMinutes() != null && ride.getWaitMinutes() >= 0 && ride.getWaitObservedAt() != null && !ride.getWaitObservedAt().isAfter(now) && ride.getWaitSource() != null && !ride.getWaitSource().isBlank()){

                if (ride.getWaitObservedAt().isBefore(now.minusMinutes("QUEUE_ANALYTICS_LIVE".equals(ride.getWaitSource()) ? Math.min(10, waitFreshnessMinutes) : waitFreshnessMinutes))){
                    freshness = "STALE";
                } else {
                    freshness = "RECENT";
                }
            }

            result.add(new RideWaitTimeDtoOut( ride.getId(), ride.getName(), ride.getStatus(), ride.getWaitMinutes(), ride.getWaitObservedAt(), ride.getWaitSource(), freshness ));
        }

        return result;
    }

    public List<Ride> filterRides(String category, String thrill, String status){
        if (category != null && !category.matches("^(THRILL|FAMILY|KIDS|WATER|ARCADE)$")){
            throw new ApiException("Category must be THRILL, FAMILY, KIDS, WATER or ARCADE");
        }

        if (thrill != null && !thrill.matches("^(LOW|MILD|EXTREME)$")){
            throw new ApiException("Thrill level must be LOW, MILD or EXTREME");
        }

        if (status != null && !status.matches("^(OPEN|CLOSED|UNAVAILABLE)$")){
            throw new ApiException("Status must be OPEN, CLOSED or UNAVAILABLE");
        }

        List<Ride> rides = rideRepository.filterRides(category, thrill, status);

        if (rides.isEmpty()){
            throw new ApiException("No rides found matching these filters");
        }

        return rides;
    }

    @Transactional
    public void updateRideWait(Integer accountId, Integer rideId, UpdateRideWaitDtoIn dto){
        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())){
            throw new ApiException("Account is not active");
        }

        if (!"ADMIN".equals(account.getRole())){
            throw new ApiException("Only admins can update ride waiting times");
        }

        Ride ride = rideRepository.findRideById(rideId);

        if (ride == null){
            throw new ApiException("Ride not found");
        }

        if (ride.getWaitObservedAt() != null && dto.getWaitObservedAt().isBefore(ride.getWaitObservedAt())){
            throw new ApiException("Cannot replace a newer waiting time observation with an older one");
        }

        ride.setWaitMinutes(dto.getWaitMinutes());
        ride.setWaitSource(dto.getWaitSource());
        ride.setWaitObservedAt(dto.getWaitObservedAt());

        rideRepository.save(ride);

        // TODO: بعد تنفيذ onRideChanged وتحديد مقدار تغير الانتظار المؤثر،
        // نربط التحديث بفحص إعادة التخطيط عند عبدالعزيز.
    }

    @Transactional
    public void updateRideStatus(Integer accountId, Integer rideId, UpdateRideStatusDtoIn dto){
        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())){
            throw new ApiException("Account is not active");
        }

        if (!"ADMIN".equals(account.getRole())){
            throw new ApiException("Only admins can update ride status");
        }

        Ride ride = rideRepository.findRideById(rideId);

        if (ride == null){
            throw new ApiException("Ride not found");
        }

        if (dto.getStatus().equals(ride.getStatus())){
            throw new ApiException("Ride already has this status");
        }

        ride.setStatus(dto.getStatus());
        ride.setAvailabilitySource("ADMIN");

        rideRepository.save(ride);

        // TODO: ربط التغيير المؤثر بـ VisitPlanService.onRideChanged(rideId)
        // بعد تنفيذها، لفحص الخطط المتأثرة واقتراح بديل دون تغيير الخطة المعتمدة مباشرة.
    }

    @Transactional(readOnly = true)
    public Map<String, Object> checkEligibilityForMember(
            Integer accountId, Integer memberId, Integer rideId){

        VisitMember member = visitMemberRepository.findVisitMemberById(memberId);

        if (member == null){
            throw new ApiException("Visit member not found");
        }

        boolean isOrganizer = member.getVisit().getOrganizer()
                .getId().equals(accountId);

        boolean isMember = member.getVisitorProfile() != null
                && member.getVisitorProfile().getAccount()
                .getId().equals(accountId);

        if (!isOrganizer && !isMember){
            throw new ApiException("You cannot access this visit member");
        }

        if (member.getVisit().getVisitDate() == null){
            throw new ApiException("Visit date is required to check eligibility");
        }

        if (member.getBirthDate() != null
                && member.getBirthDate().isAfter(member.getVisit().getVisitDate())){
            throw new ApiException("Member birth date cannot be after the visit date");
        }

        if (member.getHeightCm() != null && member.getHeightCm().signum() <= 0){
            throw new ApiException("Member height must be positive");
        }

        if (member.getWeightKg() != null && member.getWeightKg().signum() <= 0){
            throw new ApiException("Member weight must be positive");
        }

        Ride ride = getRideById(rideId);

        List<String> reasons = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        // Check height.
        if (member.getHeightCm() == null){
            if (!"NONE".equals(ride.getHeightRuleStatus())){
                missing.add("heightCm");
            }
        } else {
            if ((ride.getMinHeightCm() != null
                    && member.getHeightCm().compareTo(ride.getMinHeightCm()) < 0)
                    || (ride.getMaxHeightCm() != null
                    && member.getHeightCm().compareTo(ride.getMaxHeightCm()) > 0)){

                reasons.add("Height is outside the known ride limits");
            }
        }

        if (!"NONE".equals(ride.getHeightRuleStatus())
                && !"SPECIFIED".equals(ride.getHeightRuleStatus())){
            missing.add("ride.heightRuleStatus");
        }

        // Check weight.
        if (member.getWeightKg() == null){
            if (!"NONE".equals(ride.getWeightRuleStatus())){
                missing.add("weightKg");
            }
        } else {
            if ((ride.getMinWeightKg() != null
                    && member.getWeightKg().compareTo(ride.getMinWeightKg()) < 0)
                    || (ride.getMaxWeightKg() != null
                    && member.getWeightKg().compareTo(ride.getMaxWeightKg()) > 0)){

                reasons.add("Weight is outside the known ride limits");
            }
        }

        if (!"NONE".equals(ride.getWeightRuleStatus())
                && !"SPECIFIED".equals(ride.getWeightRuleStatus())){
            missing.add("ride.weightRuleStatus");
        }

        // Check age on the visit date.
        if (member.getBirthDate() == null){
            if (!"NONE".equals(ride.getAgeRuleStatus())){
                missing.add("birthDate");
            }
        } else {
            int age = Period.between(
                    member.getBirthDate(),
                    member.getVisit().getVisitDate()
            ).getYears();

            if (age < 0
                    || (ride.getMinAgeYears() != null
                    && age < ride.getMinAgeYears())
                    || (ride.getMaxAgeYears() != null
                    && age > ride.getMaxAgeYears())){

                reasons.add("Age is outside the known ride limits on the visit date");
            }
        }

        if (!"NONE".equals(ride.getAgeRuleStatus())
                && !"SPECIFIED".equals(ride.getAgeRuleStatus())){
            missing.add("ride.ageRuleStatus");
        }

        // A known physical restriction is enough to reject the ride.
        if (!reasons.isEmpty()){
            return Map.of(
                    "status", "NOT_ELIGIBLE",
                    "reasons", reasons,
                    "missingFields", missing
            );
        }

        String healthRuleStatus = ride.getHealthRuleStatus();

        boolean documentedHealthRules =
                ("NONE".equals(healthRuleStatus)
                        || "SPECIFIED".equals(healthRuleStatus))
                        && ride.getSourceUrl() != null
                        && !ride.getSourceUrl().isBlank()
                        && ride.getRequirementsVerifiedAt() != null
                        && !ride.getRequirementsVerifiedAt().isAfter(LocalDateTime.now());

        if (!documentedHealthRules){
            missing.add("ride.healthRules");
        } else if ("NONE".equals(healthRuleStatus)){
            // NONE must not contain restrictions.
            if (ride.getHealthRestrictions() != null
                    && !ride.getHealthRestrictions().isEmpty()){
                missing.add("ride.healthRules");
            }
        } else {
            boolean validRestrictions = ride.getHealthRestrictions() != null
                    && !ride.getHealthRestrictions().isEmpty();

            if (validRestrictions){
                for (String restriction : ride.getHealthRestrictions()){
                    if (restriction == null
                            || !HEALTH_CONDITION_CODES.contains(restriction)){
                        validRestrictions = false;
                        break;
                    }
                }
            }

            if (!validRestrictions){
                missing.add("ride.healthRestrictions");
            } else {
                VisitorProfile profile = member.getVisitorProfile();

                // Do not use the organizer's health data for a family member.
                if (profile == null){
                    missing.add("healthData");
                } else if (!privacyConsentService.hasConsent(
                        profile.getId(), "HEALTH_STORAGE")){

                    missing.add("healthConsent");
                } else {
                    String healthData = profile.getHealthData();
                    JsonNode healthNode = null;

                    if (healthData != null && !healthData.isBlank()){
                        try {
                            healthNode = jsonMapper.readTree(healthData);
                        } catch (RuntimeException exception){
                            // Invalid stored JSON remains unknown.
                            healthNode = null;
                        }
                    }

                    boolean validHealthData = healthNode != null
                            && healthNode.isObject()
                            && (healthNode.size() == 1 || healthNode.size() == 2 && healthNode.has("note") && healthNode.path("note").isTextual())
                            && healthNode.has("conditions")
                            && healthNode.get("conditions").isArray();

                    Set<String> visitorConditions = new HashSet<>();

                    if (validHealthData){
                        for (JsonNode condition : healthNode.get("conditions")){
                            if (!condition.isTextual()){
                                validHealthData = false;
                                break;
                            }

                            String code = condition.asText();

                            if (!HEALTH_CONDITION_CODES.contains(code)){
                                validHealthData = false;
                                break;
                            }

                            visitorConditions.add(code);
                        }
                    }

                    if (!validHealthData){
                        missing.add("healthData");
                    } else {
                        if (!healthNode.path("note").asString("").isBlank()) missing.add("healthNotesRequireReview");
                        for (String condition : visitorConditions){
                            if (ride.getHealthRestrictions().contains(condition)){
                                // Avoid exposing the visitor's medical condition.
                                reasons.add(
                                        "The visitor does not meet the documented health requirements"
                                );
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (!reasons.isEmpty()){
            return Map.of(
                    "status", "NOT_ELIGIBLE",
                    "reasons", reasons,
                    "missingFields", missing
            );
        }

        // Confirm that the physical rules are complete and consistent.
        if ("SPECIFIED".equals(ride.getHeightRuleStatus())
                && ride.getMinHeightCm() == null
                && ride.getMaxHeightCm() == null){
            missing.add("ride.heightLimits");
        }

        if ("NONE".equals(ride.getHeightRuleStatus())
                && (ride.getMinHeightCm() != null
                || ride.getMaxHeightCm() != null)){
            missing.add("ride.heightRules");
        }

        if ("SPECIFIED".equals(ride.getWeightRuleStatus())
                && ride.getMinWeightKg() == null
                && ride.getMaxWeightKg() == null){
            missing.add("ride.weightLimits");
        }

        if ("NONE".equals(ride.getWeightRuleStatus())
                && (ride.getMinWeightKg() != null
                || ride.getMaxWeightKg() != null)){
            missing.add("ride.weightRules");
        }

        if ("SPECIFIED".equals(ride.getAgeRuleStatus())
                && ride.getMinAgeYears() == null
                && ride.getMaxAgeYears() == null){
            missing.add("ride.ageLimits");
        }

        if ("NONE".equals(ride.getAgeRuleStatus())
                && (ride.getMinAgeYears() != null
                || ride.getMaxAgeYears() != null)){
            missing.add("ride.ageRules");
        }

        boolean documentedRequirements = ride.getSourceUrl() != null
                && !ride.getSourceUrl().isBlank()
                && ride.getRequirementsVerifiedAt() != null
                && !ride.getRequirementsVerifiedAt().isAfter(LocalDateTime.now());

        if (!documentedRequirements){
            missing.add("ride.requirementsVerification");
        }

        if (!Boolean.FALSE.equals(ride.getAdditionalSafetyReviewRequired())) {
            missing.add("ride.additionalSafetyReview");
            reasons.add("Additional ride safety requirements need review before eligibility can be confirmed");
        }

        // Determine whether this visitor requires an adult companion.
        boolean companionRequired = false;

        if (!documentedRequirements
                || ride.getAdultCompanionRequired() == null){

            missing.add("ride.companionRules");
        } else if (Boolean.FALSE.equals(ride.getAdultCompanionRequired())){
            if (ride.getCompanionRequiredBelowHeightCm() != null){
                missing.add("ride.companionRules");
            }
        } else {
            if (ride.getCompanionRequiredBelowHeightCm() == null){
                companionRequired = true;
            } else if (ride.getCompanionRequiredBelowHeightCm().signum() <= 0
                    || (ride.getMinHeightCm() != null
                    && ride.getCompanionRequiredBelowHeightCm()
                    .compareTo(ride.getMinHeightCm()) < 0)){

                missing.add("ride.companionRules");
            } else if (member.getHeightCm() == null){
                if (!missing.contains("heightCm")){
                    missing.add("heightCm");
                }
            } else {
                companionRequired = member.getHeightCm()
                        .compareTo(ride.getCompanionRequiredBelowHeightCm()) < 0;
            }
        }

        if (companionRequired){
            missing.add("companionAssignment");
            reasons.add(
                    "An eligible adult companion must be confirmed for the same ride activity"
            );
        }

        if (!missing.isEmpty()){
            reasons.add("Eligibility is unconfirmed because required information is missing or unverified");

            return Map.of(
                    "status", "UNKNOWN",
                    "reasons", reasons,
                    "missingFields", missing
            );
        }

        reasons.add("The visitor meets the documented eligibility rules checked by the system");

        return Map.of(
                "status", "ELIGIBLE",
                "reasons", reasons,
                "missingFields", missing
        );
    }

    public java.util.Map<String, Object> getRideSnapshotForVisit(Integer rideId, LocalDate visitDate, java.time.LocalTime arrivalTime, int durationMinutes) {
        Ride ride = getRideById(rideId);
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>(getRideSnapshot(rideId));
        String operationalStatus = PlanningAvailability.rideStatus(ride, visitDate);
        snapshot.put("status", PlanningAvailability.canPlanRide(ride, visitDate) ? "OPEN" : "CLOSED");
        snapshot.put("operationalStatus", operationalStatus);
        snapshot.put("availabilityIsEstimate", "UNKNOWN".equals(operationalStatus));
        boolean future = visitDate != null && !visitDate.equals(LocalDate.now());
        if (future || "SIKKA_DEFAULT_ESTIMATE".equals(snapshot.get("waitSource"))) {
            Integer estimate = historicalEstimate(ride, visitDate == null ? LocalDate.now() : visitDate, arrivalTime, durationMinutes);
            snapshot.put("planningWaitMinutes", estimate == null ? Math.max(0, defaultPlanningWaitMinutes) : estimate);
            snapshot.put("waitSource", estimate == null ? "SIKKA_DEFAULT_ESTIMATE" : "QUEUE_ANALYTICS_HISTORICAL_ESTIMATE");
            snapshot.put("waitIsEstimate", true);
            snapshot.put("waitObservedAt", java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Riyadh")).toOffsetDateTime().toString());
        }
        return snapshot;
    }

    private Integer historicalEstimate(Ride ride, LocalDate date, java.time.LocalTime arrivalTime, int durationMinutes) {
        if (ride.getAnalyticsPatternsJson() == null) return null;
        try {
            JsonNode patterns = jsonMapper.readTree(ride.getAnalyticsPatternsJson());
            JsonNode matching = patterns.path("weekdays").path(date.getDayOfWeek().name());
            JsonNode all = patterns.path("allDays");
            int start = arrivalTime == null ? 16 : arrivalTime.getHour();
            int end = Math.min(24, start + Math.max(1, (int) Math.ceil(durationMinutes / 60.0)));
            double total = 0;
            int count = 0;
            for (int hour = start; hour < end; hour++) {
                JsonNode sameDay = matching.isArray() && matching.size() > hour ? matching.get(hour) : null;
                JsonNode selected = sameDay != null && sameDay.path("daysCount").asInt() >= 2 ? sameDay : all.isArray() && all.size() > hour ? all.get(hour) : null;
                if (selected != null && selected.path("daysCount").asInt() >= 1 && selected.path("sampleCount").asInt() >= 1 && selected.path("averageWait").isNumber() && selected.path("averageWait").asDouble() >= 0 && selected.path("averageWait").asDouble() <= 1440) {
                    total += selected.path("averageWait").asDouble();
                    count++;
                }
            }
            return count == 0 ? null : (int) Math.ceil(total / count);
        } catch (Exception exception) {
            return null;
        }
    }

    public java.util.Map<String, Object> getRideSnapshot(Integer rideId) {
        Ride ride = getRideById(rideId);
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("id", ride.getId());
        String operationalStatus = PlanningAvailability.rideStatus(ride, LocalDate.now());
        snapshot.put("status", PlanningAvailability.canPlanRide(ride, LocalDate.now()) ? "OPEN" : "CLOSED");
        snapshot.put("operationalStatus", operationalStatus);
        snapshot.put("availabilityIsEstimate", "UNKNOWN".equals(operationalStatus));
        snapshot.put("availabilitySource", ride.getAvailabilitySource() == null ? "UNKNOWN" : ride.getAvailabilitySource());
        snapshot.put("nodeKey", "RIDE:" + ride.getId());
        snapshot.put("rideDurationMinutes", ride.getRideDurationMinutes() == null ? 5 : ride.getRideDurationMinutes());
        snapshot.put("timingSource", ride.getRideDurationMinutes() == null || ride.getEntryExitBufferMinutes() == null ? "SIKKA_DEFAULT_ESTIMATE" : (ride.getTimingSource() == null ? "UNKNOWN" : ride.getTimingSource()));
        snapshot.put("entryExitBufferMinutes", ride.getEntryExitBufferMinutes() == null ? 3 : ride.getEntryExitBufferMinutes());
        snapshot.put("features", ride.getRideFeatures());
        boolean recent = ride.getWaitMinutes() != null && ride.getWaitObservedAt() != null && !ride.getWaitObservedAt().isAfter(LocalDateTime.now()) && !ride.getWaitObservedAt().isBefore(LocalDateTime.now().minusMinutes(waitFreshnessMinutes));
        recent = recent && (!"QUEUE_ANALYTICS_LIVE".equals(ride.getWaitSource()) || !ride.getWaitObservedAt().isBefore(LocalDateTime.now().minusMinutes(10)));
        snapshot.put("planningWaitMinutes", recent ? ride.getWaitMinutes() : Math.max(0, defaultPlanningWaitMinutes));
        snapshot.put("waitIsEstimate", !recent || !"QUEUE_ANALYTICS_LIVE".equals(ride.getWaitSource()));
        snapshot.put("waitSource", recent ? (ride.getWaitSource() == null ? "UNKNOWN" : ride.getWaitSource()) : "SIKKA_DEFAULT_ESTIMATE");
        // For a default estimate this timestamp identifies estimate creation, not a source observation.
        snapshot.put("waitObservedAt", (recent ? ride.getWaitObservedAt().atZone(java.time.ZoneId.of("Asia/Riyadh")) : java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Riyadh"))).toOffsetDateTime().toString());
        return snapshot;
    }

}
