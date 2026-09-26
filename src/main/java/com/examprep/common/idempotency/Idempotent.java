package com.examprep.common.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes a controller method safe to retry with the same {@code Idempotency-Key} header:
 * the first call runs and its response is stored for 24 h. Repeats with the same key and
 * request replay the stored response instead of running again. The same key with a different
 * request is rejected (422), and a repeat while the first call is still running gets 409.
 *
 * <p>Use on money- and rank-changing operations: finalize ranks, re-evaluate, approvals,
 * refunds.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** When true (default), requests without the header are rejected with 400. */
    boolean required() default true;
}
