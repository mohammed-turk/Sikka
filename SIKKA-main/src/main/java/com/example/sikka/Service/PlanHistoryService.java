package com.example.sikka.Service;

import com.example.sikka.Api.ApiException;
import com.example.sikka.DtoOut.PlanHistoryDtoOut;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.PlanHistory;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.PlanHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlanHistoryService {

    private final PlanHistoryRepository planHistoryRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<PlanHistoryDtoOut> getMyPlanHistory(Integer accountId) {

        validateActiveAccount(accountId);

        List<PlanHistory> histories = planHistoryRepository.findAllByAccount_IdOrderByStartedAtDescIdDesc(accountId);

        List<PlanHistoryDtoOut> results = new ArrayList<>();

        if (histories.isEmpty()) {
            return results;
        }

        List<Integer> historyIds = new ArrayList<>();

        for (PlanHistory history : histories) {
            historyIds.add(history.getId());
        }

        Map<Integer, List<Integer>> planIdsByHistory = getGeneratedPlanIdsByHistory(historyIds);

        for (PlanHistory history : histories) {
            List<Integer> visitPlanIds = planIdsByHistory.getOrDefault(history.getId(), new ArrayList<>());

            results.add(convertPlanHistoryToDtoOut(history, visitPlanIds));
        }

        return results;
    }

    @Transactional(readOnly = true)
    public PlanHistoryDtoOut getPlanHistoryDetails(Integer accountId, Integer planHistoryId) {

        validateActiveAccount(accountId);

        PlanHistory history = planHistoryRepository.findPlanHistoryByIdAndAccount_Id(planHistoryId, accountId);

        if (history == null) {
            throw new ApiException("Plan history not found for this account");
        }

        Map<Integer, List<Integer>> planIdsByHistory = getGeneratedPlanIdsByHistory(List.of(history.getId()));

        List<Integer> visitPlanIds = planIdsByHistory.getOrDefault(history.getId(), new ArrayList<>());

        return convertPlanHistoryToDtoOut(history, visitPlanIds);
    }

    private void validateActiveAccount(Integer accountId) {

        Account account = accountRepository.findAccountById(accountId);

        if (account == null) {
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("Account is not active");
        }
    }

    private Map<Integer, List<Integer>> getGeneratedPlanIdsByHistory(List<Integer> historyIds) {

        Map<Integer, List<Integer>> planIdsByHistory = new HashMap<>();

        if (historyIds.isEmpty()) {
            return planIdsByHistory;
        }

        List<PlanHistoryRepository.GeneratedPlanReference> references = planHistoryRepository.findGeneratedPlanReferences(historyIds);

        for (PlanHistoryRepository.GeneratedPlanReference reference : references) {
            planIdsByHistory.computeIfAbsent(reference.getHistoryId(), key -> new ArrayList<>()).add(reference.getPlanId());
        }

        return planIdsByHistory;
    }

    private PlanHistoryDtoOut convertPlanHistoryToDtoOut(PlanHistory history, List<Integer> visitPlanIds) {

        Integer paymentOrderId = history.getPaymentOrder() == null ? null : history.getPaymentOrder().getId();

        Integer sourceVisitId = history.getSourceVisit() == null ? null : history.getSourceVisit().getId();

        return new PlanHistoryDtoOut(history.getId(), history.getVisit().getId(), history.getRequestKey(), history.getOperationType(), history.getStatus(), history.getBillingMode(), history.getCountsTowardQuota(), paymentOrderId, sourceVisitId, visitPlanIds, history.getStartedAt(), history.getCompletedAt(), history.getFailureReason());
    }
}