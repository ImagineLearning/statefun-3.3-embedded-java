package com.example.stateful_functions.router;

import com.example.stateful_functions.cloudevents.ExampleCloudEventDataAccess;
import com.example.stateful_functions.cloudevents.ExampleCloudEventJsonFormat;
import com.example.stateful_functions.protobuf.ExampleProtobuf;
import io.cloudevents.CloudEvent;
import jakarta.inject.Inject;
import org.apache.flink.statefun.sdk.FunctionType;
import org.apache.flink.statefun.sdk.io.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractForwarder implements Forwarder {

    @Inject
    ExampleCloudEventJsonFormat cloudEventJsonFormat;

    @Inject
    protected ExampleCloudEventDataAccess cloudEventDataAccess;

    protected Logger getLogger() {
        return LoggerFactory.getLogger(this.getClass().getName());
    }

    protected void forward(Router.Downstream<ExampleProtobuf.Envelope> downstream, FunctionType functionType, String id, CloudEvent event) {
        String json = cloudEventJsonFormat.serialize(event);
        ExampleProtobuf.Envelope envelope = ExampleProtobuf.Envelope.newBuilder().setPayload(json).build();
        downstream.forward(functionType, id, envelope);
    }

}
