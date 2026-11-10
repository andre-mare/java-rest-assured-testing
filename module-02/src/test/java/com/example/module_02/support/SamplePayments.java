package com.example.module_02.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** Valid payment bodies, so tests only spell out what they change. */
public final class SamplePayments {

    public static final String DEBTOR_IBAN = "NL91ABNA0417164300";
    public static final String CREDITOR_IBAN = "NL69INGB0123456789";

    private SamplePayments() {
    }

    /** A valid payment as a mutable map; change or remove entries to make it invalid. */
    public static Map<String, Object> validPayment(String amount) {
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("debtorIban", DEBTOR_IBAN);
        payment.put("creditorIban", CREDITOR_IBAN);
        payment.put("creditorName", "Jan Jansen");
        payment.put("amount", amount);
        payment.put("currency", "EUR");
        payment.put("reference", "Invoice 2026-001");
        return payment;
    }

    /** The same payment as a JSON string. */
    public static String validPaymentJson(String amount) {
        return """
            {
              "debtorIban": "%s",
              "creditorIban": "%s",
              "creditorName": "Jan Jansen",
              "amount": "%s",
              "currency": "EUR"
            }
            """.formatted(DEBTOR_IBAN, CREDITOR_IBAN, amount);
    }
}
