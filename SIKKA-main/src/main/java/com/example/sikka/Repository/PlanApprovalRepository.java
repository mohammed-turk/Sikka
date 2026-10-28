package com.example.sikka.Repository;

import com.example.sikka.Model.PlanApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanApprovalRepository extends JpaRepository<PlanApproval, Integer> {

    PlanApproval findPlanApprovalById(Integer id);

    PlanApproval findPlanApprovalByVisitPlan_Id(Integer visitPlanId);
}