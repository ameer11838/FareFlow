package com.fareflow.xrpl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import java.lang.annotation.*;

/**
 * Present only when the RLUSD rail is fully configured.
 *
 * <p>An expression rather than {@code @ConditionalOnProperty} because the three
 * settings are declared in {@code application.yml} with empty defaults, and an
 * empty-but-present property satisfies {@code @ConditionalOnProperty}. Every
 * environment would therefore try to build the rail and fail at startup on a
 * zero-length custody key — which is exactly what a missing integration must not do.
 *
 * <p>All three are required together: an issuer with no treasury has nowhere to
 * pay, and either without a custody key cannot hold the account that pays.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnExpression(
        "!'${fareflow.xrpl.issuer-address:}'.isEmpty()"
                + " && !'${fareflow.xrpl.treasury-address:}'.isEmpty()"
                + " && !'${fareflow.xrpl.issuer-seed:}'.isEmpty()"
                + " && !'${fareflow.xrpl.custody-key:}'.isEmpty()")
public @interface ConditionalOnXrplRail {
}
