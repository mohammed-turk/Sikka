package com.example.sikka.Repository;

import com.example.sikka.Model.VisitorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VisitorProfileRepository extends JpaRepository<VisitorProfile, Integer> {

    VisitorProfile findVisitorProfileById(Integer id);

    VisitorProfile findVisitorProfileByAccountId(Integer accountId);
    VisitorProfile findVisitorProfileByAccount_Id(Integer accountId);
}