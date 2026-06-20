package com.trackai.backend.exception;

import com.trackai.backend.dto.WalletExceptionResponse;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class WalletExceptionHandler {

        // Wallet not found
        @ExceptionHandler(WalletNotFoundException.class)
        public ResponseEntity<WalletExceptionResponse> handleWalletNotFoundException(

                        WalletNotFoundException ex,

                        HttpServletRequest request) {

                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(

                                                WalletExceptionResponse.builder()
                                                                .success(false)
                                                                .message(ex.getMessage())
                                                                .path(request.getRequestURI())
                                                                .timestamp(LocalDateTime.now())
                                                                .build());
        }

        // Insufficient tokens
        @ExceptionHandler(InsufficientTokensException.class)
        public ResponseEntity<WalletExceptionResponse> handleInsufficientTokensException(

                        InsufficientTokensException ex,

                        HttpServletRequest request) {

                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(

                                                WalletExceptionResponse.builder()
                                                                .success(false)
                                                                .message(ex.getMessage())
                                                                .path(request.getRequestURI())
                                                                .timestamp(LocalDateTime.now())
                                                                .build());
        }

        // Wallet already exists
        @ExceptionHandler(WalletAlreadyExistsException.class)
        public ResponseEntity<WalletExceptionResponse> handleWalletAlreadyExistsException(

                        WalletAlreadyExistsException ex,

                        HttpServletRequest request) {

                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(

                                                WalletExceptionResponse.builder()
                                                                .success(false)
                                                                .message(ex.getMessage())
                                                                .path(request.getRequestURI())
                                                                .timestamp(LocalDateTime.now())
                                                                .build());
        }
}