package com.example.stateful_functions.function;

import io.micronaut.context.ApplicationContext;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.statefun.sdk.FunctionType;
import org.apache.flink.statefun.sdk.StatefulFunction;
import org.apache.flink.statefun.sdk.StatefulFunctionProvider;
import org.apache.flink.statefun.sdk.spi.StatefulFunctionModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class FunctionProvider implements StatefulFunctionProvider {

    private static final Logger LOG = LoggerFactory.getLogger(FunctionProvider.class);

    private static final String STATEFUN_BASE_PACKAGE = "com.example.stateful_functions";

    @Inject
    ApplicationContext applicationContext;

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private final Map<FunctionType,Class> functionsByType = new HashMap<>();

    @PostConstruct
    public void afterPropertiesSet() throws Exception {

        Collection<StatefulFunction> functions = applicationContext.getBeansOfType(StatefulFunction.class);

        for (StatefulFunction function : functions) {

            Class functionClass = function.getClass();

            try {
                functionsByType.put((FunctionType) functionClass.getField("FUNCTION_TYPE").get(null), functionClass);
            }
            catch (Exception x) {
                String message = "Can't access required static FunctionType field FUNCTION_TYPE in " + functionClass.getName();
                LOG.error(message);
                throw new ReflectiveOperationException(message, x);
            }
        }

    }

    @Override
    public StatefulFunction functionOfType(FunctionType functionType) {

        if (!functionsByType.containsKey(functionType)) {
            throw new IllegalArgumentException("Unknown function type: " + functionType);
        }

        return (StatefulFunction)applicationContext.getBean(functionsByType.get(functionType));
    }

    public void bindFunctions(StatefulFunctionModule.Binder binder) {
        for (FunctionType functionType : functionsByType.keySet()) {
            binder.bindFunctionProvider(functionType, this);
        }
    }

    // FOR TESTING ONLY!
    @VisibleForTesting
    public Map<FunctionType,Class> getFunctionsByType() {
        return this.functionsByType;
    }
}
