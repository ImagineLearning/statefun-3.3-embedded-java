package com.example.stateful_functions.function;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to identify concrete stateful function implementation classes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE})
@Component
@Scope("prototype")
public @interface StatefunFunction {
}
