package com.example.stateful_functions.router;

import com.example.stateful_functions.protobuf.ExampleProtobuf;
import io.cloudevents.CloudEvent;
import org.apache.flink.statefun.sdk.io.Router;

import java.util.List;

public interface Forwarder {
    boolean accept(CloudEvent event);
    void forward(CloudEvent event, Router.Downstream<ExampleProtobuf.Envelope> downstream);
}
