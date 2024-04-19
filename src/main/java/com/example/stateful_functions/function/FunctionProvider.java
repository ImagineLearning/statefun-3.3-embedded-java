package com.example.stateful_functions.function;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.statefun.sdk.FunctionType;
import org.apache.flink.statefun.sdk.StatefulFunction;
import org.apache.flink.statefun.sdk.StatefulFunctionProvider;
import org.apache.flink.statefun.sdk.spi.StatefulFunctionModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class FunctionProvider implements StatefulFunctionProvider, ApplicationContextAware, InitializingBean {

    private static final Logger LOG = LoggerFactory.getLogger(FunctionProvider.class);

    private static final String STATEFUN_BASE_PACKAGE = "com.example.stateful_functions";

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private final Map<FunctionType,Class> functionsByType = new HashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        ClassPathScanningCandidateComponentProvider componentProvider = new ClassPathScanningCandidateComponentProvider(false);
        componentProvider.addIncludeFilter(new AnnotationTypeFilter(StatefulFunctionTag.class));

        for (BeanDefinition bean : componentProvider.findCandidateComponents(STATEFUN_BASE_PACKAGE)) {

            Class functionClass = Class.forName(bean.getBeanClassName());

            try {
                functionsByType.put((FunctionType) functionClass.getField("FUNCTION_TYPE").get(null), functionClass);
            }
            catch (Exception x) {
                String message = "Can't access required static FunctionType field FUNCTION_TYPE in " + bean.getBeanClassName();
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
