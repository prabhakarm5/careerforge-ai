package com.trackai.backend.exception;

// Thrown when a requested resource (user, entity, etc.) is not found in the system
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}