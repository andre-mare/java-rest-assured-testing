package com.example.http;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * An error body in the format of RFC 9457 (Problem Details for HTTP APIs), sent as {@code application/problem+json}.
 * {@code errors} lists field-level problems for validation failures and is left out when empty.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(String type, String title, int status, String detail, String instance,
                            List<Map<String, String>> errors) {
}
