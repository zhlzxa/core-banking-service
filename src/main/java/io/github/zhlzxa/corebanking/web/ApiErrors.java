package io.github.zhlzxa.corebanking.web;

import io.github.zhlzxa.corebanking.common.error.ErrorCode;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the business error codes an endpoint can answer with, in addition to the errors every
 * endpoint can produce (invalid request, authentication, authorization, internal error).
 *
 * <p>The API description is generated from these declarations and from {@link ErrorCode}, so the
 * documented status, title and code of each error always match what the service returns.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ApiErrors {

    ErrorCode[] value();
}
