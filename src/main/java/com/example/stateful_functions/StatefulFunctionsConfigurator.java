package com.example.stateful_functions;

import com.example.stateful_functions.egress.EgressSpecs;
import com.example.stateful_functions.function.FunctionProvider;
import com.example.stateful_functions.ingress.IngressSpecs;
import com.example.stateful_functions.router.MessageRouter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.flink.statefun.sdk.spi.StatefulFunctionModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Singleton
public class StatefulFunctionsConfigurator {

    private static Logger LOG = LoggerFactory.getLogger(StatefulFunctionsConfigurator.class);

    @Inject
    MessageRouter messageRouter;

    @Inject
    FunctionProvider functionProvider;


    public StatefulFunctionsConfigurator() { }

    public void configure(Map<String, String> globalConfiguration, StatefulFunctionModule.Binder binder) {
        // bind the default ingress to the system along with the router
        binder.bindIngress(IngressSpecs.DEFAULT_KINESIS_INGRESS);
        binder.bindIngressRouter(IngressSpecs.INGRESS_ID, messageRouter);

        // bind an egress to the system
        binder.bindEgress(EgressSpecs.kinesisEgress);

        // bind the function types to the functionProvider
        functionProvider.bindFunctions(binder);
    }

}
