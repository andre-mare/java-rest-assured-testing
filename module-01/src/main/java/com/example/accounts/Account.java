package com.example.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A bank account as the Accounts API returns it.
 * <p>
 * Jackson 3 writes the record components as JSON fields, {@link BigDecimal} as a JSON number and
 * {@link LocalDate} as an ISO-8601 string such as {@code "2021-03-15"}.
 */
public record Account(
        String id,
        String iban,
        String owner,
        AccountType type,
        String currency,
        BigDecimal balance,
        AccountStatus status,
        LocalDate openedOn) {
}
