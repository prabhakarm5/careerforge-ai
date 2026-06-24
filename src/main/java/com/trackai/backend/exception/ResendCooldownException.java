package com.trackai.backend.exception;

public class ResendCooldownException
        extends RuntimeException {

    public ResendCooldownException(
            String message) {

        super(message);
    }
}