package org.redactj.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

/**
 * Automated compiler-level unit test suite validating {@link RedactProcessor} behavior
 * using Google Compile Testing in-memory javac sessions.
 */
class RedactProcessorTest {

    @Test
    @DisplayName("Should successfully compile valid entity and synthesize companion RedactMasker")
    void shouldCompileValidEntityAndGenerateMasker() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "com.example.Account",
                "package com.example;",
                "import org.redactj.api.Redact;",
                "import org.redactj.api.RedactStrategy;",
                "public class Account {",
                "    @Redact(strategy = RedactStrategy.EMAIL)",
                "    private String email;",
                "    public String getEmail() { return email; }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new RedactProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("com.example.AccountRedactMasker");
    }

    @Test
    @DisplayName("Should fail compilation when @Redact is applied to non-String field")
    void shouldFailCompilationWhenFieldIsNotString() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "com.example.InvalidTypeEntity",
                "package com.example;",
                "import org.redactj.api.Redact;",
                "public class InvalidTypeEntity {",
                "    @Redact",
                "    private int secretPin = 1234;",
                "    public int getSecretPin() { return secretPin; }",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new RedactProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining("RedactJ violation: @Redact can only be applied to java.lang.String types");
    }

    @Test
    @DisplayName("Should fail compilation when private field lacks accessible getter")
    void shouldFailCompilationWhenPrivateFieldLacksGetter() {
        JavaFileObject source = JavaFileObjects.forSourceLines(
                "com.example.MissingGetterEntity",
                "package com.example;",
                "import org.redactj.api.Redact;",
                "public class MissingGetterEntity {",
                "    @Redact",
                "    private String password = \"secret\";",
                "    // No getter provided for private field",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new RedactProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining("Cannot access private field 'password'");
    }
}