package com.example.stateful_functions.function;

import com.example.stateful_functions.cloudevents.ExampleCloudEventDataAccess;
import com.example.stateful_functions.cloudevents.ExampleCloudEventJsonFormat;
import com.example.stateful_functions.cloudevents.ExampleCloudEventType;
import com.example.stateful_functions.cloudevents.data.internal.FunctionAddressDetails;
import com.example.stateful_functions.cloudevents.data.internal.FunctionSubscriptionAction;
import com.example.stateful_functions.cloudevents.data.internal.FunctionSubscriptionDetails;
import com.example.stateful_functions.egress.EgressSpecs;
import com.example.stateful_functions.protobuf.ExampleProtobuf;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.jackson.JsonCloudEventData;
import jakarta.inject.Inject;
import org.apache.flink.statefun.sdk.Context;
import org.apache.flink.statefun.sdk.FunctionType;
import org.apache.flink.statefun.sdk.StatefulFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public abstract class AbstractStatefulFunction implements StatefulFunction {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractStatefulFunction.class);

    public abstract FunctionType getFunctionType();

    @Inject
    protected ExampleCloudEventJsonFormat cloudEventJsonFormat;

    @Inject
    protected ExampleCloudEventDataAccess cloudEventDataAccess;

    @Override
    public final void invoke(Context context, Object o) {
        if (o instanceof ExampleProtobuf.Envelope) {

            CloudEvent event = cloudEventJsonFormat.deserialize(((ExampleProtobuf.Envelope)o).getPayload());

            try {
                getLogger().info("handleEvent {}, addressed-to: {}, eventId: {}, type: {}",
                        this.getClass().getSimpleName(), context.self(), event.getId(),
                        event.getType());
                handleEvent(context, event);
            }
            catch (Throwable t) {
                try {
                    LOG.error("Exception thrown by {}, message: {}, addressed-to: {}, eventId: {}, type: {}",
                            this.getClass().getSimpleName(), t.getMessage(), context.self(), event.getId(),
                            event.getType(), t);
                }
                catch (Throwable x) {
                    getLogger().error(t.getMessage(), t);
                }
            }
        }
    }

    protected abstract Logger getLogger();

    protected abstract void handleEvent(Context context, CloudEvent event);


    /** Send String payload to another function addressed by functionType and id. */
    protected void send(Context context, FunctionType functionType, String id, String payload) {
        ExampleProtobuf.Envelope protobufMessage = ExampleProtobuf.Envelope.newBuilder()
                .setPayload(payload)
                .build();
        context.send(functionType, id, protobufMessage);
    }

    /** Send a CloudEvent to another function addressed by functionType and id. */
    protected void send(Context context, FunctionType functionType, String id, CloudEvent event) {
        send(context, functionType, id, cloudEventJsonFormat.serialize(event));
    }

    /** Subscribe to another function.  The destination (publisher) function must explicitly support subscriptions or queries. */
    protected void subscribe(Context context,
                             FunctionType publisherType,
                             String publisherId,
                             FunctionType subscriberType,
                             String subscriberId,
                             FunctionSubscriptionAction action,
                             String correlationId) {

        FunctionSubscriptionDetails.Builder builder = new FunctionSubscriptionDetails.Builder()
                .publisher(new FunctionAddressDetails.Builder()
                        .namespace(publisherType.namespace())
                        .type(publisherType.name())
                        .id(publisherId)
                        .build())
                .subscriber(new FunctionAddressDetails.Builder()
                        .namespace(subscriberType.namespace())
                        .type(subscriberType.name())
                        .id(subscriberId)
                        .build())
                .action(action)
                .correlationId(correlationId);

        FunctionSubscriptionDetails functionSubscriptionDetails = builder.build();

        CloudEvent subscriptionEvent = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(ExampleCloudEventType.FUNCTION_SUBSCRIPTION_EVENT_TYPE)
                .withSource(URI.create(String.format("http://stateful_functions.example.com/%s.%s/%s",
                        subscriberType.namespace(), subscriberType.name(), subscriberId)))
                .withData("application/json", JsonCloudEventData.wrap(
                        cloudEventDataAccess.getObjectMapper().valueToTree(functionSubscriptionDetails)))
                .withTime(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        send(context, publisherType, publisherId, cloudEventJsonFormat.serialize(subscriptionEvent));
    }

    /** Egress an event payload */
    protected void egressEvent(Context context, CloudEvent event, String partitionKey) {
        ExampleProtobuf.Envelope protobufEnvelope = ExampleProtobuf.Envelope.newBuilder()
                .setPayload(cloudEventJsonFormat.serialize(event))
                .setPartitionKey(partitionKey)
                .build();
        context.send(EgressSpecs.ID, protobufEnvelope);
    }
}
