package com.nsemcx.trading.event;

import java.time.Instant;

/**
 * Generic API response envelope used by all REST controllers.
 * Java 21 record — immutable, compact, pattern-match friendly.
 *
 * @param <T> type of the payload
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        Instant timestamp
) {

    /** Convenience constructor so callers don't supply timestamp manually. */
    public ApiResponse(boolean success, T data, String message) {
        this(success, data, message, Instant.now());
    }

    // ── Factory methods ──────────────────────────────────────────────────────

    /**
     * Successful response with a payload and a descriptive message.
     */
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, Instant.now());
    }

    /**
     * Successful response with a payload and a default "OK" message.
     */
    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, "OK");
    }

    /**
     * Successful response with no payload (e.g., DELETE / action endpoints).
     */
    public static <Void> ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, "OK", Instant.now());
    }

    /**
     * Error response with no payload.
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, Instant.now());
    }

    /**
     * Error response carrying error-detail data (e.g., a validation error map).
     */
    public static <T> ApiResponse<T> error(String message, T errorDetail) {
        return new ApiResponse<>(false, errorDetail, message, Instant.now());
    }

    // ── Derived helpers ──────────────────────────────────────────────────────

    /** Returns true when the response carries a non-null data payload. */
    public boolean hasData() {
        return data != null;
    }
}
