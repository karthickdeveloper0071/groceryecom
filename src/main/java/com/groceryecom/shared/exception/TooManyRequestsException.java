package com.groceryecom.shared.exception;

/** 429: the caller has made too many requests and should retry later. */
public class TooManyRequestsException extends ApplicationException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message, "RATE_LIMITED", 429);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
