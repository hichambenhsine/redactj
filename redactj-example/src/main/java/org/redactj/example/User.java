package org.redactj.example;

import org.redactj.api.Redact;
import org.redactj.api.RedactStrategy;

/**
 * Standard entity class demonstrating private field masking with getters.
 */
public class User {

    private String username;

    @Redact(strategy = RedactStrategy.EMAIL)
    private String email;

    @Redact(strategy = RedactStrategy.FULL)
    private String password;

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}