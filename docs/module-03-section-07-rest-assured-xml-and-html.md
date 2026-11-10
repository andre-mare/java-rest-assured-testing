# XML and HTML in REST Assured: Elements, Attributes, Depth-First Search and Namespaces

XML and HTML in REST Assured: Check XML and HTML responses with the same body(..) and GPath, covering elements and attributes, find and findAll, depth-first search with **, namespaces with and without declareNamespace, and lenient HTML.

Not every API speaks JSON. Banks exchange statements as ISO 20022 XML, older SOAP services answer in XML, and some endpoints return an HTML page. Do you need a different library to test them? With REST Assured you don't. The same `body(..)` call works, with the same GPath language. A few things are different, though: a path starts with the root element, attributes have their own syntax, values are text, and namespaces can get in the way.

This post follows [Section 7 of Module 3](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-03) of the REST Assured course. It checks a bank statement in XML and the same statement as an HTML page. The examples are in [`XmlAndHtmlTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/src/test/java/com/example/module_03/section_07/XmlAndHtmlTest.java), and the [usage guide](https://github.com/rest-assured/rest-assured/wiki/Usage#example-2---xml) has more XML examples.

## How Does GPath Work on XML?

REST Assured looks at the content type. For XML and HTML it parses the body with XmlPath instead of JsonPath. The paths look the same as for JSON, with four differences:

1. **A path starts with the root element.** For `<Document>..</Document>`, every path starts with `Document`.
2. **`@` reads an attribute.** `Amt.@Ccy` reads the `Ccy` attribute of `<Amt Ccy="EUR">`.
3. **`**` searches the whole tree, depth first.** `**.find { it.name() == 'MsgId' }` finds an element wherever it is. `it.name()` is the element's name.
4. **Values are text.** `<Amt>2407.65</Amt>` is the string `"2407.65"`, not a number.

`find`, `findAll`, indexes and `size()` work as in [Section 1](module-03-section-01-rest-assured-gpath-for-json.md).

## The Data Under Test

`GET /api/v1/accounts/acc-1001/statement` returns a simplified ISO 20022 camt.053, the bank-to-customer statement banks exchange (shortened):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.08" xmlns:ext="urn:example:bank:extensions">
  <BkToCstmrStmt>
    <GrpHdr>
      <MsgId>STMT-2026-03</MsgId>
      <CreDtTm>2026-04-01T06:00:00</CreDtTm>
    </GrpHdr>
    <Stmt>
      <Id>acc-1001-2026-03</Id>
      <Acct>
        <Id><IBAN>NL91ABNA0417164300</IBAN></Id>
        <Ccy>EUR</Ccy>
      </Acct>
      <Bal><Tp>OPBD</Tp><Amt Ccy="EUR">1000.00</Amt><CdtDbtInd>CRDT</CdtDbtInd></Bal>
      <Bal><Tp>CLBD</Tp><Amt Ccy="EUR">2407.65</Amt><CdtDbtInd>CRDT</CdtDbtInd></Bal>
      <Ntry>
        <NtryRef>tx-1</NtryRef>
        <Amt Ccy="EUR">2500.00</Amt>
        <CdtDbtInd>CRDT</CdtDbtInd>
        <BookgDt><Dt>2026-03-01</Dt></BookgDt>
        <NtryDtls>
          <RltdPties><Nm>Acme B.V.</Nm></RltdPties>
          <RmtInf><Ustrd>Salary March</Ustrd></RmtInf>
        </NtryDtls>
        <ext:Category>SALARY</ext:Category>
      </Ntry>
      ...
    </Stmt>
  </BkToCstmrStmt>
</Document>
```

- **`Bal`** is a balance: `OPBD` is the opening balance and `CLBD` the closing balance.
- **`Ntry`** is an entry, one per transaction. Amounts are positive, and `CdtDbtInd` says whether it is a credit (`CRDT`) or a debit (`DBIT`).
- **`ext:Category`** is in a second namespace, an extension that is not part of the standard.

`GET /api/v1/accounts/acc-1001/statement.html` returns the same transactions as an HTML table.

The tests use a constant for the long path to the statement:

```java
private static final String STATEMENT = "Document.BkToCstmrStmt.Stmt";
```

## XML Paths

### Step 1: Elements and Attributes

```java
@Test
void elementsAndAttributes() {
    transactionsApi()
    .when()
        .get("/accounts/{id}/statement", TransactionsApi.ACCOUNT_ID)
    .then()
        .contentType(ContentType.XML)
        .body("Document.BkToCstmrStmt.GrpHdr.MsgId", equalTo("STMT-2026-03"))
        .body(STATEMENT + ".Acct.Id.IBAN", equalTo(TransactionsApi.IBAN))
        .body(STATEMENT + ".Ntry.size()", equalTo(6))
        .body(STATEMENT + ".Bal[0].Amt.@Ccy", equalTo("EUR"));
}
```

- **Repeated elements** form a list: `Ntry` has six entries, so `Ntry.size()` is `6`, and `Bal[0]` is the first balance.
- **`@Ccy`** reads the attribute of the `Amt` element.

### Step 2: find and findAll on Elements

```java
@Test
void findAndFindAllWorkOnElements() {
    transactionsApi()
    .when()
        .get("/accounts/{id}/statement", TransactionsApi.ACCOUNT_ID)
    .then()
        .rootPath(STATEMENT)
        .body("Bal.find { it.Tp == 'CLBD' }.Amt", equalTo("2407.65"))
        .body("Ntry.findAll { it.CdtDbtInd == 'DBIT' }.NtryRef", contains("tx-2", "tx-3", "tx-4", "tx-5"))
        .body("Ntry.NtryDtls.RltdPties.Nm", hasItems("Acme B.V.", "NS Reizigers"));
}
```

- **`rootPath(..)`** from [Section 2](module-03-section-02-rest-assured-root-paths-and-path-arguments.md) works on XML too.
- **`it.Tp == 'CLBD'`** compares the text of a child element.
- **`equalTo("2407.65")`** is a string, because XML values are text.

### Step 3: Depth-First Search

```java
@Test
void depthFirstSearch() {
    transactionsApi()
    .when()
        .get("/accounts/{id}/statement", TransactionsApi.ACCOUNT_ID)
    .then()
        .body("**.find { it.name() == 'MsgId' }", equalTo("STMT-2026-03"))
        .body("**.findAll { it.name() == 'Ntry' }.size()", equalTo(6));
}
```

`**` visits every element in the document. The path no longer depends on where `MsgId` sits, so it keeps working when the structure around it changes. The cost is precision: if two elements have the same name in different places, `find` returns the first one.

> **The code above, ready to run**
>
> All five tests in [`XmlAndHtmlTest`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/src/test/java/com/example/module_03/section_07/XmlAndHtmlTest.java) pass with `mvn test -pl module-03 -Dtest=XmlAndHtmlTest`. Add `.log().body()` after `then()` to see the whole statement.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 3 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-03/README.md)

## Namespaces

The statement uses two namespaces. The default one, from the ISO 20022 standard, covers most elements. The `ext` namespace covers `ext:Category`.

```java
@Test
void namespaces() {
    // Without declaring namespaces, a path matches elements by their local name.
    transactionsApi()
    .when()
        .get("/accounts/{id}/statement", TransactionsApi.ACCOUNT_ID)
    .then()
        .body(STATEMENT + ".Ntry[2].Category", equalTo("RENT"));

    // Declaring a namespace lets a path match only elements in it.
    transactionsApi()
        .config(config().xmlConfig(xmlConfig().declareNamespace("ext", "urn:example:bank:extensions")))
    .when()
        .get("/accounts/{id}/statement", TransactionsApi.ACCOUNT_ID)
    .then()
        .body(STATEMENT + ".Ntry[2].ext:Category", equalTo("RENT"));
}
```

- **Without a declaration**, `Category` matches the element by its local name, whatever its namespace. That is simple, and enough for most tests.
- **With `declareNamespace(..)`**, the path `ext:Category` matches only `Category` elements in the namespace `urn:example:bank:extensions`. Use it when two namespaces have elements with the same name.

What counts is the namespace URI, not the prefix in the document. You could declare it as `x` and write `x:Category`.

## HTML

HTML uses the same XmlPath, with a lenient parser:

```java
@Test
void html() {
    transactionsApi()
    .when()
        .get("/accounts/{id}/statement.html", TransactionsApi.ACCOUNT_ID)
    .then()
        .contentType(ContentType.HTML)
        .body("html.head.title", equalTo("Statement " + TransactionsApi.IBAN))
        .body("html.body.h1", equalTo("March 2026"))
        .body("**.findAll { it.name() == 'tr' }.size()", equalTo(6))
        .body("**.findAll { it.name() == 'tr' }[2].td[1]", equalTo("Woonstichting De Key"))
        .body("**.find { it.@id == 'transactions' }.name()", equalTo("table"));
}
```

- **A full path** such as `html.head.title` works for simple pages.
- **Depth-first search** is safer for tables. HTML parsers repair markup as browsers do, and they may add elements you didn't write, such as a `<tbody>` between `<table>` and `<tr>`. A full path like `html.body.table.tr` breaks when that happens. `**.findAll { it.name() == 'tr' }` doesn't.
- **`it.@id`** finds an element by an attribute, like a CSS id selector.

Checking HTML this way suits a small server-rendered page. For a real web application, use a browser-testing tool.

## Common Pitfalls

1. **Comparing an XML value with a number.** `equalTo(2407.65f)` fails, because the value is the text `"2407.65"`. Compare with a string, or convert it yourself.
2. **Leaving out the root element.** For JSON, a path starts inside the body. For XML, it starts with the root element, here `Document`.
3. **A prefix without a declaration.** `ext:Category` matches nothing unless the namespace is declared with `XmlConfig`. Use the local name `Category`, or declare the namespace.
4. **Full paths into HTML tables.** The parser may add a `<tbody>`. Search for `tr` depth first.
5. **Depth-first search for a common name.** `**.find { it.name() == 'Amt' }` returns the first `Amt`, which is a balance, not a transaction. Use a full path when the name appears in several places.

## Wrapping Up

REST Assured checks XML and HTML with the same `body(..)` and GPath as JSON. A path starts with the root element, `@` reads an attribute, `**` searches depth first, and every value is text. Without a declared namespace, a path matches elements by their local name. `XmlConfig.declareNamespace(..)` makes a prefixed path match one namespace only. HTML is parsed leniently, so prefer depth-first search over full paths into tables.
