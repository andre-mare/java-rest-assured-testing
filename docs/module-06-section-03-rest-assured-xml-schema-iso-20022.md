# XML Schema Validation in REST Assured: XSDs for ISO 20022 pain.001 and pain.002

XML Schema Validation in REST Assured: Validate ISO 20022 payment files and status reports against XSDs with matchesXsdInClasspath(..), check the test data a test sends, test an accepted and a rejected file, and handle the SAXParseException an invalid document throws.

Banks don't only speak JSON. Companies send salary runs and supplier payments to their bank as ISO 20022 XML files, and the bank answers with a status report. Both messages have an official XML Schema (XSD), and a file that doesn't match it is rejected before anyone reads it. A test suite for such an API needs to check two things: that the reports the API sends match their schema, and that the files the tests send are valid in the first place.

This post follows [Section 3 of Module 6](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-06) of the REST Assured course. It validates ISO 20022 messages in both directions with `matchesXsdInClasspath(..)`. The examples are in [`XmlSchemaTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/src/test/java/com/example/module_06/section_03/XmlSchemaTest.java).

## What is an XSD?

An XML Schema Definition describes the elements an XML document may contain, in which order, how often, with which attributes and with which types. REST Assured validates against one with:

1. **`matchesXsdInClasspath(path)`**, which loads the XSD from the classpath.
2. **`matchesXsd(xsd)`**, which takes it as a string, a `File` or an `InputStream`.

Both are static imports from `io.restassured.matcher.RestAssuredMatchers`. Like the JSON Schema matcher, they are Hamcrest matchers on a `String`, so they work on responses and on any other XML.

## The Messages Under Test

Two ISO 20022 message types are involved:

| Message | Name | Direction |
| --- | --- | --- |
| `pain.001` | Customer Credit Transfer Initiation | The company sends payments to the bank |
| `pain.002` | Customer Payment Status Report | The bank reports whether it accepted them |

`POST /pisp/v1/payment-files` takes a pain.001 file and answers `202 Accepted` with a pain.002 report. The test file, [`salaries-march.xml`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/src/test/resources/pain/salaries-march.xml), pays two salaries (shortened):

```xml
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
  <CstmrCdtTrfInitn>
    <GrpHdr>
      <MsgId>SAL-2026-03</MsgId>
      <CreDtTm>2026-03-25T09:00:00</CreDtTm>
      <NbOfTxs>2</NbOfTxs>
    </GrpHdr>
    <PmtInf>
      <PmtInfId>SAL-2026-03-1</PmtInfId>
      <DbtrAcct><Id><IBAN>NL91ABNA0417164300</IBAN></Id></DbtrAcct>
      <CdtTrfTxInf>
        <EndToEndId>SAL-JAN-JANSEN</EndToEndId>
        <InstdAmt Ccy="EUR">2450.00</InstdAmt>
        <Cdtr><Nm>Jan Jansen</Nm></Cdtr>
        <CdtrAcct><Id><IBAN>NL20INGB0001234567</IBAN></Id></CdtrAcct>
      </CdtTrfTxInf>
      <CdtTrfTxInf> ... Anna de Vries, 2780.00 ... </CdtTrfTxInf>
    </PmtInf>
  </CstmrCdtTrfInitn>
</Document>
```

The API accepts the file (`ACCP`) when `NbOfTxs` matches the number of transactions, and rejects it (`RJCT`) when it doesn't. A rejected report looks like this:

```xml
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.002.001.10">
  <CstmrPmtStsRpt>
    <GrpHdr>
      <MsgId>STS-SAL-2026-03</MsgId>
      <CreDtTm>2026-11-11T06:29:21</CreDtTm>
    </GrpHdr>
    <OrgnlGrpInfAndSts>
      <OrgnlMsgId>SAL-2026-03</OrgnlMsgId>
      <OrgnlMsgNmId>pain.001.001.09</OrgnlMsgNmId>
      <OrgnlNbOfTxs>3</OrgnlNbOfTxs>
      <GrpSts>RJCT</GrpSts>
      <StsRsnInf><AddtlInf>NbOfTxs is 3 but the file has 2 transactions</AddtlInf></StsRsnInf>
    </OrgnlGrpInfAndSts>
  </CstmrPmtStsRpt>
</Document>
```

The XSDs in [`src/test/resources/xsd`](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-06/src/test/resources/xsd) are simplified subsets of the official ones. They keep the element names, nesting and namespaces of the standard, and leave out most optional elements.

## Validating Both Directions

### Step 1: The Test Data Is Valid Too

```java
private static final String PAIN_001_XSD = "xsd/pain.001.001.09-simplified.xsd";
private static final String PAIN_002_XSD = "xsd/pain.002.001.10-simplified.xsd";

@Test
void theTestDataIsValidToo() {
    assertThat(salaries, matchesXsdInClasspath(PAIN_001_XSD));
}
```

`salaries` is the content of `salaries-march.xml`, read in `@BeforeAll`. If the test file is invalid, every test that sends it tests the wrong thing: the API might reject it for a reason that has nothing to do with the test. One line rules that out.

### Step 2: An Accepted File

```java
private static final String STATUS = "Document.CstmrPmtStsRpt.OrgnlGrpInfAndSts";

@Test
void anAcceptedFile() {
    paymentFiles()
        .body(salaries)
    .when()
        .post("/payment-files")
    .then()
        .statusCode(202)
        .contentType(ContentType.XML)
        .body(matchesXsdInClasspath(PAIN_002_XSD))
        .body(STATUS + ".OrgnlMsgId", equalTo("SAL-2026-03"))
        .body(STATUS + ".GrpSts", equalTo("ACCP"));
}
```

The XSD checks the structure of the report. The path checks, with the XML GPath from [Module 3](module-03-section-07-rest-assured-xml-and-html.md), check its meaning: the report refers to the file that was sent, and accepts it. `ACCP` is the ISO 20022 code for "accepted".

### Step 3: A Rejected File Is Still a Valid Report

```java
@Test
void aRejectedFileIsStillAValidReport() {
    paymentFiles()
        .body(salaries.replace("<NbOfTxs>2</NbOfTxs>", "<NbOfTxs>3</NbOfTxs>"))
    .when()
        .post("/payment-files")
    .then()
        .statusCode(202)
        .body(matchesXsdInClasspath(PAIN_002_XSD))
        .body(STATUS + ".GrpSts", equalTo("RJCT"))
        .body(STATUS + ".StsRsnInf.AddtlInf", containsString("the file has 2 transactions"));
}
```

A rejection is a business outcome, not an HTTP error: the status is still `202`, and the reason is in the report. The schema must hold for rejections too, including the optional `StsRsnInf` element that only appears in them.

> **The code above, ready to run**
>
> All four tests in [`XmlSchemaTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/src/test/java/com/example/module_06/section_03/XmlSchemaTest.java) pass with `mvn test -pl module-06 -Dtest=XmlSchemaTest`. Open the XSDs next to the XML to see how each element is described.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 6 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-06/README.md)

## When a Document Is Invalid

```java
@Test
void theXsdRejectsBrokenDocuments() {
    String noAmountCurrency = salaries.replace("<InstdAmt Ccy=\"EUR\">2450.00</InstdAmt>", "<InstdAmt>2450.00</InstdAmt>");

    SAXParseException invalid = assertThrows(SAXParseException.class,
        () -> assertThat(noAmountCurrency, matchesXsdInClasspath(PAIN_001_XSD)));
    assertThat(invalid.getMessage(), containsString("Attribute 'Ccy' must appear on element 'InstdAmt'"));
}
```

This is where the XSD matcher differs from the JSON Schema matcher. An invalid document doesn't make it return `false`. It rethrows the validator's `org.xml.sax.SAXParseException`, with a message such as:

```text
cvc-complex-type.4: Attribute 'Ccy' must appear on element 'InstdAmt'.
```

So `not(matchesXsdInClasspath(..))` can't express "is invalid": it throws instead of passing. Expect the exception with `assertThrows(..)`, and check its message to be sure the document failed for the reason you meant.

The same exception, not an `AssertionError`, is what a failing response check throws inside a `then()` chain. JUnit still reports the test as failed, as an error rather than an assertion failure.

## Common Pitfalls

1. **`not(..)` around the XSD matcher.** It throws `SAXParseException` instead of passing. Use `assertThrows(SAXParseException.class, ..)`.
2. **The wrong namespace.** An XSD validates elements in its target namespace only. A pain.001.001.03 file checked against the 001.09 schema fails with `cvc-elt.1.a: Cannot find the declaration of element 'Document'`, which says nothing about versions. Check the `xmlns` first.
3. **The wrong XSD for the message.** The same message reads "Cannot find the declaration of element 'Document'" when a pain.001 is checked against the pain.002 schema.
4. **Unvalidated test files.** A file that the bank would reject makes every test that sends it unreliable. Validate test data against its XSD.
5. **Treating the XSD as the whole contract.** It checks structure, not meaning. `NbOfTxs` can be a valid number and still be wrong. Combine it with path checks.

## Wrapping Up

`matchesXsdInClasspath(..)` validates XML against an XML Schema, for responses and, because it is a Hamcrest matcher, for any XML a test uses. For ISO 20022, that means checking both the pain.001 files a test sends and the pain.002 reports the API returns, accepted or rejected. An invalid document makes the matcher throw `SAXParseException`, so test for invalidity with `assertThrows(..)`. Combine the XSD with path checks, because a schema checks the shape of a message, not whether its content is right.
