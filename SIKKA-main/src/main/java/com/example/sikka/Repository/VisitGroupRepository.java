package com.example.sikka.Repository;

import com.example.sikka.Model.VisitGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VisitGroupRepository extends JpaRepository<VisitGroup, Integer> {
    VisitGroup findVisitGroupById(Integer id);
    VisitGroup findVisitGroupByInviteCodeHash(String inviteCodeHash);
}