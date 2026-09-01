package com.fareflow.session;

import com.fareflow.auth.CurrentUserService;
import com.fareflow.payment.PaymentService;
import com.fareflow.payment.dto.PaymentIntentResponse;
import com.fareflow.session.dto.PayTransitSessionRequest;
import com.fareflow.session.dto.AdvanceTransitSessionRequest;
import com.fareflow.session.dto.StartTransitSessionRequest;
import com.fareflow.session.dto.TransitSessionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** PLAN → START → TRACK → END → PAY endpoints for usage-priced transit. */
@RestController
@RequestMapping("/api/transit-sessions")
public class TransitSessionController {

    private final TransitSessionService sessionService;
    private final PaymentService paymentService;
    private final CurrentUserService currentUserService;

    public TransitSessionController(TransitSessionService sessionService,
                                    PaymentService paymentService,
                                    CurrentUserService currentUserService) {
        this.sessionService = sessionService;
        this.paymentService = paymentService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<TransitSessionResponse> start(
            @Valid @RequestBody StartTransitSessionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        var creation = sessionService.start(
                currentUserService.require(), request, idempotencyKey);
        return ResponseEntity.status(creation.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(creation.session());
    }

    @GetMapping("/active")
    public ResponseEntity<TransitSessionResponse> active() {
        return sessionService.active(currentUserService.require())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public TransitSessionResponse get(@PathVariable UUID id) {
        return sessionService.get(currentUserService.require(), id);
    }

    @PostMapping("/{id}/advance")
    public TransitSessionResponse advance(
            @PathVariable UUID id,
            @RequestBody(required = false) AdvanceTransitSessionRequest request) {
        String outcome = request == null ? null : request.outcome();
        return sessionService.advance(currentUserService.require(), id,
                TransitProgressOutcome.parse(outcome));
    }

    @PostMapping("/{id}/end")
    public TransitSessionResponse end(@PathVariable UUID id) {
        return sessionService.end(currentUserService.require(), id);
    }

    @PostMapping("/{id}/pay")
    public PaymentIntentResponse pay(
            @PathVariable UUID id,
            @RequestBody(required = false) PayTransitSessionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        var user = currentUserService.require();
        TransitSession session = sessionService.owned(user, id);
        PayTransitSessionRequest command = request == null
                ? new PayTransitSessionRequest(null, null) : request;
        return paymentService.payTransitSession(
                user, session, command.paymentMethodOrWallet(), idempotencyKey,
                command.simulatedCardToken());
    }
}
