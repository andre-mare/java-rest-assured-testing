package com.example.module_09.section_04;

import static com.example.module_09.support.PaymentsClient.createPayment;
import static com.example.module_09.support.PaymentsClient.spec;
import static com.example.module_09.support.PaymentsClient.validPayment;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.example.lifecycle.PaymentLifecycleApi;

import io.restassured.response.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Module 9.4 – HTTP semantics that payment APIs depend on and that are easy to get wrong.
 */
class HttpSemanticsTest {

    private static PaymentLifecycleApi api;

    @BeforeAll
    static void startApi() {
        api = PaymentLifecycleApi.start();
    }

    @AfterAll
    static void stopApi() {
        api.close();
    }

    /**
     * Idempotency keys (IETF draft "The Idempotency-Key HTTP Header Field"): a client that didn't get an answer can
     * safely send the same POST again, and the money moves only once.
     */
    @Nested
    class IdempotencyKeys {

        @Test
        void theSameRequestTwiceCreatesOnePayment() {
            String key = UUID.randomUUID().toString();
            Map<String, Object> body = validPayment("75.00");

            String first = given(spec(api)).header("Idempotency-Key", key).body(body)
                .when().post("/payments")
                .then().statusCode(201).header("Idempotent-Replayed", nullValue())
                .extract().path("paymentId");

            given(spec(api)).header("Idempotency-Key", key).body(body)
                .when().post("/payments")
                .then()
                .statusCode(201)
                .header("Idempotent-Replayed", "true")
                .body("paymentId", equalTo(first));                  // the same payment, not a second one
        }

        @Test
        void reusingAKeyForAnotherRequestIsAnError() {
            String key = UUID.randomUUID().toString();
            given(spec(api)).header("Idempotency-Key", key).body(validPayment("75.00"))
                .when().post("/payments").then().statusCode(201);

            given(spec(api)).header("Idempotency-Key", key).body(validPayment("76.00"))
                .when().post("/payments").then().statusCode(422);
        }

        @Test
        void theKeyIsRequired() {
            given(spec(api)).body(validPayment("75.00")).when().post("/payments").then().statusCode(400);
        }
    }

    /** ETags: conditional reads save bandwidth; conditional writes stop lost updates (RFC 9110 section 13). */
    @Nested
    class ETags {

        @Test
        void anUnchangedResourceIsNotSentAgain() {
            String id = createPayment(api, "20.00");
            String etag = given(spec(api)).when().get("/payments/{id}", id)
                .then().statusCode(200).extract().header("ETag");

            given(spec(api))
                .header("If-None-Match", etag)
            .when()
                .get("/payments/{id}", id)
            .then()
                .statusCode(304)                                      // Not Modified
                .body(emptyString());
        }

        @Test
        void anUpdateNeedsIfMatch() {
            String id = createPayment(api, "20.00");

            given(spec(api)).contentType("application/merge-patch+json").body(Map.of("reference", "Rent"))
                .when().patch("/payments/{id}", id)
                .then().statusCode(428);                              // Precondition Required
        }

        @Test
        void aStaleETagLosesTheRace() {
            String id = createPayment(api, "20.00");
            String etag = given(spec(api)).when().get("/payments/{id}", id).then().extract().header("ETag");

            String newEtag = given(spec(api))                         // the first writer wins ...
                .header("If-Match", etag)
                .contentType("application/merge-patch+json").body(Map.of("reference", "First"))
                .when().patch("/payments/{id}", id)
                .then().statusCode(200).header("ETag", not(equalTo(etag)))
                .extract().header("ETag");

            given(spec(api))                                          // ... the second, with the old ETag, gets 412
                .header("If-Match", etag)
                .contentType("application/merge-patch+json").body(Map.of("reference", "Second"))
                .when().patch("/payments/{id}", id)
                .then().statusCode(412);

            given(spec(api)).when().get("/payments/{id}", id)
                .then().header("ETag", newEtag).body("reference", equalTo("First"));
        }
    }

    /** Cursor pagination with a {@code Link: <..>; rel="next"} header (RFC 8288). */
    @Nested
    class Pagination {

        private static final Pattern NEXT = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");

        @Test
        void followNextLinksUntilTheLastPage() {
            List<String> ids = new ArrayList<>();
            String next = PaymentLifecycleApi.BASE_PATH + "/payments?limit=2";
            int pages = 0;
            while (next != null) {
                Response page = given(spec(api)).basePath("").when().get(next).then().statusCode(200).extract().response();
                ids.addAll(page.path("items.paymentId"));
                next = nextLink(page.header("Link"));
                pages++;
            }

            assertThat(ids).startsWith("pay-1", "pay-2", "pay-3", "pay-4", "pay-5").doesNotHaveDuplicates();
            assertThat(pages).isEqualTo((ids.size() + 1) / 2);
        }

        @Test
        void theLastPageHasNoNextLink() {
            given(spec(api))
                .queryParam("limit", 100)
            .when()
                .get("/payments")
            .then()
                .header("Link", nullValue());
        }

        private static String nextLink(String linkHeader) {
            if (linkHeader == null) {
                return null;
            }
            Matcher matcher = NEXT.matcher(linkHeader);
            return matcher.find() ? matcher.group(1) : null;
        }
    }
}
