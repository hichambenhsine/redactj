package org.redactj.logback;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.redactj.api.RedactJ;
import org.slf4j.helpers.MessageFormatter;

/**
 * Custom Logback pattern converter that intercepts log events, extracts their arguments,
 * and transparently applies RedactJ masking before final message formatting.
 */
public class RedactJMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        Object[] argumentArray = event.getArgumentArray();

        // If the log statement has no arguments (e.g. logger.info("Hello world")),
        // return the raw formatted message as-is.
        if (argumentArray == null || argumentArray.length == 0) {
            return event.getFormattedMessage();
        }

        // Intercept each argument and pass it through the RedactJ facade
        Object[] maskedArguments = new Object[argumentArray.length];
        for (int i = 0; i < argumentArray.length; i++) {
            Object originalArg = argumentArray[i];
            maskedArguments[i] = (originalArg != null) ? RedactJ.redact(originalArg) : null;
        }

        // Reconstruct the secure log message using SLF4J's optimized array formatter
        return MessageFormatter.arrayFormat(event.getMessage(), maskedArguments).getMessage();
    }
}