package com.fareflow.wallet;

import com.fareflow.auth.CurrentUserService;
import com.fareflow.wallet.dto.WalletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;
    private final CurrentUserService currentUserService;

    public WalletController(WalletService walletService, CurrentUserService currentUserService) {
        this.walletService = walletService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public WalletResponse wallet() {
        return walletService.forUser(currentUserService.require());
    }
}
