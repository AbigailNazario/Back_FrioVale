package com.pi.frioVale.exception;

import org.springframework.http.HttpStatus;

/** Violação de regra de negócio. Por padrão responde 409 (conflito de estado). */
public class BusinessRuleException extends RuntimeException {

    private final HttpStatus status;

    public BusinessRuleException(String message) {
        this(message, HttpStatus.CONFLICT);
    }

    public BusinessRuleException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
