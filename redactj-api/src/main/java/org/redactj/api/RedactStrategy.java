package org.redactj.api;

/**
 * Defines available masking strategies for sensitive data.
 */
public enum RedactStrategy {
    /** Masks the entire value (e.g., secret -> ******). */
    FULL,

    /** Preserves the first character and the domain (e.g., alexandre@corp.com -> a***@corp.com). */
    EMAIL,

    /** Preserves only the last four characters (e.g., 4970123456789012 -> ****9012). */
    LAST_FOUR
}