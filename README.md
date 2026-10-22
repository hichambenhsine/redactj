# RedactJ

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Build](https://img.shields.io/badge/Build-Maven%203.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Architecture](https://img.shields.io/badge/Architecture-JSR%20269%20%7C%20SPI-purple.svg)]()

RedactJ is a compile-time PII (Personally Identifiable Information) redaction and data masking framework for Java 21. By shifting data masking computation entirely into the compilation phase (javac), RedactJ enforces strict Shift-Left Security and guarantees zero runtime reflection overhead.

---

## Overview

### Standard Java Logging (Data Leak Risk)
```java
// Standard toString() and formatters expose sensitive PII in plain-text logs
logger.info("Checkout initiated for: {}", customer);
// Output: Customer[id=CUST-01, email=user@example.com, card=4970123456789012]
```

### With RedactJ (Compile-Time Masked)
```java
public record Customer(
    String id,
    @Redact(strategy = RedactStrategy.EMAIL) String email,
    @Redact(strategy = RedactStrategy.LAST_FOUR) String card
) {}

// Automated Logback interception with zero reflection:
logger.info("Checkout initiated for: {}", customer);
// Output: Customer[id=CUST-01, email=u***@example.com, card=****9012]
```

---

## Installation and Build Configuration

### 1. Maven Coordinates
Add the API and the optional automated Logback adapter to your pom.xml:

```xml
<dependencies>
    <!-- Core API and Annotations -->
    <dependency>
        <groupId>org.redactj</groupId>
        <artifactId>redactj-api</artifactId>
        <version>1.0.0</version>
    </dependency>

    <!-- Optional: Automated Logback Logging Interceptor -->
    <dependency>
        <groupId>org.redactj</groupId>
        <artifactId>redactj-logback</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

### 2. Annotation Processor Configuration
Hook the redactj-processor into maven-compiler-plugin:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
            <configuration>
                <source>21</source>
                <target>21</target>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.redactj</groupId>
                        <artifactId>redactj-processor</artifactId>
                        <version>1.0.0</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

---

## Usage Guide

### 1. Annotate Domain Models
RedactJ natively supports both encapsulated classes and Java 21 canonical Records:

```java
// Standard Class (Requires accessible getters)
public class User {
    @Redact(strategy = RedactStrategy.EMAIL)
    private String email;

    @Redact(strategy = RedactStrategy.FULL)
    private String password;

    public String getEmail() { return email; }
    public String getPassword() { return password; }
}

// Java 21 Record (Accessors resolved automatically)
public record CustomerRecord(
    String id,
    @Redact(strategy = RedactStrategy.LAST_FOUR) String creditCardNumber
) {}
```

### 2. Automated Logback Integration
Configure your `src/main/resources/logback.xml` with the `%redactMsg` conversion rule:

```xml
<configuration>
    <!-- Register RedactJ conversion pattern rule -->
    <conversionRule conversionWord="redactMsg" 
                    converterClass="org.redactj.logback.RedactJMessageConverter" />

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %redactMsg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

Log normally via SLF4J. RedactJ intercepts arguments and secures sensitive data with zero manual invocation:
```java
logger.info("Authenticated user: {}", user);
logger.info("Payment confirmed: {}", customerRecord);
```

### 3. Direct Programmatic Invocation
Alternatively, mask objects synchronously via the RedactJ facade:
```java
String safeOutput = RedactJ.redact(user);
```

---

## Redaction Strategies Reference

| Strategy | Description | Original Input    | Output Value     |
| :--- | :--- |:------------------|:-----------------|
| `RedactStrategy.FULL` (Default) | Replaces characters with asterisks (max 8) | Secret123!        | ********         |
| `RedactStrategy.EMAIL` | Preserves first character and domain | user@example.com  | u***@example.com |
| `RedactStrategy.LAST_FOUR` | Preserves only the last 4 characters | 4970123456789012  | ****9012         |

---

## Compile-Time Fail-Fast Verification

RedactJ transforms runtime errors into early compilation failures:

1. Type Incompatibility Check: `@Redact` is restricted to `java.lang.String` members. Non-string types trigger an immediate compilation error (`Diagnostic.Kind.ERROR`).
2. Encapsulation Access Check: Private fields without accessible getters (`getFieldName` or `isFieldName`) cause compilation to fail, preventing unreadable state at runtime.

---

## Building and Verification

Run the full reactor build and automated compiler test suite (powered by Google Compile Testing):

```bash
# Build all modules
mvn clean install

# Execute in-memory javac compiler tests
mvn test -pl redactj-processor -am
```

---

## License

RedactJ is open-source software licensed under the Apache 2.0 License.
