package com.trackai.backend.controller;

import com.trackai.backend.dto.WalletResponse;
import com.trackai.backend.dto.WalletTransactionResponse;
import com.trackai.backend.service.WalletService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    // Get current wallet
    @GetMapping
    public ResponseEntity<WalletResponse> getCurrentWallet() {

        return ResponseEntity.ok(
                walletService.getCurrentWallet());
    }

    // Get transaction history
    @GetMapping("/history")
    public ResponseEntity<List<WalletTransactionResponse>> getTransactionHistory() {

        return ResponseEntity.ok(
                walletService.getTransactionHistory());
    }
}