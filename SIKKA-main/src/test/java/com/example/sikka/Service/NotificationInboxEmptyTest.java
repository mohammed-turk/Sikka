package com.example.sikka.Service;

import com.example.sikka.Model.Account;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationInboxEmptyTest {
    @Mock private AccountRepository accounts;
    @Mock private NotificationRepository notifications;
    @InjectMocks private NotificationService service;

    @Test
    void activeAccountHasAnEmptyInboxWithoutAnError() {
        Account account = new Account();
        account.setId(1);
        account.setStatus("ACTIVE");
        when(accounts.findAccountById(1)).thenReturn(account);
        when(notifications.findAllByAccount_IdAndChannelAndStatusOrderByCreatedAtDescIdDesc(1, "IN_APP", "SENT")).thenReturn(List.of());
        assertTrue(service.getMyNotifications(1).isEmpty());
    }
}
