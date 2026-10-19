package org.redactj.api;

import java.lang.annotation.*;

/**
 * Marks a field or a Record component whose value must be masked in application logs.
 */
@Documented
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Redact {
    /** The masking strategy to apply. Defaults to FULL. */
    RedactStrategy strategy() default RedactStrategy.FULL;
}
