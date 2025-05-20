package com.example.stateful_functions.integration;

import com.example.stateful_functions.AbstractStatefulFunctionTest;
import com.example.stateful_functions.egress.EgressSpecs;
import com.example.stateful_functions.ingress.IngressSpecs;
import com.example.stateful_functions.protobuf.ExampleProtobuf;
import com.example.stateful_functions.util.TestMessageSource;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.flink.statefun.flink.harness.Harness;
import org.apache.flink.statefun.flink.harness.io.SerializableConsumer;


import java.util.ArrayList;
import java.util.List;

@MicronautTest
public abstract class StatefulFunctionIntegrationTest extends AbstractStatefulFunctionTest  {

    @Inject
    TestMessageSource testMessageSource;

    static final List<ExampleProtobuf.Envelope> egressEvents = new ArrayList<>();

    protected List<ExampleProtobuf.Envelope> executeTestHarnessWith(String messageSourceResourceName) throws Exception {
        testMessageSource.setEventsResourcePath(messageSourceResourceName);

        egressEvents.clear();

        Harness harness =
                new Harness()
                        .withFlinkSourceFunction(IngressSpecs.INGRESS_ID, testMessageSource)
                        .withConsumingEgress(EgressSpecs.ID, (SerializableConsumer<ExampleProtobuf.Envelope>) envelope -> egressEvents.add(envelope))
                        .withConfiguration("pipeline.closure-cleaner-level","NONE");

        harness.start();

        return egressEvents;
    }
}
