package com.example.sikka.Controller;
import com.example.sikka.DtoIn.ConfirmPaymentDtoIn;
import jakarta.validation.Valid;
import com.example.sikka.Model.Account;
import com.example.sikka.Service.PaymentOrderService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payment-order")
@AllArgsConstructor
public class PaymentOrderController {

    private final PaymentOrderService paymentOrderService;

    @GetMapping("/get/my/orders")
    public ResponseEntity<?> getMyPaymentOrders(@AuthenticationPrincipal Account account){
        return ResponseEntity.status(200).body(paymentOrderService.getMyPaymentOrders(account.getId()));
    }

    @GetMapping("/get/details/{paymentOrderId}")
    public ResponseEntity<?> getPaymentOrderById(@AuthenticationPrincipal Account account, @PathVariable Integer paymentOrderId){
        return ResponseEntity.status(200).body(paymentOrderService.getPaymentOrderById(account.getId(), paymentOrderId));
    }

    @GetMapping("/get/visit/{visitId}")
    public ResponseEntity<?> getVisitPaymentOrders(@AuthenticationPrincipal Account account, @PathVariable Integer visitId){
        return ResponseEntity.status(200).body(paymentOrderService.getVisitPaymentOrders(account.getId(), visitId));
    }

    @PostMapping("/add/{visitId}")
    public ResponseEntity<?> addPaymentOrder(@AuthenticationPrincipal Account account, @PathVariable Integer visitId){
        return ResponseEntity.status(200).body(paymentOrderService.addPaymentOrder(account.getId(), visitId));
    }

    @PostMapping("/confirm/{paymentOrderId}")
    public ResponseEntity<?> confirmPayment(@AuthenticationPrincipal Account account, @PathVariable Integer paymentOrderId,
                                            @RequestBody @Valid ConfirmPaymentDtoIn dto){
        return ResponseEntity.status(200).body(paymentOrderService.confirmPayment(account.getId(), paymentOrderId, dto.getPaymentId()));
    }
}