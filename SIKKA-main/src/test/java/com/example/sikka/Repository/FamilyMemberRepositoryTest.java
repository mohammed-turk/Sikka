package com.example.sikka.Repository;

import com.example.sikka.Model.Account;
import com.example.sikka.Model.FamilyMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.test.database.replace=none")
class FamilyMemberRepositoryTest {

    @Autowired
    FamilyMemberRepository familyMemberRepository;

    @Autowired
    AccountRepository accountRepository;

    Account owner;

    @BeforeEach
    void setUp() {
        owner = new Account();
        owner.setName("Parent");
        owner.setEmail("parent@sikka.test");
        owner.setPasswordHash("hash");
        owner.setRole("USER");
        owner.setStatus("ACTIVE");
        accountRepository.save(owner);

        FamilyMember child = new FamilyMember();
        child.setName("Little Layan");
        child.setBirthDate(LocalDate.of(2018, 3, 1));
        child.setGender("FEMALE");
        child.setOwnerAccount(owner);
        familyMemberRepository.save(child);
    }

    @Test
    void findFamilyMembersByOwnerAccountIdTest() {
        List<FamilyMember> members = familyMemberRepository.findFamilyMembersByOwnerAccountId(owner.getId());

        assertThat(members).hasSize(1);
        assertThat(members.get(0).getName()).isEqualTo("Little Layan");
    }

    @Test
    void findFamilyMembersOfAnotherAccountTest() {
        List<FamilyMember> members = familyMemberRepository.findFamilyMembersByOwnerAccountId(9999);

        assertThat(members).isEmpty();
    }
}