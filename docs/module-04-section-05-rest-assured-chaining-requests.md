# Chaining Requests in REST Assured: Location Headers, Hypermedia Links and a Consent Flow

Chaining Requests in REST Assured: Build a flow of requests in which each step uses what the previous one returned, covering following a Location header, the open banking consent flow from customer to balances, and the negative cases of an unauthorised consent and a missing permission.

Real API usage is rarely one request. To read a customer's balances in an open banking API, a client creates a consent, has it authorised, reads the accounts the consent covers, and only then asks for a balance. Each step needs something from the step before: an id, a link, a header value. A test of that flow is a chain of requests, and everything from the earlier sections of this module comes together in it.

This post follows [Section 5 of Module 4](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-04) of the REST Assured course. It follows a `Location` header, runs the whole consent flow, and tests what a consent must not allow. The examples are in [`ChainingRequestsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/src/test/java/com/example/module_04/section_05/ChainingRequestsTest.java).

## What is a Chain of Requests?

A chain is a test in which each request uses a value from an earlier response. Three rules keep chains reliable:

1. **Validate every step before you use it.** A failed step should fail the test at that step, with a clear message, as [Section 1](module-04-section-01-rest-assured-extracting-values.md) explains.
2. **Follow the links the API gives you.** A `Location` header or a `_links` entry tells you where to go next. Building the URL yourself duplicates knowledge the API already has, and breaks when it changes.
3. **Create the data each test needs.** Every test starts from its own customer or consent, so tests don't depend on each other or on the order they run in.

## The Consent Flow

The Customers and Consents API is modelled on open banking account access consents (PSD2 AIS):

| Step | Request | Response |
| --- | --- | --- |
| 1. Create a customer | `POST /api/v1/customers` | `201` with `Location` |
| 2. Request a consent | `POST /api/v1/consents` with `customerId` and `permissions` | `201`, status `AWAITING_AUTHORISATION`, with `_links` |
| 3. Authorise it | `POST` to the `authorise` link | `200`, status `AUTHORISED` |
| 4. Read accounts | `GET /api/v1/accounts`, header `Consent-ID` | The customer's accounts, if the consent has `ReadAccountsBasic` |
| 5. Read balances | `GET /api/v1/accounts/{id}/balances`, header `Consent-ID` | The balances, if the consent has `ReadBalances` |

A new consent looks like this:

```json
{
  "consentId": "consent-1",
  "customerId": "cust-1",
  "permissions": ["ReadAccountsBasic", "ReadBalances"],
  "status": "AWAITING_AUTHORISATION",
  "expiresOn": "2027-02-08",
  "_links": {
    "authorise": {"href": "/api/v1/consents/consent-1/authorisation"},
    "self": {"href": "/api/v1/consents/consent-1"}
  }
}
```

The `Consent-ID` header is the key: without it, the accounts endpoints answer `400`. With a consent that isn't authorised, or lacks a permission, they answer `403`.

The tests map the JSON to the records from [Section 3](module-04-section-03-rest-assured-mapping-to-java-records.md): `NewCustomer`, `Customer`, `NewConsent`, `Consent` and `Account`.

## Following Links

### Step 1: Follow the Location Header

```java
@Test
void createThenFollowTheLocationHeader() {
    String location = consentsApi()
        .contentType(ContentType.JSON)
        .body(new NewCustomer("Lotte Laan", "lotte@example.nl", "RETAIL"))
    .when()
        .post("/customers")
    .then()
        .statusCode(201)
        .extract().header("Location");

    // The Location is an absolute path, so it is resolved against the server, not the base path.
    given().baseUri(api.baseUri()).port(api.port())
    .when()
        .get(location)
    .then()
        .statusCode(200)
        .body("name", equalTo("Lotte Laan"));
}
```

The `Location` is `/api/v1/customers/cust-6`, a path that already starts with `/api/v1`. The second request is built without `basePath(..)`, so the path is used as it is. With the usual `consentsApi()` helper, REST Assured would put the base path in front of it and request `/api/v1/api/v1/customers/cust-6`, which is a `404`.

## The Full Flow

### Step 2: From Customer to Balances

```java
@Test
void theFullConsentFlow() {
    Customer customer = consentsApi()
        .contentType(ContentType.JSON)
        .body(new NewCustomer("Daan Dekker", "daan@example.nl", "RETAIL"))
        .when().post("/customers")
        .then().statusCode(201)
        .extract().as(Customer.class);

    Consent consent = consentsApi()
        .contentType(ContentType.JSON)
        .body(new NewConsent(customer.id(), List.of("ReadAccountsBasic", "ReadBalances")))
        .when().post("/consents")
        .then().statusCode(201)
        .body("status", equalTo("AWAITING_AUTHORISATION"))
        .extract().as(Consent.class);

    given().baseUri(api.baseUri()).port(api.port())
        .when().post(consent.links().get("authorise").href())      // follow the link, don't build the URL
        .then().statusCode(200)
        .body("status", equalTo("AUTHORISED"));

    List<Account> accounts = consentsApi()
        .header("Consent-ID", consent.consentId())
        .when().get("/accounts")
        .then().statusCode(200)
        .extract().as(new TypeRef<List<Account>>() {});
    assertThat(accounts).hasSize(2);

    consentsApi()
        .header("Consent-ID", consent.consentId())
    .when()
        .get("/accounts/{id}/balances", accounts.getFirst().accountId())
    .then()
        .statusCode(200)
        .body("balances[0].type", equalTo("closingBooked"))
        .body("balances[0].amount.currency", equalTo("EUR"));
}
```

Each step follows the same pattern: send, validate, extract what the next step needs.

1. **The customer** is mapped to a record, and its `id` goes into the consent request.
2. **The consent** is checked for its starting status, then mapped, so its id and links are available.
3. **The authorisation** follows the `authorise` link from the consent. The test doesn't know the URL pattern, and doesn't need to.
4. **The accounts** are read with the consent id, and mapped with a `TypeRef`. A new retail customer has two accounts, current and savings.
5. **The balances** of the first account end the flow.

The balance response checks nested values: `{"accountId":"acc-1001","balances":[{"type":"closingBooked","amount":{"currency":"EUR","amount":"15000.00"}}]}`.

> **The code above, ready to run**
>
> All four tests in [`ChainingRequestsTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/src/test/java/com/example/module_04/section_05/ChainingRequestsTest.java) pass with `mvn test -pl module-04 -Dtest=ChainingRequestsTest`. Each creates its own data, so they pass in any order and on their own.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 4 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/README.md)

## What a Consent Must Not Allow

A flow test proves that the happy path works. The tests that prove what is refused matter just as much in a security-sensitive API.

### An Unauthorised Consent Opens Nothing

```java
@Test
void aConsentThatIsNotAuthorisedOpensNothing() {
    String consentId = consentsApi()
        .contentType(ContentType.JSON)
        .body(new NewConsent("cust-1", List.of("ReadAccountsBasic")))
        .when().post("/consents")
        .then().statusCode(201)
        .extract().path("consentId");

    consentsApi()
        .header("Consent-ID", consentId)
    .when()
        .get("/accounts")
    .then()
        .statusCode(403)
        .body("detail", containsString("not authorised"));
}
```

The consent exists, but nobody authorised it, so the API answers `403` with the detail `Consent consent-N is not authorised`. Only one value from the first response is needed, so `extract().path(..)` is enough. No record required.

### A Consent Only Grants Its Permissions

```java
@Test
void aConsentOnlyGrantsItsPermissions() {
    Consent consent = consentsApi()
        .contentType(ContentType.JSON)
        .body(new NewConsent("cust-1", List.of("ReadAccountsBasic")))   // no ReadBalances
        .when().post("/consents")
        .then().extract().as(Consent.class);
    consentsApi().when().post("/consents/{id}/authorisation", consent.consentId()).then().statusCode(200);

    consentsApi()
        .header("Consent-ID", consent.consentId())
    .when()
        .get("/accounts/{id}/balances", "acc-1001")
    .then()
        .statusCode(403)
        .body("detail", containsString("ReadBalances"));
}
```

The consent is authorised, but only for `ReadAccountsBasic`. Asking for balances gives `403` with the detail `Consent consent-N lacks the permission ReadBalances`. The check on `detail` proves the request was refused for the right reason, not for some other `403`.

This test builds the authorisation URL from the id instead of following the link. That is fine when the URL isn't what the test is about, but the full flow above shows the better habit.

## Common Pitfalls

1. **A `Location` resolved against the base path.** It already contains `/api/v1`, so the request goes to `/api/v1/api/v1/...` and gets a `404`. Send it without `basePath(..)`.
2. **Building URLs the API already gave you.** Follow `Location` and `_links`. The test then keeps working when the API changes its URL structure.
3. **Skipping validation in the middle of a chain.** If step 2 silently fails, step 4 fails with a confusing error. Check the status of every step.
4. **Tests that share a consent or a customer.** One test's authorisation changes another test's result. Create the data in each test, as these do.
5. **Checking only the status of a refusal.** A `403` can have many causes. Check the `detail` too, so the test fails if the API refuses for the wrong reason.

## Wrapping Up

A chain of requests sends, validates and extracts at every step, and passes what it extracted to the next request. Map responses to records when you need several values, and use `extract().path(..)` when you need one. Follow the `Location` headers and links the API returns, remembering that an absolute path must not get the base path in front of it. Create the data each test needs, and test the refusals as carefully as the happy path.
