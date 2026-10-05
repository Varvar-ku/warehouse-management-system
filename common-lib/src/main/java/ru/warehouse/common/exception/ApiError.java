package ru.warehouse.common.exception;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Единый формат ошибки API (п. 15.1 ТЗ): code, message, details, trace_id. */
public record ApiError(
        String code,
        String message,
        List<String> details,
        @JsonProperty("trace_id") String traceId) {
}