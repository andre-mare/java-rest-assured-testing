package com.example.module_06.support;

/** Request bodies for the payment initiation API. */
public final class SamplePayments {

    private SamplePayments() {
    }

    public static String domesticPayment(String instructionId, String amount) {
        return """
            {
              "instructionId": "%s",
              "debtor": { "iban": "NL91ABNA0417164300", "name": "Anna de Vries" },
              "creditor": { "iban": "NL69INGB0123456789", "name": "Jan Jansen" },
              "amount": { "value": "%s", "currency": "EUR" },
              "remittanceInformation": "Invoice %s"
            }
            """.formatted(instructionId, amount, instructionId);
    }
}
