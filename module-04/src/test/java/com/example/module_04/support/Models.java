package com.example.module_04.support;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The tests' own view of the API's JSON, as records. They are written from the API's contract, not copied from its
 * code, so a mismatch between the two shows up as a failing test.
 */
public final class Models {

    private Models() {
    }

    public record NewCustomer(String name, String email, String segment) {
    }

    public record Customer(String id, String name, String email, String segment, Instant createdAt) {
    }

    /** One page of a list. Generic, so {@code Page<Customer>} needs a {@code TypeRef} to be mapped. */
    public record Page<T>(List<T> items, int page, int size, int totalItems, int totalPages) {
    }

    public record NewConsent(String customerId, List<String> permissions) {
    }

    public record Link(String href) {
    }

    /** {@code _links} isn't a valid Java name, so the record component gets a Jackson annotation. */
    public record Consent(String consentId, String customerId, List<String> permissions, String status,
                          @JsonProperty("_links") Map<String, Link> links) {
    }

    /** Deliberately without {@code productCode} and {@code balance}, which the API also sends. */
    public record Account(String accountId, String iban, String currency, String type, String nickname) {
    }
}
