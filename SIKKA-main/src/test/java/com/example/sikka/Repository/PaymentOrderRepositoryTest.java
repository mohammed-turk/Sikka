package com.example.sikka.Repository;

import com.example.sikka.Model.Account;
import com.example.sikka.Model.PaymentOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.test.database.replace=none")
class PaymentOrderRepositoryTest {

    @Autowired
    PaymentOrderRepository paymentOrderRepository;

    @Autowired
    AccountRepository accountRepository;

    Account buyer;

    @BeforeEach
    void setUp() {
        buyer = new Account();
        buyer.setName("Buyer");
        buyer.setEmail("buyer@sikka.test");
        buyer.setPasswordHash("hash");
        buyer.setRole("USER");
        buyer.setStatus("ACTIVE");
        accountRepository.save(buyer);

        PaymentOrder order = new PaymentOrder();
        order.setOrderGroupKey("group-key-1");
        order.setAmountSar(new BigDecimal("29.00"));
        order.setStatus("PAID");
        order.setMoyasarPaymentId("pay-123");
        order.setBuyerAccount(buyer);
        paymentOrderRepository.save(order);
    }

    @Test
    void findPaymentOrderByMoyasarPaymentIdTest() {
        PaymentOrder found = paymentOrderRepository.findPaymentOrderByMoyasarPaymentId("pay-123");

        assertThat(found).isNotNull();
        assertThat(found.getStatus()).isEqualTo("PAID");
    }

    @Test
    void findPaymentOrdersByBuyerAccountIdTest() {
        List<PaymentOrder> orders = paymentOrderRepository.findPaymentOrdersByBuyerAccountId(buyer.getId());

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getAmountSar()).isEqualByComparingTo("29.00");
    }
}