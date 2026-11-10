package com.example.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Read-only, in-memory store of the four accounts that every Module 1 test sees.
 * <p>
 * The data never changes, so tests can assert exact values without setting anything up first.
 */
public final class AccountRepository {

    private final Map<String, Account> accounts = new LinkedHashMap<>();

    public AccountRepository() {
        add(new Account("acc-1001", "NL91ABNA0417164300", "Anna de Vries", AccountType.CURRENT, "EUR",
                new BigDecimal("1250.50"), AccountStatus.ACTIVE, LocalDate.of(2019, 4, 1)));
        add(new Account("acc-1002", "NL69INGB0123456789", "Anna de Vries", AccountType.SAVINGS, "EUR",
                new BigDecimal("15000.00"), AccountStatus.ACTIVE, LocalDate.of(2021, 3, 15)));
        add(new Account("acc-1003", "NL20INGB0001234567", "Jan Jansen", AccountType.CURRENT, "EUR",
                new BigDecimal("-42.10"), AccountStatus.BLOCKED, LocalDate.of(2017, 11, 30)));
        add(new Account("acc-1004", "GB33BUKB20201555555555", "Emily Clarke", AccountType.CURRENT, "GBP",
                new BigDecimal("980.00"), AccountStatus.ACTIVE, LocalDate.of(2023, 6, 5)));
    }

    private void add(Account account) {
        accounts.put(account.id(), account);
    }

    public Optional<Account> findById(String id) {
        return Optional.ofNullable(accounts.get(id));
    }

    /** All accounts that match the filter, in the order they were added. */
    public List<Account> findAll(Predicate<Account> filter) {
        return accounts.values().stream().filter(filter).toList();
    }
}
