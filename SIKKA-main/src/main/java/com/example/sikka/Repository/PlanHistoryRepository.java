package com.example.sikka.Repository;

import com.example.sikka.Model.PlanHistory;
import com.example.sikka.Model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanHistoryRepository extends JpaRepository<PlanHistory, Integer> {

    @Query("select h.id from PlanHistory h where h.account.id = :accountId order by h.startedAt desc, h.id desc")
    List<Integer> findOrderedHistoryIdsByAccountId(@Param("accountId") Integer accountId);

    default List<PlanHistory> findAllByAccount_IdOrderByStartedAtDescIdDesc(Integer accountId) {
        List<Integer> ids = findOrderedHistoryIdsByAccountId(accountId);
        if (ids.isEmpty()) {
            return List.of();
        }
        java.util.Map<Integer, PlanHistory> rows = new java.util.HashMap<>();
        for (PlanHistory history : findAllById(ids)) {
            rows.put(history.getId(), history);
        }
        List<PlanHistory> ordered = new java.util.ArrayList<>();
        for (Integer id : ids) {
            PlanHistory history = rows.get(id);
            if (history != null) {
                ordered.add(history);
            }
        }
        return ordered;
    }

    PlanHistory findPlanHistoryByIdAndAccount_Id(Integer planHistoryId, Integer accountId);

    @Query("select p.generatedByHistory.id as historyId, p.id as planId from VisitPlan p where p.generatedByHistory.id in :historyIds order by p.id asc")
    List<GeneratedPlanReference> findGeneratedPlanReferences(@Param("historyIds") List<Integer> historyIds);

    PlanHistory findPlanHistoryByAccount_IdAndRequestKey(Integer accountId, String requestKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from PlanHistory h where h.id = :planHistoryId")
    PlanHistory findPlanHistoryByIdForUpdate(@Param("planHistoryId") Integer planHistoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :accountId")
    Account findAccountByIdForGenerationUpdate(@Param("accountId") Integer accountId);
    interface GeneratedPlanReference {

        Integer getHistoryId();

        Integer getPlanId();
    }
}