# Keeping Versions Aligned: REST Assured, JUnit and Jackson Under Spring Boot's BOM

Keeping Versions Aligned: Learn what Spring Boot 4.1's dependency management decides for REST Assured, JUnit, Jackson and Hamcrest, why the first imported BOM wins, how to order BOMs so the versions you choose apply, and how to check which versions Maven really resolved.

Adding Spring Boot to a project changes more than the application code. Spring Boot's dependency management sets the versions of hundreds of libraries, including several that a REST Assured test suite depends on: JUnit, Jackson and Hamcrest. If those versions are older than the ones the rest of your suite uses, nothing warns you. Tests that passed in the other modules start failing in strange ways, or a newer feature simply isn't there. This section has no test class. Its lesson is in the module's `pom.xml`.

This post follows [Section 4 of Module 11](https://github.com/andre-mare/java-rest-assured-testing/tree/main/module-11) of the REST Assured course. It explains how Maven chooses versions when several BOMs manage the same library, and how this module makes sure the course's versions apply. The configuration is in [`module-11/pom.xml`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-11/pom.xml).

## What is a BOM?

A BOM (bill of materials) is a Maven POM that only lists versions, in its `dependencyManagement` section. You **import** it into your own `dependencyManagement`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-dependencies</artifactId>
    <version>${spring-boot.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

After that, dependencies whose versions the BOM manages can be declared without a version, and transitive dependencies are pulled to the managed version too.

Three rules decide which version wins:

1. **A version you declare yourself**, in a dependency or your own `dependencyManagement`, beats any imported BOM.
2. **Between imported BOMs, the first one declared wins.** A later BOM can't override an earlier one.
3. **A library no BOM manages** gets the version its dependants ask for, by Maven's "nearest wins" rule.

## What Spring Boot 4.1 Manages

Spring Boot 4.1.2 manages several libraries this course uses, at versions older than the course's:

| Library | Spring Boot 4.1.2 manages | The course uses |
| --- | --- | --- |
| JUnit | 6.0.3 | 6.1.3 |
| Jackson 3 | 3.1.7 | 3.2.3 |
| Hamcrest | 3.0 | 3.0 |
| REST Assured | not managed any more | 6.3.0 |

Spring Boot 4 no longer manages REST Assured, so its version comes from the course's parent POM. JUnit and Jackson would silently drop to Spring Boot's versions.

## The Module's BOM Order

```xml
<dependencyManagement>
    <dependencies>
        <!-- Lesson 11.4: when several imported BOMs manage the same artifact, the first one declared wins. -->
        <dependency>
            <groupId>org.junit</groupId>
            <artifactId>junit-bom</artifactId>
            <version>6.1.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured-bom</artifactId>
            <version>6.3.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>tools.jackson</groupId>
            <artifactId>jackson-bom</artifactId>
            <version>${jackson.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

JUnit's, REST Assured's and Jackson's BOMs come **before** Spring Boot's. For those libraries, they win. For everything else, Spring Boot's BOM still applies, so Spring's own dependencies stay consistent with each other.

Importing a library's whole BOM, rather than pinning one artifact, matters. A library family such as Jackson or JUnit is many artifacts that must have exactly the same version. A BOM sets them all at once.

## Checking What Maven Resolved

Never assume: ask Maven. `dependency:tree` shows the versions it chose:

```bash
mvn -f module-11/pom.xml dependency:tree \
    -Dincludes='org.junit.jupiter:junit-jupiter-api,tools.jackson.core:jackson-databind,io.rest-assured:*,org.hamcrest:hamcrest'
```

With the module's BOM order:

```text
com.example:module-11:jar:0.0.1-SNAPSHOT
+- org.springframework.boot:spring-boot-starter-webmvc:jar:4.1.2:compile
|  \- org.springframework.boot:spring-boot-starter-jackson:jar:4.1.2:compile
|     \- org.springframework.boot:spring-boot-jackson:jar:4.1.2:compile
|        \- tools.jackson.core:jackson-databind:jar:3.2.3:compile
+- io.rest-assured:spring-mock-mvc:jar:6.3.0:test
|  \- io.rest-assured:spring-commons:jar:6.3.0:test
+- org.junit.jupiter:junit-jupiter:jar:6.1.3:test
|  \- org.junit.jupiter:junit-jupiter-api:jar:6.1.3:test
\- io.rest-assured:rest-assured:jar:6.3.0:test
   +- org.hamcrest:hamcrest:jar:3.0:test
   +- io.rest-assured:json-path:jar:6.3.0:test
   |  \- io.rest-assured:rest-assured-common:jar:6.3.0:test
   \- io.rest-assured:xml-path:jar:6.3.0:test
```

With Spring Boot's BOM moved to the top, and nothing else changed:

```text
|        \- tools.jackson.core:jackson-databind:jar:3.1.7:compile
...
+- org.junit.jupiter:junit-jupiter:jar:6.0.3:test
|  \- org.junit.jupiter:junit-jupiter-api:jar:6.0.3:test
```

Jackson drops to 3.1.7 and JUnit to 6.0.3. The build still succeeds, and no warning is printed. REST Assured stays at 6.3.0, because Spring Boot doesn't manage it.

> **Try it yourself**
>
> There is no test class for this section. Run the `dependency:tree` command above, then move the `spring-boot-dependencies` import to the top of `dependencyManagement` in [`module-11/pom.xml`](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-11/pom.xml) and run it again. Put it back afterwards.
>
> [Clone the repository](https://github.com/andre-mare/java-rest-assured-testing) · [Read the Module 11 README](https://github.com/andre-mare/java-rest-assured-testing/blob/main/module-11/README.md)

## The Classic Failure: a Mixed Family

The worst outcome isn't an old version. It is a **mixed** one: some artifacts of a library family at one version and some at another. That happens when a version is pinned for one artifact instead of the whole family:

- **A newer `jackson-databind` on an older `jackson-core`** fails at run time with `NoSuchMethodError` or `NoClassDefFoundError`, often in a test that seems unrelated.
- **JUnit Jupiter and the JUnit Platform at different versions** can stop tests from being discovered at all.
- **An older `rest-assured-common` under REST Assured 6.3.** REST Assured 6.3 checks that its modules have matching versions, and reports a mismatch at start-up instead of failing later with an obscure error.

Importing each family's BOM, as this module does, prevents all three.

## Common Pitfalls

1. **Spring Boot's BOM first.** It pins JUnit and Jackson to its own versions, silently. Import the BOMs you want to win before it.
2. **Pinning one artifact of a family.** A newer `jackson-databind` alone mixes versions. Import the family's BOM.
3. **Trusting the version in your POM.** A `<version>` property that nothing uses, or a BOM that loses, changes nothing. Check with `dependency:tree`.
4. **Assuming Spring Boot manages REST Assured.** Spring Boot 4 doesn't. Declare its version, preferably with `rest-assured-bom`.
5. **Upgrading Spring Boot without checking.** A new Spring Boot version can change what it manages. Run `dependency:tree` after every upgrade.

## Wrapping Up

Spring Boot's BOM manages JUnit, Jackson and Hamcrest, and Spring Boot 4.1.2 manages them at older versions than this course uses. When several imported BOMs manage the same library, the first one declared wins, so this module imports JUnit's, REST Assured's and Jackson's BOMs before Spring Boot's. Spring Boot 4 no longer manages REST Assured at all. Import whole BOMs rather than pinning single artifacts, so a library family never mixes versions, and check what Maven really resolved with `dependency:tree`.
