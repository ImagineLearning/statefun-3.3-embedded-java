package com.example.stateful_functions.function.product;


import com.example.stateful_functions.cloudevents.ExampleCloudEventType;
import com.example.stateful_functions.cloudevents.data.ProductAvailability;
import com.example.stateful_functions.cloudevents.data.ProductEventDetails;
import com.example.stateful_functions.cloudevents.data.internal.FunctionSubscriptionAction;
import com.example.stateful_functions.cloudevents.data.internal.FunctionSubscriptionDetails;
import com.example.stateful_functions.function.AbstractStatefulFunction;
import com.example.stateful_functions.function.StatefulFunctionTag;
import com.example.stateful_functions.function.subscriber.FunctionSubscriber;
import com.example.stateful_functions.function.subscriber.FunctionSubscriberUtil;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.jackson.JsonCloudEventData;
import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.statefun.sdk.Context;
import org.apache.flink.statefun.sdk.FunctionType;
import org.apache.flink.statefun.sdk.annotations.Persisted;
import org.apache.flink.statefun.sdk.state.PersistedTable;
import org.apache.flink.statefun.sdk.state.PersistedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@StatefulFunctionTag
public class ProductStatefulFunction extends AbstractStatefulFunction {

    public static final String NAMESPACE = "example";
    public static final String TYPE = "product";
    public static final FunctionType FUNCTION_TYPE = new FunctionType(NAMESPACE, TYPE);

    private static final Logger LOG = LoggerFactory.getLogger(ProductStatefulFunction.class);

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    @Override
    public FunctionType getFunctionType() {
        return FUNCTION_TYPE;
    }

    @Persisted
    private final PersistedValue<ProductStateDetails> state = PersistedValue.of("state", ProductStateDetails.class);

    @Persisted
    private final PersistedTable<String,FunctionSubscriber> subscribers = PersistedTable.of("subscribers", String.class, FunctionSubscriber.class);

    // FOR TESTING ONLY!
    @VisibleForTesting
    public ProductStateDetails getStateValue() {
        return state.get();
    }

    private ProductStateDetails fromProductEventDetails(ProductEventDetails productEventDetails) {
        ProductStateDetails productStateDetails = new ProductStateDetails();
        productStateDetails.setId(productEventDetails.getId());
        productStateDetails.setTitle(productEventDetails.getTitle());
        productStateDetails.setDescription(productEventDetails.getDescription());
        productStateDetails.setPrice(productEventDetails.getPrice());
        productStateDetails.setAvailability(ProductStateAvailability.valueOf(productEventDetails.getAvailability().name()));
        return productStateDetails;
    }

    @Override
    public void handleEvent(Context context, CloudEvent event) {
        switch (event.getType()) {
            case ExampleCloudEventType.PRODUCT_EVENT_TYPE: {
                handleProductEvent(context, event);

                break;
            }
            case ExampleCloudEventType.FUNCTION_SUBSCRIPTION_EVENT_TYPE: {
                handleFunctionSubscriptionEvent(context, event);
                break;
            }
            default: {
                LOG.info("Ignoring event of type {}", event.getType());
            }
        }
    }

    private void handleProductEvent(Context context, CloudEvent event) {
        state.set(fromProductEventDetails(cloudEventDataAccess.toProductEventDetails(event)));
        notifySubscribers(context, event);
    }

    private void handleFunctionSubscriptionEvent(Context context, CloudEvent subscriptionEvent) {
        FunctionSubscriptionDetails subscriptionDetails = cloudEventDataAccess.toFunctionSubscriptionDetails(subscriptionEvent);
        FunctionSubscriber subscriber = FunctionSubscriberUtil.subscriberFromSubscription(subscriptionDetails);
        if (subscriptionDetails.getAction() == FunctionSubscriptionAction.UNSUBSCRIBE) {
            subscribers.remove(subscriber.getSubscriberId());
            return;
        }

        if (subscriptionDetails.getAction() == FunctionSubscriptionAction.SUBSCRIBE) {
            subscribers.set(subscriber.getSubscriberId(), subscriber);
        }

        notifySubscriber(context, subscriber, createEventFromState());
    }

    private void notifySubscriber(Context context, FunctionSubscriber subscriber, CloudEvent productEvent) {
        send(context, new FunctionType(subscriber.getNamespace(), subscriber.getType()), subscriber.getId(), productEvent);
    }

    private void notifySubscribers(Context context, CloudEvent productEvent) {
        for (String key : subscribers.keys()) {
            FunctionSubscriber subscriber = subscribers.get(key);
            notifySubscriber(context, subscriber, productEvent);
        }
    }

    private CloudEvent createEventFromState() {

        ProductStateDetails productStateDetails = state.get();

        ProductEventDetails productDetails = new ProductEventDetails.Builder()
                .id(productStateDetails.getId())
                .title(productStateDetails.getTitle())
                .description(productStateDetails.getDescription())
                .price(productStateDetails.getPrice())
                .availability(ProductAvailability.valueOf(productStateDetails.getAvailability().name()))
                .build();

        CloudEvent productEvent = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(ExampleCloudEventType.PRODUCT_EVENT_TYPE)
                .withSource(URI.create("http://stateful_functions.example.com/product-function"))
                .withData("application/json", JsonCloudEventData.wrap(cloudEventDataAccess.getObjectMapper().valueToTree(productDetails)))
                .withTime(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        return productEvent;
    }
}