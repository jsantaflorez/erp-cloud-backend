package com.erp.erp_cloud.exception;


public class BusinessException extends RuntimeException {

    private final String errorCode; // nullable — null means "use the raw message" (current behavior)

    public BusinessException(String message) {
        super(message);
        this.errorCode = null;
    }

    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}