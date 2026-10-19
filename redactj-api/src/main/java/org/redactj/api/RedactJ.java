package org.redactj.api;


import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main public entry point of the RedactJ data-masking framework.
 */
public final class RedactJ {

    // Thread-safe cache ensuring nanosecond-level lookups after warm-up
    private static final Map<Class<?>, RedactJMasker<?>> CACHE = new ConcurrentHashMap<>();

    static {
        // Dynamic discovery of generated maskers via Java SPI at classloading time
        ServiceLoader<RedactJMasker> loader = ServiceLoader.load(RedactJMasker.class);
        for (RedactJMasker<?> masker : loader) {
            CACHE.put(masker.getTargetClass(), masker);
        }
    }

    private RedactJ() {
        // Utility class
    }

    /**
     * Redacts sensitive data from the provided entity using its generated companion masker.
     * Falls back to standard toString() if no dedicated masker is available.
     *
     * @param entity the object to mask
     * @param <T>    the object type
     * @return the masked string representation, or "null" if the entity is null
     */
    @SuppressWarnings("unchecked")
    public static <T> String redact(T entity) {
        if (entity == null) {
            return "null";
        }

        RedactJMasker<T> masker = (RedactJMasker<T>) CACHE.computeIfAbsent(
                entity.getClass(),
                clazz -> {
                    ServiceLoader<RedactJMasker> loader = ServiceLoader.load(RedactJMasker.class);
                    for (RedactJMasker<?> m : loader) {
                        if (m.getTargetClass().equals(clazz)) {
                            return m;
                        }
                    }
                    return null;
                }
        );

        return (masker != null) ? masker.redact(entity) : entity.toString();
    }
}
