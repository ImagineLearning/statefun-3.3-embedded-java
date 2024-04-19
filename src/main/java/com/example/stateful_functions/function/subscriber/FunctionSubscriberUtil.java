package com.example.stateful_functions.function.subscriber;

import com.example.stateful_functions.cloudevents.data.internal.FunctionAddressDetails;
import com.example.stateful_functions.cloudevents.data.internal.FunctionSubscriptionDetails;

import java.time.Clock;
import java.time.LocalDateTime;

public class FunctionSubscriberUtil {

    // Transform the subscription details from an event, into the FunctionSubscriber for persistent state.
    public static FunctionSubscriber subscriberFromSubscription(FunctionSubscriptionDetails subscriptionDetails) {
        FunctionAddressDetails subscriberAddress = subscriptionDetails.getSubscriber();
        FunctionSubscriber subscriber = new FunctionSubscriber();
        subscriber.setNamespace(subscriberAddress.getNamespace());
        subscriber.setType(subscriberAddress.getType());
        subscriber.setId(subscriberAddress.getId());
        subscriber.setCorrelationId(subscriptionDetails.getCorrelationId());
        subscriber.setDateCreated(LocalDateTime.now(Clock.systemUTC()));
        return subscriber;
    }

}
