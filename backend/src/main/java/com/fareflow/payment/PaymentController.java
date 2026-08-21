package com.fareflow.payment;

import com.fareflow.auth.CurrentUserService;
import com.fareflow.payment.dto.ConfirmPaymentRequest;
import com.fareflow.payment.dto.CreateJourneyPaymentRequest;
import com.fareflow.payment.dto.PaymentIntentResponse;
import com.fareflow.payment.dto.PaymentReconciliationResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Payment lifecycle endpoints, always scoped to the authenticated rider. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUserService currentUserService;

    public PaymentController(PaymentService paymentService,
                             CurrentUserService currentUserService) {
        this.paymentService = paymentService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/intents")
    public ResponseEntity<PaymentIntentResponse> create(
            @Valid @RequestBody CreateJourneyPaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        PaymentService.Creation creation = paymentService.createJourneyIntent(
                currentUserService.require(), request, idempotencyKey);
        return ResponseEntity.status(creation.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(creation.payment());
    }

    @PostMapping("/intents/{id}/confirm")
    public PaymentIntentResponse confirm(@PathVariable UUID id,
                                         @RequestBody(required = false) ConfirmPaymentRequest request) {
        return paymentService.confirm(currentUserService.require(), id, request);
    }

    @PostMapping("/intents/{id}/retry")
    public PaymentIntentResponse retry(@PathVariable UUID id,
                                       @RequestBody(required = false) ConfirmPaymentRequest request) {
        return paymentService.retry(currentUserService.require(), id, request);
    }

    @PostMapping("/intents/{id}/refund")
    public PaymentIntentResponse refund(@PathVariable UUID id) {
        return paymentService.refund(currentUserService.require(), id);
    }

    @GetMapping("/intents/{id}")
    public PaymentIntentResponse get(@PathVariable UUID id) {
        return paymentService.get(currentUserService.require(), id);
    }

    @GetMapping("/intents")
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        var payments = paymentService.list(currentUserService.require(),
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100)));
        return Map.of(
                "content", payments.getContent(),
                "page", payments.getNumber(),
                "size", payments.getSize(),
                "totalElements", payments.getTotalElements(),
                "totalPages", payments.getTotalPages());
    }

    @GetMapping("/reconciliation")
    public PaymentReconciliationResponse reconcile() {
        return paymentService.reconcile(currentUserService.require());
    }
}
