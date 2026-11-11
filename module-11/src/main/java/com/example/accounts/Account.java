package com.example.accounts;

/** An account. The balance is a string with two decimals, so no precision is lost in JSON. */
public record Account(String id, String iban, String owner, String currency, String balance) {
}
