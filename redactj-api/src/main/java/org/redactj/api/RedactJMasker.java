package org.redactj.api;

/**
 * SPI contract implemented by every compile-time generated masking companion class.
 *
 * @param <T> the target entity type
 */
public interface RedactJMasker<T> {

    /**
     * Produces a secure, masked string representation of the given entity.
     *
     * @param entity the instance to mask
     * @return the redacted string representation
     */
    String redact(T entity);

    /**
     * Returns the target entity class handled by this masker implementation.
     *
     * @return the Class object of the target entity
     */
    Class<T> getTargetClass();
}
