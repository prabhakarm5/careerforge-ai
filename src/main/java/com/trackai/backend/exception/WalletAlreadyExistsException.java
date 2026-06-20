package com.trackai.backend.exception;

public class WalletAlreadyExistsException
        extends RuntimeException {

    public WalletAlreadyExistsException(
            String message) {

        super(message);
    }
}