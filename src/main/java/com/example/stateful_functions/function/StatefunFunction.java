package com.example.stateful_functions.function;

import io.micronaut.context.annotation.Prototype;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to identify concrete stateful function implementation classes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE})
@Prototype
public @interface StatefunFunction {
}
