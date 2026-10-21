package org.redactj.example;

import org.redactj.api.Redact;
import org.redactj.api.RedactStrategy;

/**
 * Java 21 Record demonstrating direct component masking without getters.
 */
public record CustomerRecord(
        String id,
        String customerName,
        @Redact(strategy = RedactStrategy.LAST_FOUR) String creditCardNumber
) {
}