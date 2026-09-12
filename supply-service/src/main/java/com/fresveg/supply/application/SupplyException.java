package com.fresveg.supply.application;

import org.springframework.http.HttpStatus;

public class SupplyException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public SupplyException(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
