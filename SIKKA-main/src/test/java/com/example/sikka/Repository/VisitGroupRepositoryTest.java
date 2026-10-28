package com.example.sikka.Repository;

import com.example.sikka.Model.Account;
import com.example.sikka.Model.VisitGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.test.database.replace=none")
class VisitGroupRepositoryTest {

    @Autowired
    VisitGroupRepository visitGroupRepository;

    @Autowired
    AccountRepository accountRepository;

    VisitGroup group;

    @BeforeEach
    void setUp() {
        Account organizer = new Account();
        organizer.setName("Organizer");
        organizer.setEmail("organizer@sikka.test");
        organizer.setPasswordHash("hash");
        organizer.setRole("USER");
        organizer.setStatus("ACTIVE");
        accountRepository.save(organizer);

        group = new VisitGroup();
        group.setName("Test Group");
        group.setInviteCodeHash("hash-of-code");
        group.setOrganizer(organizer);
        visitGroupRepository.save(group);
    }

    @Test
    void findVisitGroupByInviteCodeHashTest() {
        VisitGroup found = visitGroupRepository.findVisitGroupByInviteCodeHash("hash-of-code");

        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Test Group");
    }

    @Test
    void findVisitGroupByInviteCodeHashWrongCodeTest() {
        VisitGroup found = visitGroupRepository.findVisitGroupByInviteCodeHash("other-hash");

        assertThat(found).isNull();
    }
}