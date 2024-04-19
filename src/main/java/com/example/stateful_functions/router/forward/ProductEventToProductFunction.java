package com.example.stateful_functions.router.forward;

import com.example.stateful_functions.cloudevents.data.ProductEventDetails;
import com.example.stateful_functions.function.product.ProductStatefulFunction;
import com.example.stateful_functions.protobuf.ExampleProtobuf;
import com.example.stateful_functions.router.AbstractForwarder;
import io.cloudevents.CloudEvent;
import org.apache.flink.statefun.sdk.io.Router;
import org.springframework.stereotype.Component;

@Component
public class ProductEventToProductFunction extends AbstractForwarder {

    @Override
    public boolean accept(CloudEvent event) {
        return "example.product".equals(event.getType());
    }

    @Override
    public void forward(CloudEvent event, Router.Downstream<ExampleProtobuf.Envelope> downstream) {
        ProductEventDetails productEventDetails = cloudEventDataAccess.toProductEventDetails(event);
        forward(downstream, ProductStatefulFunction.FUNCTION_TYPE, productEventDetails.getId(), event);
    }
}
