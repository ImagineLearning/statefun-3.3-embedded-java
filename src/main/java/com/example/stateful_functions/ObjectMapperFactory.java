package com.example.stateful_functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class ObjectMapperFactory {

    @Singleton
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
