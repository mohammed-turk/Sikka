package com.example.sikka.Repository;

import com.example.sikka.Model.VisitPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface VisitPlanRepository extends JpaRepository<VisitPlan, Integer> {

    VisitPlan findVisitPlanById(Integer id);

    List<VisitPlan> findAllByVisit_Id(Integer visitId);

    boolean existsByVisit_IdAndPlanKind(Integer visitId, String planKind);

    boolean existsByVisit_IdAndOwnerVisitMember_IdAndPlanKind(Integer visitId, Integer visitMemberId, String planKind);

    @Query(value = "select * from visit_plan where id = :visitPlanId for update", nativeQuery = true)
    VisitPlan findVisitPlanByIdForUpdate(@Param("visitPlanId") Integer visitPlanId);

    VisitPlan findVisitPlanByVisit_IdAndOwnerVisitMember_IdAndPlanKind(Integer visitId, Integer visitMemberId, String planKind);

    List<VisitPlan> findAllByVisit_IdAndPlanKind(Integer visitId, String planKind);
    boolean existsByOwnerVisitMember_Id(Integer visitMemberId);
}
