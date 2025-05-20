package com.example.stateful_functions.router;

import com.example.stateful_functions.cloudevents.ExampleCloudEventJsonFormat;
import com.example.stateful_functions.protobuf.ExampleProtobuf;
import io.cloudevents.CloudEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.flink.statefun.sdk.io.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


@Singleton
public final class MessageRouter implements Router<ExampleProtobuf.Envelope> {

    private static final Logger LOG = LoggerFactory.getLogger(MessageRouter.class);

    // Spring automatically collects all the of Forwarders and provides them as a list here
    @Inject
    private List<Forwarder> forwarders;

    @Inject
    ExampleCloudEventJsonFormat cloudEventJsonFormat;


    @Override
    public void route(ExampleProtobuf.Envelope envelope, Downstream<ExampleProtobuf.Envelope> downstream) {

        CloudEvent cloudEvent = cloudEventJsonFormat.deserialize(envelope.getPayload());
        if (cloudEvent == null) {
            return;
        }
        for (Forwarder forwarder : forwarders) {
            if (forwarder.accept(cloudEvent)) {
                forwarder.forward(cloudEvent, downstream);
            }
        }
    }
}

