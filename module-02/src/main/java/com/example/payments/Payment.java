package com.example.payments;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * A payment as the Payments API stores and returns it. Amounts travel as strings, such as {@code "25.00"}, as in most
 * open banking APIs, so no precision is lost in JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)   // a missing reference or nickname is left out, not sent as null
public record Payment(
        String id,
        PaymentStatus status,
        String debtorIban,
        String creditorIban,
        String creditorName,
        String amount,
        String currency,
        String reference,
        Instant createdAt) {

    Payment withStatus(PaymentStatus newStatus) {
        return new Payment(id, newStatus, debtorIban, creditorIban, creditorName, amount, currency, reference, createdAt);
    }

    Payment withReference(String newReference) {
        return new Payment(id, status, debtorIban, creditorIban, creditorName, amount, currency, newReference, createdAt);
    }
}
