# Mapping Responses to Java Records in REST Assured: as(..), TypeRef and Unknown Fields

Mapping Responses to Java Records in REST Assured: Turn response bodies into Java records with as(..), send a record and get one back, map generic types such as Page<Customer> and List<Account> with TypeRef, see why unknown fields don't break a test, and assert on records with AssertJ.

A path per field works for a few values. A customer has five fields, an account seven, and a page of customers holds several of each. Reading them one path at a time makes long tests with string paths everywhere. Java records offer a better way: describe the JSON once, as a record, and let REST Assured turn the body into it. The compiler then checks every field name you use, and your IDE completes them.

This post follows [Section 3 of Module 4](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-04) of the REST Assured course. It maps single objects, generic pages and JSON arrays to records, and shows what happens with fields the record doesn't have. The examples are in [`ObjectMappingTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/src/test/java/com/example/module_04/section_03/ObjectMappingTest.java).

## How Does as(..) Work?

`extract().as(type)` reads the response body with an object mapper and returns an instance of the type:

1. **REST Assured picks the mapper** from the classpath and the content type. This module has Jackson 3, so JSON is read by Jackson 3.
2. **Jackson matches JSON fields to record components** by name, and converts each value to the component's type, such as an ISO-8601 string to an `Instant`.
3. **A generic type** such as `List<Account>` needs a `TypeRef`, because Java erases the type argument at runtime.

[Section 4](module-04-section-04-rest-assured-object-mapper-configuration.md) shows how to choose and configure the mapper.

## The Tests' Own Records

The tests describe the API's JSON with their own records, in [`Models`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/src/test/java/com/example/module_04/support/Models.java):

```java
public record NewCustomer(String name, String email, String segment) {
}

public record Customer(String id, String name, String email, String segment, Instant createdAt) {
}

/** One page of a list. Generic, so {@code Page<Customer>} needs a {@code TypeRef} to be mapped. */
public record Page<T>(List<T> items, int page, int size, int totalItems, int totalPages) {
}

public record Link(String href) {
}

/** {@code _links} isn't a valid Java name, so the record component gets a Jackson annotation. */
public record Consent(String consentId, String customerId, List<String> permissions, String status,
                      @JsonProperty("_links") Map<String, Link> links) {
}

/** Deliberately without {@code productCode} and {@code balance}, which the API also sends. */
public record Account(String accountId, String iban, String currency, String type, String nickname) {
}
```

These records are written from the API's contract, not copied from its code. If the tests reused the API's own classes, a renamed field would change both sides at once, and no test would notice. With separate records, a change in the API shows up as a failing test.

## Mapping to Records

### Step 1: A Record Out

```java
@Test
void aRecord() {
    Customer customer = consentsApi()
    .when()
        .get("/customers/{id}", "cust-3")
    .then()
        .statusCode(200)
        .extract().as(Customer.class);

    assertThat(customer.name()).isEqualTo("Bakkerij Bart B.V.");
    assertThat(customer.segment()).isEqualTo("BUSINESS");
    assertThat(customer.createdAt()).isBefore(Instant.now());      // an ISO-8601 string became an Instant
}
```

`createdAt` arrives as `"2026-11-10T21:22:12.081316Z"`, and Jackson 3 turns it into an `Instant`. Jackson 3 supports `java.time` types without an extra module, so a test can compare dates instead of strings.

### Step 2: A Record In and a Record Out

```java
@Test
void aRecordInAndARecordOut() {
    NewCustomer request = new NewCustomer("Sanne Smit", "sanne@example.nl", "RETAIL");

    Customer created = consentsApi()
        .contentType(ContentType.JSON)
        .body(request)                                              // serialized with Jackson 3
    .when()
        .post("/customers")
    .then()
        .statusCode(201)
        .extract().as(Customer.class);

    assertThat(created)
        .returns("Sanne Smit", Customer::name)
        .returns("sanne@example.nl", Customer::email);
    assertThat(created.id()).startsWith("cust-");
}
```

The same mapper writes the request body, as in [Module 2](module-02-section-04-rest-assured-request-bodies.md), and reads the response. AssertJ's `returns(expected, getter)` checks several components of one record in a single readable statement.

## Generic Types

### Step 3: A Generic Page Needs a TypeRef

```java
@Test
void aGenericPageNeedsATypeRef() {
    Page<Customer> page = consentsApi()
        .queryParam("size", 2)
    .when()
        .get("/customers")
    .then()
        .extract().as(new TypeRef<Page<Customer>>() {});

    assertThat(page.totalItems()).isGreaterThanOrEqualTo(5);
    assertThat(page.items()).hasSize(2).allSatisfy(customer -> assertThat(customer.id()).startsWith("cust-"));
}
```

`Page<Customer>.class` doesn't exist in Java. At runtime there is only `Page`, and Jackson can't know that `items` holds customers. `new TypeRef<Page<Customer>>() {}` is an anonymous subclass, and a subclass keeps its type argument, so Jackson can read it. Note the `{}`: without it, there is no subclass.

`allSatisfy(..)` runs the same assertions on every element, and reports each one that fails.

### Step 4: A JSON Array as a List

`GET /api/v1/accounts` needs an authorised consent, created by a helper at the end of the class. It returns a JSON array:

```json
[
  {"accountId": "acc-1001", "iban": "NL11BANK0000000001", "currency": "EUR", "type": "CURRENT",
   "nickname": "Main account", "productCode": "CUR-01", "balance": "15000.00"},
  {"accountId": "acc-1002", "iban": "NL12BANK0000000002", "currency": "EUR", "type": "SAVINGS",
   "nickname": "Rainy day", "productCode": "SAV-01", "balance": "1250.50"}
]
```

```java
@Test
void aJsonArrayAsAList() {
    String consentId = authorisedConsent("cust-1", List.of("ReadAccountsBasic"));

    List<Account> accounts = consentsApi()
        .header("Consent-ID", consentId)
    .when()
        .get("/accounts")
    .then()
        .statusCode(200)
        .extract().as(new TypeRef<List<Account>>() {});

    assertThat(accounts)
        .extracting(Account::type)
        .containsExactly("CURRENT", "SAVINGS");
    assertThat(accounts).allSatisfy(account -> assertThat(account.iban()).matches("NL\\d{2}BANK\\d{10}"));
}
```

`extracting(Account::type)` turns the list of accounts into a list of types, so one assertion checks them all, in order.

> **The code above, ready to run**
>
> All five tests in [`ObjectMappingTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/src/test/java/com/example/module_04/section_03/ObjectMappingTest.java) pass with `mvn test -pl module-04 -Dtest=ObjectMappingTest`. Rename a component of `Customer` and watch the tests that use it fail.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 4 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/README.md)

## Unknown Fields

Each account has `productCode` and `balance`, which the `Account` record leaves out on purpose:

```java
@Test
void unknownFieldsAreIgnoredByDefault() {
    String consentId = authorisedConsent("cust-2", List.of("ReadAccountsBasic"));

    List<Account> accounts = consentsApi()
        .header("Consent-ID", consentId)
        .when().get("/accounts")
        .then().extract().as(new TypeRef<>() {});                   // the type argument can be inferred

    assertThat(accounts).isNotEmpty();
}
```

**Jackson 3 ignores unknown properties by default.** Jackson 2 failed on them. So when the API adds a field, tests that don't care about it keep passing. That is usually what you want from a test suite. When you do want to notice new fields, [Section 4](module-04-section-04-rest-assured-object-mapper-configuration.md) makes the mapper strict.

`new TypeRef<>() {}` works too: Java infers the type argument from the variable.

## Fields That Aren't Valid Java Names

The consent JSON has a `_links` field with hypermedia links:

```json
{
  "consentId": "consent-1",
  "status": "AWAITING_AUTHORISATION",
  "_links": {
    "authorise": {"href": "/api/v1/consents/consent-1/authorisation"},
    "self": {"href": "/api/v1/consents/consent-1"}
  }
}
```

The `Consent` record names the component `links` and maps it with `@JsonProperty("_links")`. `Map<String, Link>` holds any number of links by name, so `consent.links().get("authorise").href()` reads the link to follow. [Section 5](module-04-section-05-rest-assured-chaining-requests.md) follows it.

## Common Pitfalls

1. **Mapping a generic type without a `TypeRef`.** `as(Page.class)` compiles, but the items are `LinkedHashMap`s, and the first `Customer` you read from the list throws `ClassCastException`.
2. **Forgetting the `{}` in a `TypeRef`.** `new TypeRef<List<Account>>()` doesn't compile, because `TypeRef` is abstract. The braces create the subclass that keeps the type.
3. **A component name that doesn't match the JSON.** Unknown fields are ignored, so a record component without a matching field silently stays `null`. Without `@JsonProperty("_links")`, `links()` is `null`, and the test fails later with a `NullPointerException`.
4. **Reusing the API's own classes in tests.** A change on both sides goes unnoticed. Write the tests' records from the contract.
5. **Mapping when you only need one value.** `as(Customer.class).name()` reads the whole body for one field. A path, or `body("name", ..)`, is simpler.

## Wrapping Up

`extract().as(Type.class)` maps a response body to a record, with the object mapper on the classpath, Jackson 3 here. Generic types such as `Page<Customer>` and `List<Account>` need a `TypeRef`, an anonymous subclass that keeps the type argument. Jackson 3 ignores fields the record doesn't have, so new API fields don't break tests, but a misnamed component stays `null` without a warning. Write the records from the contract, and assert on them with AssertJ's `returns`, `extracting` and `allSatisfy`.
