package com.example.accounts;

/** Whether an account can be used. A blocked account can still be read through the API. */
public enum AccountStatus {
    ACTIVE,
    BLOCKED,
    CLOSED
}
