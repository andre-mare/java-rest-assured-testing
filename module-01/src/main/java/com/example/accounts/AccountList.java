package com.example.accounts;

import java.util.List;

/** The body of {@code GET /api/v1/accounts}: the number of accounts and the accounts themselves. */
public record AccountList(int count, List<Account> accounts) {

    public AccountList {
        accounts = List.copyOf(accounts);
    }

    public static AccountList of(List<Account> accounts) {
        return new AccountList(accounts.size(), accounts);
    }
}
