package com.example.accounts;

/**
 * An error body in the format of RFC 9457 (Problem Details for HTTP APIs), sent with the content type
 * {@code application/problem+json}.
 */
public record ProblemDetail(String type, String title, int status, String detail, String instance) {

    static ProblemDetail notFound(String detail, String instance) {
        return new ProblemDetail("about:blank", "Not Found", 404, detail, instance);
    }

    static ProblemDetail badRequest(String detail, String instance) {
        return new ProblemDetail("about:blank", "Bad Request", 400, detail, instance);
    }

    static ProblemDetail methodNotAllowed(String detail, String instance) {
        return new ProblemDetail("about:blank", "Method Not Allowed", 405, detail, instance);
    }
}
