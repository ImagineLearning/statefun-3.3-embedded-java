package com.example.stateful_functions.cloudevents;

import com.example.stateful_functions.cloudevents.data.CartProductEventDetails;
import com.example.stateful_functions.cloudevents.data.CartStatusEventDetails;
import com.example.stateful_functions.cloudevents.data.ProductEventDetails;
import com.example.stateful_functions.cloudevents.data.internal.FunctionSubscriptionDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.CloudEventUtils;
import io.cloudevents.core.data.PojoCloudEventData;
import io.cloudevents.jackson.PojoCloudEventDataMapper;
import org.apache.flink.annotation.VisibleForTesting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExampleCloudEventDataAccess {

    @Autowired
    ObjectMapper objectMapper;

    public ExampleCloudEventDataAccess() {
    }

    @VisibleForTesting
    public ExampleCloudEventDataAccess(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public ProductEventDetails toProductEventDetails(CloudEvent event) {
        PojoCloudEventData<ProductEventDetails> cloudEventData = CloudEventUtils.mapData(
                event,
                PojoCloudEventDataMapper.from(objectMapper, ProductEventDetails.class)
        );
        return cloudEventData.getValue();
    }

    public CartProductEventDetails toCartProductEventDetails(CloudEvent event) {
        PojoCloudEventData<CartProductEventDetails> cloudEventData = CloudEventUtils.mapData(
                event,
                PojoCloudEventDataMapper.from(objectMapper, CartProductEventDetails.class)
        );
        return cloudEventData.getValue();
    }

    public CartStatusEventDetails toCartStatusEventDetails(CloudEvent event) {
        PojoCloudEventData<CartStatusEventDetails> cloudEventData = CloudEventUtils.mapData(
                event,
                PojoCloudEventDataMapper.from(objectMapper, CartStatusEventDetails.class)
        );
        return cloudEventData.getValue();
    }

    public FunctionSubscriptionDetails toFunctionSubscriptionDetails(CloudEvent event) {
        PojoCloudEventData<FunctionSubscriptionDetails> cloudEventData = CloudEventUtils.mapData(
                event,
                PojoCloudEventDataMapper.from(objectMapper, FunctionSubscriptionDetails.class)
        );
        return cloudEventData.getValue();
    }

}
