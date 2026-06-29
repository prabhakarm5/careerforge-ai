package com.trackai.backend.exception;

public class TokenExpiredEXception extends RuntimeException {

    public TokenExpiredEXception(String message) {
        super(message);
    }

}
