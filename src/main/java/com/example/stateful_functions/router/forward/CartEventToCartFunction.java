package com.example.stateful_functions.router.forward;

import com.example.stateful_functions.cloudevents.data.CartProductEventDetails;
import com.example.stateful_functions.function.cart.CartStatefulFunction;
import com.example.stateful_functions.protobuf.ExampleProtobuf;
import com.example.stateful_functions.router.AbstractForwarder;
import io.cloudevents.CloudEvent;
import org.apache.flink.statefun.sdk.io.Router;
import org.springframework.stereotype.Component;

@Component
public class CartEventToCartFunction extends AbstractForwarder {

    @Override
    public boolean accept(CloudEvent event) {
        return event.getType().startsWith("example.cart-");
    }

    @Override
    public void forward(CloudEvent event, Router.Downstream<ExampleProtobuf.Envelope> downstream) {
        CartProductEventDetails cartProductEventDetails = cloudEventDataAccess.toCartProductEventDetails(event);
        forward(downstream, CartStatefulFunction.FUNCTION_TYPE, cartProductEventDetails.getCartId(), event);
    }
}
