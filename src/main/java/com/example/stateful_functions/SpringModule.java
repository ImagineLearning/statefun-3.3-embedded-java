package com.example.stateful_functions;

import com.example.stateful_functions.egress.EgressSpecs;
import com.example.stateful_functions.function.FunctionProvider;
import com.example.stateful_functions.ingress.IngressSpecs;
import com.example.stateful_functions.router.MessageRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.statefun.sdk.spi.StatefulFunctionModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Configuration
@ComponentScan(basePackages = "com.example.stateful_functions")
public class SpringModule {

    private static Logger LOG = LoggerFactory.getLogger(SpringModule.class);

    @Autowired
    MessageRouter messageRouter;

    @Autowired
    FunctionProvider functionProvider;


    public SpringModule() { }

    public void configure(Map<String, String> globalConfiguration, StatefulFunctionModule.Binder binder) {
        // bind the default ingress to the system along with the router
        binder.bindIngress(IngressSpecs.DEFAULT_KINESIS_INGRESS);
        binder.bindIngressRouter(IngressSpecs.INGRESS_ID, messageRouter);

        // bind an egress to the system
        binder.bindEgress(EgressSpecs.kinesisEgress);

        // bind the function types to the functionProvider
        functionProvider.bindFunctions(binder);
    }

    @Bean
    @Scope("singleton")
    public ObjectMapper getObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure objectMapper has needed here
        return objectMapper;
    }
}
