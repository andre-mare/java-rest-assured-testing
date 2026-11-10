package com.example.payments;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A saved payee, created from an HTML-style form post. */
@JsonInclude(JsonInclude.Include.NON_NULL)   // a missing reference or nickname is left out, not sent as null
public record Beneficiary(String id, String name, String iban, String nickname) {
}
