package com.fareflow.ledger;

import com.fareflow.ledger.dto.LedgerEntryResponse;
import com.fareflow.auth.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledgerService;
    private final CurrentUserService currentUserService;

    public LedgerController(LedgerService ledgerService, CurrentUserService currentUserService) {
        this.ledgerService = ledgerService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "25") int size) {
        Page<LedgerEntry> entries = ledgerService.findForUser(
                currentUserService.requireId(), PageRequest.of(page, Math.min(size, 100)));

        return Map.of(
                "content", entries.getContent().stream().map(LedgerEntryResponse::from).toList(),
                "page", entries.getNumber(),
                "size", entries.getSize(),
                "totalElements", entries.getTotalElements(),
                "totalPages", entries.getTotalPages());
    }
}
