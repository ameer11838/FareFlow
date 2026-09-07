package com.fareflow.xrpl;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface XrplWalletRepository extends JpaRepository<XrplWallet, Long> {
    Optional<XrplWallet> findByUserId(Long userId);
}
