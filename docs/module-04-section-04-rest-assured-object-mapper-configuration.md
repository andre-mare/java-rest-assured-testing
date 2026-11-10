# Configuring the Object Mapper in REST Assured: Jackson 3, ObjectMapperType and a Strict JsonMapper

Configuring the Object Mapper in REST Assured: Choose the object mapper explicitly with ObjectMapperType, see the lenient Jackson 3 default, and plug in your own strict JsonMapper with a Jackson3ObjectMapperFactory, the 6.3 way.

[Section 3](module-04-section-03-rest-assured-mapping-to-java-records.md) mapped responses to records without any configuration. REST Assured found Jackson 3 on the classpath and used it with its defaults. Often that is all you need. But what if the classpath also has Jackson 2 or Gson, because another library brought it in? And what if you want a test to fail when the API sends a field your record doesn't know about, because that is exactly the change you want to catch?

This post follows [Section 4 of Module 4](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-04) of the REST Assured course. It shows how to choose the object mapper, and how to give REST Assured a Jackson 3 `JsonMapper` that you configured yourself. The examples are in [`ObjectMapperConfigurationTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/src/test/java/com/example/module_04/section_04/ObjectMapperConfigurationTest.java).

## Which Mapper Does REST Assured Use?

REST Assured 6 supports several object mappers:

1. **Jackson 3**, in the package `tools.jackson`.
2. **Jackson 2**, in the package `com.fasterxml.jackson`.
3. **Gson**, **JSON-B** and, for XML, **JAXB**.

Jackson 3 and Jackson 2 can be on the classpath side by side. When several mappers are present, REST Assured prefers Jackson 3. There are two ways to change that:

- **`ObjectMapperType`** picks a mapper by name for one call, such as `as(Customer.class, ObjectMapperType.JACKSON_3)`.
- **`ObjectMapperConfig`** sets the mapper, or a factory that builds it, for every request that uses the config.

## The Data Under Test

The tests read customer `cust-1`:

```json
{"id":"cust-1","name":"Anna de Vries","email":"anna@example.nl","segment":"RETAIL","createdAt":"2026-11-10T21:22:12.081316Z"}
```

Two of them map it to a record that has every field except `email`:

```java
/** Like the record the API should return, but without "email": the strict mapper will notice. */
record CustomerWithoutEmail(String id, String name, String segment, java.time.Instant createdAt) {
}
```

That stands for a common situation: the API has added a field that the tests' records don't know about yet.

## Choosing and Configuring the Mapper

### Step 1: Choose the Mapper Explicitly

```java
@Test
void chooseTheMapperExplicitly() {
    Customer customer = consentsApi()
        .when().get("/customers/{id}", "cust-1")
        .then().extract().as(Customer.class, ObjectMapperType.JACKSON_3);

    assertThat(customer.name()).isEqualTo("Anna de Vries");
}
```

Naming the mapper makes the test independent of the classpath. If a new dependency brings in another mapper, this call still uses Jackson 3. `ObjectMapperType` also has `JACKSON_2`, `GSON`, `JSONB` and `JAXB`, among others. The mapper you name must be on the classpath.

### Step 2: The Lenient Default

```java
@Test
void aLenientMapperIgnoresFieldsTheRecordLacks() {
    CustomerWithoutEmail customer = consentsApi()
        .when().get("/customers/{id}", "cust-1")
        .then().extract().as(CustomerWithoutEmail.class);

    assertThat(customer.name()).isEqualTo("Anna de Vries");
}
```

The body has an `email` field, and the record doesn't. Jackson 3 ignores it, so the mapping succeeds. This is Jackson 3's default, and the opposite of Jackson 2's.

### Step 3: A Strict Mapper

To fail on unknown fields, build a `JsonMapper` with `FAIL_ON_UNKNOWN_PROPERTIES` and give it to REST Assured:

```java
@Test
void aStrictMapperFailsOnFieldsTheRecordLacks() {
    JsonMapper strict = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    Jackson3ObjectMapperFactory factory = (type, charset) -> strict;
    RestAssuredConfig strictConfig = config().objectMapperConfig(objectMapperConfig().jackson3ObjectMapperFactory(factory));

    assertThatThrownBy(() -> consentsApi()
            .config(strictConfig)
            .when().get("/customers/{id}", "cust-1")
            .then().extract().as(CustomerWithoutEmail.class))
        .hasStackTraceContaining("email");                           // the unknown property is named
}
```

1. **`JsonMapper.builder()`** builds a Jackson 3 mapper. Jackson 3 mappers are immutable, so every setting goes through the builder.
2. **`Jackson3ObjectMapperFactory`** tells REST Assured how to get a mapper. It receives the type being mapped and the charset, and here always returns the same strict mapper.
3. **`objectMapperConfig().jackson3ObjectMapperFactory(..)`** puts the factory in a `RestAssuredConfig`, which the request uses through `config(..)`.

The mapping now fails with a Jackson exception that names the field and lists the ones the record knows:

```text
tools.jackson.databind.exc.UnrecognizedPropertyException: Unrecognized property "email"
(class CustomerWithoutEmail), not marked as ignorable (4 known properties: "id", "name", "segment", "createdAt")
```

A strict mapper is useful in a contract test that should notice every change in the API. For everyday tests, the lenient default is usually better, because it keeps tests focused on the fields they check.

> **The code above, ready to run**
>
> All three tests in [`ObjectMapperConfigurationTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/src/test/java/com/example/module_04/section_04/ObjectMapperConfigurationTest.java) pass with `mvn test -pl module-04 -Dtest=ObjectMapperConfigurationTest`. Remove `assertThatThrownBy` from the last one to see the full exception.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 4 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/README.md)

## The Factory Interface Since REST Assured 6.3

REST Assured 6.3 moved the mapper factory interfaces to `io.restassured.common.mapper.factory`. The old `Jackson3ObjectMapperFactory`, in `io.restassured.path.json.mapper.factory`, is deprecated. It now extends the new one, and `jackson3ObjectMapperFactory(..)` has an overload for each.

That has a consequence for lambdas. Pass a bare lambda, and Java picks the most specific overload, which is the one that takes the **deprecated** interface:

```java
objectMapperConfig().jackson3ObjectMapperFactory((type, charset) -> strict);   // binds to the deprecated interface
```

It compiles and works, but it relies on an interface that a later version will remove. Assigning the lambda to a variable of the new type makes REST Assured use the new interface:

```java
import io.restassured.common.mapper.factory.Jackson3ObjectMapperFactory;

Jackson3ObjectMapperFactory factory = (type, charset) -> strict;
objectMapperConfig().jackson3ObjectMapperFactory(factory);
```

Check the import: both interfaces have the same simple name, and an IDE may suggest the deprecated one.

## Sharing the Configuration

This test builds the config inline so you can see every step. In a real suite, you build it once and share it, for example as a constant, in a request specification or as a static default on `RestAssured`. Module 5 covers specifications and `RestAssuredConfig` in depth.

## Common Pitfalls

1. **Relying on whichever mapper the classpath has.** A new dependency can bring in another mapper. Name it with `ObjectMapperType` where it matters, or set it in the config.
2. **Expecting Jackson 2's strictness from Jackson 3.** Jackson 3 ignores unknown fields by default. Turn on `FAIL_ON_UNKNOWN_PROPERTIES` when you want to know.
3. **Importing the deprecated factory interface.** `io.restassured.path.json.mapper.factory.Jackson3ObjectMapperFactory` still works, but is on its way out. Import the one from `io.restassured.common.mapper.factory`.
4. **A strict mapper for every test.** Every new API field then breaks tests that don't care about it. Keep strictness for contract tests.
5. **Configuring Jackson 2 for a Jackson 3 mapper.** The two have different packages and different factory methods: `jackson2ObjectMapperFactory(..)` and `jackson3ObjectMapperFactory(..)`. Use the one that matches the mapper REST Assured really uses.

## Wrapping Up

REST Assured 6 supports Jackson 3, Jackson 2, Gson, JSON-B and JAXB, and prefers Jackson 3. `ObjectMapperType` names a mapper for one call, and `ObjectMapperConfig` sets one for every request that uses the config. Jackson 3 ignores unknown fields by default. A `JsonMapper` with `FAIL_ON_UNKNOWN_PROPERTIES`, passed through a `Jackson3ObjectMapperFactory`, makes mapping strict. Since 6.3, assign that factory to a variable of the new interface type, so the code doesn't depend on the deprecated one.
