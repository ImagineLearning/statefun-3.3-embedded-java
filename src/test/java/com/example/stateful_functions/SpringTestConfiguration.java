package com.example.stateful_functions;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class SpringTestConfiguration  {
    @Inject
    ApplicationContext applicationContext;
}
