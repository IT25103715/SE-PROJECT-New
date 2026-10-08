package com.cinemahub.exception;

/** Thrown by a service layer when a lookup by id fails. Mapped to the 404 page by GlobalExceptionHandler. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
