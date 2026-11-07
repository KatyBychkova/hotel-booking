package ru.mephi.stay.common;

import java.time.Instant;

public record ErrorDto(Instant timestamp, int status, String message, String path, String traceId) {
    public static ErrorDto of(int status, String message, String path, String traceId) {
        return new ErrorDto(Instant.now(), status, message, path, traceId);
    }
}
