package org.redactj.api;

/**
 * Deterministic and null-safe masking transformation algorithms.
 */
public final class RedactUtils {

    private RedactUtils() {
        // Non-instantiable utility class
    }

    /**
     * Applies the specified masking strategy to the given input.
     *
     * @param input    the raw string to hide
     * @param strategy the masking strategy to apply
     * @return the masked string, or "null" if the input is null
     */
    public static String apply(String input, RedactStrategy strategy) {
        if (input == null) {
            return "null";
        }
        if (input.isBlank()) {
            return input;
        }

        RedactStrategy targetStrategy = (strategy != null) ? strategy : RedactStrategy.FULL;

        return switch (targetStrategy) {
            case FULL -> "*".repeat(Math.min(input.length(), 8));

            case EMAIL -> {
                int atIndex = input.indexOf('@');
                if (atIndex <= 1) {
                    yield "***" + (atIndex >= 0 ? input.substring(atIndex) : "");
                }
                yield input.charAt(0) + "***" + input.substring(atIndex);
            }

            case LAST_FOUR -> {
                if (input.length() <= 4) {
                    yield "****";
                }
                yield "****" + input.substring(input.length() - 4);
            }
        };
    }
}