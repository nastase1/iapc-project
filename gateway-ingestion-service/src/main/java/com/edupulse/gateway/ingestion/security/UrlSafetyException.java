package com.edupulse.gateway.ingestion.security;

/** Thrown when an external source violates the security policy; the message names the control that blocked it. */
public class UrlSafetyException extends RuntimeException {

    public UrlSafetyException(String message) {
        super(message);
    }
}
