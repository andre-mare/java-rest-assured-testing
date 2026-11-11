package com.example.accounts;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

/** An in-memory store, so the module needs no database. */
@Service
public class AccountService {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger(1003);

    public AccountService() {
        save(new Account("acc-1001", "NL91ABNA0417164300", "Anna de Vries", "EUR", "1250.50"));
        save(new Account("acc-1002", "NL69INGB0123456789", "Jan Jansen", "EUR", "15000.00"));
    }

    public List<Account> findAll() {
        return accounts.values().stream().sorted(java.util.Comparator.comparing(Account::id)).toList();
    }

    public Optional<Account> find(String id) {
        return Optional.ofNullable(accounts.get(id));
    }

    public Account open(NewAccount request) {
        return save(new Account("acc-" + ids.getAndIncrement(), request.iban(), request.owner(), request.currency(), "0.00"));
    }

    private Account save(Account account) {
        accounts.put(account.id(), account);
        return account;
    }
}
