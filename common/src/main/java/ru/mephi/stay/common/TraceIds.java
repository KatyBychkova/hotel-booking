package ru.mephi.stay.common;

import java.util.UUID;

public final class TraceIds {
    public static final String HEADER = "X-Request-Id";
    private TraceIds() {}

    public static String from(String value) {
        if (value != null && value.matches("[a-zA-Z0-9._-]{1,80}")) {
            return value;
        }
        return UUID.randomUUID().toString();
    }
}
