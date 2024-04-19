package com.example.stateful_functions.router;

import com.example.stateful_functions.cloudevents.ExampleCloudEventJsonFormat;
import com.example.stateful_functions.protobuf.ExampleProtobuf;
import io.cloudevents.CloudEvent;
import org.apache.flink.statefun.sdk.io.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public final class MessageRouter implements Router<ExampleProtobuf.Envelope> {

    private static final Logger LOG = LoggerFactory.getLogger(MessageRouter.class);

    // Spring automatically collects all the of Forwarders and provides them as a list here
    @Autowired
    private List<Forwarder> forwarders;

    @Autowired
    ExampleCloudEventJsonFormat cloudEventJsonFormat;


    @Override
    public void route(ExampleProtobuf.Envelope envelope, Downstream<ExampleProtobuf.Envelope> downstream) {

        CloudEvent cloudEvent = cloudEventJsonFormat.deserialize(envelope.getPayload());
        for (Forwarder forwarder : forwarders) {
            if (forwarder.accept(cloudEvent)) {
                forwarder.forward(cloudEvent, downstream);
            }
        }
    }
}

