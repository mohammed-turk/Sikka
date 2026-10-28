package com.example.sikka.Service;
import tools.jackson.databind.JsonNode;
import com.example.sikka.Api.ApiException;
import com.example.sikka.Model.Account;
import com.example.sikka.Model.PaymentOrder;
import com.example.sikka.Model.Visit;
import com.example.sikka.Repository.AccountRepository;
import com.example.sikka.Repository.PaymentOrderRepository;
import com.example.sikka.Repository.VisitRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Set;

@Service
@AllArgsConstructor
public class PaymentOrderService {

    private final jakarta.persistence.EntityManager entityManager;
    private final PaymentOrderRepository paymentOrderRepository;
    private final AccountRepository accountRepository;
    private final VisitRepository visitRepository;
    private final com.example.sikka.Repository.PlanHistoryRepository planHistoryRepository;
    private final MoyasarService moyasarService;

    public List<PaymentOrder> getMyPaymentOrders(Integer accountId){
        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        return paymentOrderRepository.findPaymentOrdersByBuyerAccountId(accountId);
    }

    public PaymentOrder getPaymentOrderById(Integer accountId, Integer paymentOrderId){
        PaymentOrder paymentOrder = paymentOrderRepository.findPaymentOrderById(paymentOrderId);

        if (paymentOrder == null){
            throw new ApiException("Payment order not found");
        }

        if (!paymentOrder.getBuyerAccount().getId().equals(accountId)){
            throw new ApiException("You cannot access another user's payment order");
        }

        return paymentOrder;
    }

    public List<PaymentOrder> getVisitPaymentOrders(Integer accountId, Integer visitId){
        Visit visit = visitRepository.findVisitById(visitId);

        if (visit == null){
            throw new ApiException("Visit not found");
        }

        if (!visit.getOrganizer().getId().equals(accountId)){
            throw new ApiException("Only the visit organizer can view its payment orders");
        }

        return paymentOrderRepository.findPaymentOrdersByVisitId(visitId);
    }

    @Transactional
    public PaymentOrder addPaymentOrder(Integer accountId, Integer visitId){
        Account account = accountRepository.findAccountById(accountId);

        if (account == null){
            throw new ApiException("Account not found");
        }

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException("Account is not active");
        }
        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);

        if (visit == null){
            throw new ApiException("Visit not found");
        }

        if (!visit.getOrganizer().getId().equals(accountId)){
            throw new ApiException("Only the visit organizer can pay for the plan");
        }

        if (visit.getVisitDate() == null || visit.getVisitDate().isBefore(LocalDate.now(java.time.ZoneId.of("Asia/Riyadh")))){
            throw new ApiException("Cannot pay for a past visit");
        }

        if (!java.util.Set.of("DRAFT", "PLANNING", "READY").contains(visit.getStatus()) || visit.getStartedAt() != null){
            throw new ApiException("Payment is only allowed for draft visits");
        }

        List<PaymentOrder> paymentOrders = paymentOrderRepository.findPaymentOrdersByVisitId(visitId);

        PaymentOrder latestPaymentOrder = null;

        for (PaymentOrder paymentOrder : paymentOrders){
            if (isVerifiedGenerationPayment(paymentOrder, accountId, visitId)) throw new ApiException("This visit is already paid");
            if ("PAID".equals(paymentOrder.getStatus())) throw new ApiException("Payment verification is incomplete");

            if ("PENDING".equals(paymentOrder.getStatus())){
                throw new ApiException("A pending payment order already exists for this visit");
            }

            if (!"FAILED".equals(paymentOrder.getStatus())){
                throw new ApiException("An existing payment order has an unsupported status");
            }

            if (latestPaymentOrder == null || paymentOrder.getId() > latestPaymentOrder.getId()){
                latestPaymentOrder = paymentOrder;
            }
        }

        PaymentOrder newPaymentOrder = new PaymentOrder();

        if (latestPaymentOrder == null){
            newPaymentOrder.setOrderGroupKey(UUID.randomUUID().toString());
            newPaymentOrder.setAttemptNo(1);
            newPaymentOrder.setKind("INITIAL");
        } else {
            newPaymentOrder.setOrderGroupKey(latestPaymentOrder.getOrderGroupKey());
            newPaymentOrder.setAttemptNo(latestPaymentOrder.getAttemptNo() + 1);
            newPaymentOrder.setKind("RETRY");
        }

        newPaymentOrder.setAmountSar(getPrice(visit));
        newPaymentOrder.setStatus("PENDING");
        newPaymentOrder.setVisit(visit);
        newPaymentOrder.setBuyerAccount(account);

        return paymentOrderRepository.save(newPaymentOrder);
    }

    // confirms a payment by asking Moyasar itself; the frontend's word is never trusted
    @Transactional
    public PaymentOrder confirmPayment(Integer accountId, Integer paymentOrderId, String moyasarPaymentId) {
        PaymentOrder order = paymentOrderRepository.findPaymentOrderById(paymentOrderId);
        if (order == null)
            throw new ApiException("Payment order not found");
        if (!order.getBuyerAccount().getId().equals(accountId))
            throw new ApiException("You cannot confirm another user's payment order");
        if ("PAID".equals(order.getStatus()))
            return order;
        if (!"PENDING".equals(order.getStatus()))
            throw new ApiException("Only a pending payment order can be confirmed");

        // one Moyasar payment can settle only one order
        PaymentOrder used = paymentOrderRepository.findPaymentOrderByMoyasarPaymentId(moyasarPaymentId);
        if (used != null && !used.getId().equals(order.getId()))
            throw new ApiException("This payment was already used for another order");

        JsonNode payment = moyasarService.fetchPayment(moyasarPaymentId);
        String status = payment.path("status").asString();

        if ("failed".equals(status)) {
            String reason = payment.path("source").path("message").asString();
            if (reason == null || reason.isBlank())
                reason = "Payment failed";
            order.setStatus("FAILED");
            order.setFailureReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
            return paymentOrderRepository.save(order);
        }
        if (!"paid".equals(status))
            throw new ApiException("Payment is not completed yet");

        // Moyasar amounts are in halalas: 29.00 SAR = 2900
        long expected = order.getAmountSar().multiply(new BigDecimal("100")).longValue();
        if (payment.path("amount").asLong() != expected || !"SAR".equals(payment.path("currency").asString()))
            throw new ApiException("Payment amount does not match the order");
        if (!String.valueOf(order.getId()).equals(payment.path("metadata").path("order_id").asString()))
            throw new ApiException("Payment does not belong to this order");

        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Riyadh"));
        order.setStatus("PAID");
        order.setMoyasarPaymentId(moyasarPaymentId);
        order.setVerifiedAt(now);
        order.setPaidAt(now);
        order.setFailureReason(null);
        return paymentOrderRepository.save(order);
    }

    private BigDecimal getPrice(Visit visit) {
        if (!Set.of("SOLO", "FRIENDS", "FAMILY").contains(visit.getTripType())) throw new ApiException("Valid tripType is required");
        return new BigDecimal("SOLO".equals(visit.getTripType()) ? "19.00" : "29.00");
    }

    @Transactional(readOnly = true)
    public boolean hasPaidVisit(Integer accountId, Integer visitId) {
        return isPaid(accountId, getPlanningVisit(accountId, visitId));
    }

    private boolean isPaid(Integer accountId, Visit visit) {
        return paymentOrderRepository.findPaymentOrdersByVisitId(visit.getId()).stream().anyMatch(p -> isVerifiedGenerationPayment(p, accountId, visit.getId()));
    }

    private long occupiedFreeSlots(Integer accountId) {
        Account account = accountRepository.findAccountById(accountId);
        long archived = account == null || account.getArchivedFreeVisits() == null ? 0 : account.getArchivedFreeVisits();
        return archived + visitRepository.findAllByOrganizer_Id(accountId).stream().filter(v -> Boolean.TRUE.equals(v.getFreeSlotReserved())).filter(v -> !"CANCELLED".equals(v.getStatus()) || v.getStartedAt() != null).filter(v -> v.getStartedAt() != null || !isPaid(accountId, v)).count();
    }

    @Transactional(readOnly = true)
    public com.example.sikka.DtoOut.PlanningAccessDtoOut getPlanningAccess(Integer accountId, Integer visitId) {
        Visit visit = getPlanningVisit(accountId, visitId);
        boolean paid = isPaid(accountId, visit);
        boolean reusable = visit.getReusedFromVisitId() != null;
        boolean reserved = Boolean.TRUE.equals(visit.getFreeSlotReserved());
        int remaining = (int) Math.max(0, 2 - occupiedFreeSlots(accountId));
        boolean freeAllowed = !reusable && (reserved || remaining > 0);
        boolean paymentRequired = !paid && !freeAllowed;
        boolean allowed = visit.getStartedAt() == null && Set.of("DRAFT", "PLANNING", "READY").contains(visit.getStatus());
        String status = paid ? "PAID" : reusable ? "REUSE_FREE_PAYMENT_REQUIRED_FOR_EDITS" : freeAllowed ? "FREE" : "PAYMENT_REQUIRED";
        return new com.example.sikka.DtoOut.PlanningAccessDtoOut(remaining, paymentRequired, getPrice(visit), status, allowed && (paid || freeAllowed));
    }

    @Transactional
    public Integer reserveEntitlement(Integer accountId, Integer visitId, String requestKey, String operationType) {
        Account account = planHistoryRepository.findAccountByIdForGenerationUpdate(accountId);
        if (account == null || requestKey == null || requestKey.isBlank() || requestKey.length() > 80 || !Set.of("INITIAL", "REGENERATE").contains(operationType)) {
            throw new ApiException("Valid generation request details are required");
        }
        Visit visit = visitRepository.findVisitByIdForUpdate(visitId);
        if (visit == null || !visit.getOrganizer().getId().equals(accountId) || !"ACTIVE".equals(account.getStatus())) throw new ApiException("An active visit organizer is required");
        if (visit.getStartedAt() != null || !Set.of("DRAFT", "PLANNING", "READY").contains(visit.getStatus())) throw new ApiException("Generation entitlement is only available before the visit starts");
        com.example.sikka.Model.PlanHistory previous = planHistoryRepository.findPlanHistoryByAccount_IdAndRequestKey(accountId, requestKey);
        if (previous != null) {
            if (!previous.getVisit().getId().equals(visitId) || !previous.getOperationType().equals(operationType)) throw new ApiException("Request key is already used for another operation");
            return previous.getId();
        }
        boolean paid = isPaid(accountId, visit);
        boolean reused = visit.getReusedFromVisitId() != null;
        boolean reserved = Boolean.TRUE.equals(visit.getFreeSlotReserved());
        if (!paid && (reused || !reserved && occupiedFreeSlots(accountId) >= 2)) throw new ApiException("A server-verified payment is required");
        if (!paid && !reserved) {
            visit.setFreeSlotReserved(true);
            visitRepository.save(visit);
        }
        com.example.sikka.Model.PlanHistory history = new com.example.sikka.Model.PlanHistory();
        history.setAccount(account);
        history.setVisit(visit);
        history.setRequestKey(requestKey);
        history.setOperationType(operationType);
        history.setBillingMode(paid ? "PAID" : "FREE");
        return planHistoryRepository.save(history).getId();
    }

    @Transactional
    public void consumeEntitlement(Integer accountId, Integer planHistoryId) {
        planHistoryRepository.findAccountByIdForGenerationUpdate(accountId);
        com.example.sikka.Model.PlanHistory history = planHistoryRepository.findPlanHistoryByIdForUpdate(planHistoryId);
        if (history == null || !history.getAccount().getId().equals(accountId) || !"PROCESSING".equals(history.getStatus())) throw new ApiException("Processing generation request not found");
        if ("PAID".equals(history.getBillingMode()) && !isPaid(accountId, history.getVisit())) throw new ApiException("Server-verified payment is required");
    }

    @Transactional
    public void releaseEntitlement(Integer accountId, Integer planHistoryId) {
        planHistoryRepository.findAccountByIdForGenerationUpdate(accountId);
        com.example.sikka.Model.PlanHistory history = planHistoryRepository.findPlanHistoryByIdForUpdate(planHistoryId);
        if (history == null || !history.getAccount().getId().equals(accountId) || "SUCCEEDED".equals(history.getStatus())) throw new ApiException("Only unsuccessful requests may be released");
        history.setStatus("FAILED");
        history.setCountsTowardQuota(false);
        planHistoryRepository.save(history);
        Visit visit = visitRepository.findVisitByIdForUpdate(history.getVisit().getId());
        if (visit != null && visit.getStartedAt() == null && Boolean.TRUE.equals(visit.getFreeSlotReserved())) {
            boolean activeHistory = planHistoryRepository.findAllByAccount_IdOrderByStartedAtDescIdDesc(accountId).stream()
                    .anyMatch(h -> h.getVisit().getId().equals(visit.getId()) && Set.of("PROCESSING", "SUCCEEDED").contains(h.getStatus()));
            if (!activeHistory) {
                visit.setFreeSlotReserved(false);
                visitRepository.save(visit);
            }
        }
    }

    private Visit getPlanningVisit(Integer accountId, Integer visitId) {
        Account account = accountRepository.findAccountById(accountId);
        Visit visit = visitRepository.findVisitById(visitId);
        if (account == null || !"ACTIVE".equals(account.getStatus()) || visit == null || !visit.getOrganizer().getId().equals(accountId)) {
            throw new ApiException("An active visit organizer is required");
        }
        return visit;
    }

    private boolean isVerifiedGenerationPayment(PaymentOrder payment, Integer accountId, Integer visitId) {
        return payment != null && payment.getVisit() != null && payment.getBuyerAccount() != null
                && payment.getBuyerAccount().getId().equals(accountId) && payment.getVisit().getId().equals(visitId)
                && "PAID".equals(payment.getStatus()) && payment.getVerifiedAt() != null
                && payment.getMoyasarPaymentId() != null && !payment.getMoyasarPaymentId().isBlank()
                && payment.getAmountSar() != null && payment.getAmountSar().compareTo(getPrice(payment.getVisit())) == 0;
    }

}
