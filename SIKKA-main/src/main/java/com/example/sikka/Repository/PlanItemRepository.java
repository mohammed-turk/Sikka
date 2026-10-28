package com.example.sikka.Repository;

import com.example.sikka.Model.PlanItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanItemRepository extends JpaRepository<PlanItem, Integer> {

    PlanItem findPlanItemById(Integer id);

    List<PlanItem> findAllByVisitPlan_Id(Integer visitPlanId);


    @org.springframework.data.jpa.repository.Query("select i from PlanItem i join i.visitPlan p join p.visit v where i.id > :afterId and i.activityKind = 'RIDE' and i.ride is not null and p.planKind = 'GROUP' and p.isActive = true and p.timingMode = 'SCHEDULED' and v.status in ('READY', 'IN_PROGRESS') order by i.id asc")
    List<PlanItem> findRideChangeCandidates(@org.springframework.data.repository.query.Param("afterId") Integer afterId, org.springframework.data.domain.Pageable pageable);

}
