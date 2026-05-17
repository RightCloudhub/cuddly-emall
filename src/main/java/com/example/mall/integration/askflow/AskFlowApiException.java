package com.example.mall.integration.askflow;

/** Thrown when AskFlow returns a non-2xx response or the call fails outright. */
public class AskFlowApiException extends RuntimeException {

    private final int statusCode;

    public AskFlowApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public AskFlowApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
