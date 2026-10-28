package com.example.sikka.Repository;

import com.example.sikka.Model.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Integer> {

    boolean existsByVisit_IdAndStatusAndVerifiedAtIsNotNull(Integer visitId, String status);

    PaymentOrder findPaymentOrderById(Integer id);

    List<PaymentOrder> findPaymentOrdersByBuyerAccountId(Integer accountId);

    List<PaymentOrder> findPaymentOrdersByVisitId(Integer visitId);

    PaymentOrder findPaymentOrderByMoyasarPaymentId(String moyasarPaymentId);

    PaymentOrder findTopByOrderGroupKeyOrderByAttemptNoDesc(String orderGroupKey);
}