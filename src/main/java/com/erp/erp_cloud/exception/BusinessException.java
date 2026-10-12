package com.erp.erp_cloud.exception;


public class BusinessException extends RuntimeException {

    private final String errorCode; // nullable — null means "use the raw message" (current behavior)

    // Optional structured payload riding alongside the stable errorCode --
    // e.g. the exact list of months still open before a fiscal year can
    // close (see AccountingPeriodService.validateAllMonthsClosedBeforeYearEnd()).
    // Kept generic (Object) and nullable so every existing call site that
    // doesn't need it is completely unaffected: errorCode alone still
    // drives the frontend's fixed translation via API_ERROR_LABELS, and
    // this is purely additive detail for the handful of errors precise
    // enough to want it.
    private final Object details;

    public BusinessException(String message) {
        super(message);
        this.errorCode = null;
        this.details = null;
    }

    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
    }

    public BusinessException(String message, String errorCode, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object getDetails() {
        return details;
    }
}