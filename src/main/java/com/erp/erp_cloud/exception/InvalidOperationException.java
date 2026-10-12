package com.erp.erp_cloud.exception;

public class InvalidOperationException extends BusinessException {
    public InvalidOperationException(String message) {
        super(message);
    }

    public InvalidOperationException(String message, String errorCode) {
        super(message, errorCode);
    }

    public InvalidOperationException(String message, String errorCode, Object details) {
        super(message, errorCode, details);
    }
}