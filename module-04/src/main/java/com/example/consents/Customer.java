package com.example.consents;

import java.time.Instant;

/** A bank customer. {@code segment} is {@code RETAIL} or {@code BUSINESS}. */
public record Customer(String id, String name, String email, String segment, Instant createdAt) {
}
