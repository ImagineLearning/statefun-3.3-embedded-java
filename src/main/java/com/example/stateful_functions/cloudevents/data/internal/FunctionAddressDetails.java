package com.example.stateful_functions.cloudevents.data.internal;

public class FunctionAddressDetails {
    private String namespace;
    private String type;
    private String id;

    public FunctionAddressDetails() {
    }

    private FunctionAddressDetails(Builder builder) {
        namespace = builder.namespace;
        type = builder.type;
        id = builder.id;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public static final class Builder {
        private String namespace;
        private String type;
        private String id;

        public Builder() {
        }

        public Builder namespace(String val) {
            namespace = val;
            return this;
        }

        public Builder type(String val) {
            type = val;
            return this;
        }

        public Builder id(String val) {
            id = val;
            return this;
        }

        public FunctionAddressDetails build() {
            return new FunctionAddressDetails(this);
        }
    }
}
