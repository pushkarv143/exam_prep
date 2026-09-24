package com.examprep.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative, Redis-backed rate limit for a controller method.
 *
 * <pre>
 * &#64;RateLimit(name = "login", limit = 10, windowSeconds = 60)                 // 10/min per IP
 * &#64;RateLimit(name = "autosave", limit = 30, windowSeconds = 60, key = KeyType.USER)
 * </pre>
 *
 * The counter is shared by all application instances, so the limit holds across the cluster.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Logical bucket name; becomes part of the Redis key. */
    String name();

    /** Max requests allowed per window. */
    int limit();

    /** Window length in seconds. */
    long windowSeconds();

    /** What the counter is keyed by. */
    KeyType key() default KeyType.IP;

    enum KeyType {
        /** Client IP (after X-Forwarded-For resolution). Use for anonymous endpoints. */
        IP,
        /** Authenticated user id. Falls back to IP when anonymous. */
        USER
    }
}
