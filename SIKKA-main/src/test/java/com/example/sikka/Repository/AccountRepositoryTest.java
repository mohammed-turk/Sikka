package com.example.sikka.Repository;

import com.example.sikka.Model.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.test.database.replace=none")
class AccountRepositoryTest {

    @Autowired
    AccountRepository accountRepository;

    Account account;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setName("Test User");
        account.setEmail("test@sikka.test");
        account.setPasswordHash("hash");
        account.setRole("USER");
        account.setStatus("ACTIVE");
    }

    @Test
    void findAccountByEmailTest() {
        accountRepository.save(account);

        Account found = accountRepository.findAccountByEmail("test@sikka.test");

        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Test User");
    }

    @Test
    void findAccountByEmailUnknownTest() {
        Account found = accountRepository.findAccountByEmail("nobody@sikka.test");

        assertThat(found).isNull();
    }
}