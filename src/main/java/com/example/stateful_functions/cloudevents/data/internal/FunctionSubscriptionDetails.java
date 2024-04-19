package com.example.stateful_functions.cloudevents.data.internal;

public class FunctionSubscriptionDetails {

    private FunctionSubscriptionAction action;
    private FunctionAddressDetails publisher;
    private FunctionAddressDetails subscriber;
    private String correlationId;

    public FunctionSubscriptionDetails() {
    }

    private FunctionSubscriptionDetails(Builder builder) {
        action = builder.action;
        publisher = builder.publisher;
        subscriber = builder.subscriber;
        correlationId = builder.correlationId;
    }

    public FunctionSubscriptionAction getAction() {
        return action;
    }

    public FunctionAddressDetails getPublisher() {
        return publisher;
    }

    public FunctionAddressDetails getSubscriber() {
        return subscriber;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public static final class Builder {
        private FunctionSubscriptionAction action;
        private FunctionAddressDetails publisher;
        private FunctionAddressDetails subscriber;
        private String correlationId;

        public Builder() {
        }

        public Builder action(FunctionSubscriptionAction val) {
            action = val;
            return this;
        }

        public Builder publisher(FunctionAddressDetails val) {
            publisher = val;
            return this;
        }

        public Builder subscriber(FunctionAddressDetails val) {
            subscriber = val;
            return this;
        }

        public Builder correlationId(String val) {
            correlationId = val;
            return this;
        }

        public FunctionSubscriptionDetails build() {
            return new FunctionSubscriptionDetails(this);
        }
    }
}
