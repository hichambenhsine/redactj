package org.redactj.example;

import org.redactj.api.RedactJ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verification runner demonstrating:
 * 1. Direct synchronous masking through the RedactJ SPI facade.
 * 2. Automated, zero-code-change log interception via Logback pattern conversion.
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        User user = new User("Hicham", "Hicham@example.com", "SuperSecretPassword123!");
        CustomerRecord customer = new CustomerRecord("1000", "Hicham", "4970123456789012");

        System.out.println("=== 1. Direct Façade Demonstration ===");
        System.out.println(RedactJ.redact(user));
        System.out.println(RedactJ.redact(customer));

        System.out.println("\n=== 2. Automated Logback Masking (No RedactJ.redact() call!) ===");
        logger.info("Processing user login: {}", user);
        logger.info("Charging customer invoice: {}", customer);
    }
}