# JsonPath and XmlPath in REST Assured: Typed Getters, Maps, Objects and JSON From Anywhere

JsonPath and XmlPath in REST Assured: Use JsonPath and XmlPath as objects, covering typed getters such as getInt and getList, maps and mapped objects, JSON that didn't come from an HTTP call, and XmlPath on an ISO 20022 payment status report.

`extract().path(..)` is fine for one value. For ten values from the same page of customers, you want something you can query again and again. And not every piece of JSON comes from a REST Assured request: a webhook payload, a message from a queue or a file in `src/test/resources` needs checking too. Do you need another library for those? You don't. The path engine behind `body(..)` is available on its own.

This post follows [Section 2 of Module 4](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-04) of the REST Assured course. It shows `JsonPath` and `XmlPath` as objects you can keep and query, with getters that convert values to the type you ask for. The examples are in [`JsonPathAndXmlPathTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/src/test/java/com/example/module_04/section_02/JsonPathAndXmlPathTest.java).

## What are JsonPath and XmlPath?

`io.restassured.path.json.JsonPath` and `io.restassured.path.xml.XmlPath` are the classes that evaluate GPath. You get one in two ways:

1. **From a response**: `extract().jsonPath()` or `extract().xmlPath()`.
2. **From any text**: `JsonPath.from(json)` or `XmlPath.from(xml)`. A string, a `File` or an `InputStream` works.

Each has typed getters:

| Getter | Returns |
| --- | --- |
| `getString(path)` | The value as a `String` |
| `getInt(path)`, `getLong(path)`, `getDouble(path)` | The value converted to that number type |
| `getBoolean(path)` | The value as a `boolean` |
| `getList(path, type)` | A list, with each element converted or mapped to `type` |
| `getMap(path)` | An object as a `Map` |
| `getObject(path, type)` | An object mapped to a Java type with the object mapper |

Unlike `path(..)`, the getters convert. `getLong("totalItems")` works even though REST Assured read the number as an `Integer`, and `getString("totalItems")` returns `"5"`.

## The Data Under Test

The tests read the first page of customers, with three per page:

```java
private static JsonPath firstPageOfCustomers() {
    return given().baseUri(api.baseUri()).port(api.port()).basePath(ConsentsApi.BASE_PATH)
        .queryParam("size", 3)
    .when()
        .get("/customers")
    .then()
        .statusCode(200)
        .extract().jsonPath();
}
```

The page holds `cust-1` Anna de Vries and `cust-2` Jan Jansen, both `RETAIL`, and `cust-3` Bakkerij Bart B.V., a `BUSINESS` customer. There are five customers, so `totalItems` is `5` and `totalPages` is `2`.

The helper validates the status before it extracts, as [Section 1](module-04-section-01-rest-assured-extracting-values.md) recommends, then returns the `JsonPath` for the test to query.

## Reading a Response With JsonPath

### Step 1: Typed Getters

```java
@Test
void typedGetters() {
    JsonPath page = firstPageOfCustomers();

    assertThat(page.getInt("totalItems")).isEqualTo(5);
    assertThat(page.getInt("totalPages")).isEqualTo(2);
    assertThat(page.getString("items[0].name")).isEqualTo("Anna de Vries");
    assertThat(page.getList("items.email", String.class)).hasSize(3).allMatch(email -> email.contains("@"));
}
```

The assertions use [AssertJ](https://assertj.github.io/doc/), which reads well on plain Java values. `getList("items.email", String.class)` returns a `List<String>`, so AssertJ's list assertions apply to it directly.

### Step 2: Maps and Objects

```java
@Test
void mapsAndObjects() {
    JsonPath page = firstPageOfCustomers();

    Map<String, Object> first = page.getMap("items[0]");
    assertThat(first).containsEntry("segment", "RETAIL");

    Customer third = page.getObject("items[2]", Customer.class);   // mapped with Jackson 3
    assertThat(third.name()).isEqualTo("Bakkerij Bart B.V.");
    assertThat(third.createdAt()).isNotNull();

    List<Customer> all = page.getList("items", Customer.class);
    assertThat(all).extracting(Customer::segment).containsExactly("RETAIL", "RETAIL", "BUSINESS");
}
```

- **`getMap(..)`** returns an object as a map, with the field names as keys.
- **`getObject(path, Customer.class)`** maps part of the body to a record. It uses the same object mapper as `as(..)`, Jackson 3 here, so the ISO-8601 `createdAt` string becomes an `Instant`.
- **`getList("items", Customer.class)`** maps every element. Without the type, the elements would be maps.

`Customer` is a record from the tests' [`Models`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/src/test/java/com/example/module_04/support/Models.java). [Section 3](module-04-section-03-rest-assured-mapping-to-java-records.md) covers mapping whole bodies to records.

> **The code above, ready to run**
>
> All four tests in [`JsonPathAndXmlPathTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/src/test/java/com/example/module_04/section_02/JsonPathAndXmlPathTest.java) pass with `mvn test -pl module-04 -Dtest=JsonPathAndXmlPathTest`. The last two send no HTTP request at all.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 4 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-04/README.md)

## JSON and XML From Anywhere

### JSON That Didn't Come From HTTP

```java
@Test
void jsonThatDidntComeFromHttp() {
    String webhook = """
            {"event": "consent.revoked", "data": {"consentId": "consent-7", "reason": "CUSTOMER_REQUEST"}}
            """;

    JsonPath json = JsonPath.from(webhook);

    assertThat(json.getString("event")).isEqualTo("consent.revoked");
    assertThat(json.getString("data.reason")).isEqualTo("CUSTOMER_REQUEST");
}
```

`JsonPath.from(..)` reads any JSON text with the same GPath you use in `body(..)`. That is useful for a webhook your service sends, a message it puts on a queue, or an expected response kept in a file.

### XmlPath Works the Same Way

```java
@Test
void xmlPathWorksTheSameWay() {
    String xml = """
            <Document><CstmrPmtStsRpt><OrgnlGrpInfAndSts>
              <OrgnlMsgId>MSG-42</OrgnlMsgId><GrpSts>ACCP</GrpSts>
            </OrgnlGrpInfAndSts></CstmrPmtStsRpt></Document>
            """;

    XmlPath status = XmlPath.from(xml).setRootPath("Document.CstmrPmtStsRpt.OrgnlGrpInfAndSts");

    assertThat(status.getString("OrgnlMsgId")).isEqualTo("MSG-42");
    assertThat(status.getString("GrpSts")).isEqualTo("ACCP");      // ISO 20022 code for "accepted"
}
```

The XML is a simplified ISO 20022 `pain.002`, the payment status report a bank sends back after a payment file. `setRootPath(..)` works like `rootPath(..)` in [Module 3](module-03-section-02-rest-assured-root-paths-and-path-arguments.md), and the XML rules from [Module 3, Section 7](module-03-section-07-rest-assured-xml-and-html.md) apply: paths start with the root element, and values are text.

## JsonPath or body(..)?

1. **`body(..)`** when you check a response in the chain. It reports every failure with its path.
2. **`extract().jsonPath()`** when you need many values from one response, or values for later requests.
3. **`JsonPath.from(..)`** when the JSON didn't come from a REST Assured request.

## Common Pitfalls

1. **A typed getter for a missing number.** `getInt("missing")` throws a `NullPointerException`, because there is no `int` for `null`. Check that the field exists first, or use `getString(..)`, which returns `null`.
2. **`getList(path)` without a type.** The elements are maps, not records. Pass the type: `getList("items", Customer.class)`.
3. **Invalid JSON.** `JsonPath.from("{not json")` throws `JsonPathException: Failed to parse the JSON document` when you first read a value, not when you create it.
4. **Mixing up `path(..)` and the getters.** `path(..)` returns the value as it was read, so a `Long` variable fails on an `Integer`. `getLong(..)` converts it.
5. **Rebuilding JsonPath for every value.** Extract it once, keep it in a variable, and query it as often as you like.

## Wrapping Up

`JsonPath` and `XmlPath` are the path engines behind `body(..)`, available as objects. Get one from a response with `extract().jsonPath()`, or from any text with `JsonPath.from(..)` and `XmlPath.from(..)`. Their typed getters convert values to the type you ask for, `getMap(..)` returns objects as maps, and `getObject(..)` and `getList(path, type)` map them to records. Use them for many values from one response, and for JSON and XML that didn't come over HTTP.
