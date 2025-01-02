package com.example.stateful_functions.function.cart;


import com.example.stateful_functions.Configuration;
import com.example.stateful_functions.cloudevents.ExampleCloudEventType;
import com.example.stateful_functions.cloudevents.data.CartItemStatusDetails;
import com.example.stateful_functions.cloudevents.data.CartProductEventDetails;
import com.example.stateful_functions.cloudevents.data.CartStatusEventDetails;
import com.example.stateful_functions.cloudevents.data.ProductAvailability;
import com.example.stateful_functions.cloudevents.data.ProductEventDetails;
import com.example.stateful_functions.cloudevents.data.internal.FunctionSubscriptionAction;
import com.example.stateful_functions.function.AbstractStatefulFunction;
import com.example.stateful_functions.function.StatefunFunction;
import com.example.stateful_functions.function.product.ProductStateAvailability;
import com.example.stateful_functions.function.product.ProductStatefulFunction;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.jackson.JsonCloudEventData;
import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.statefun.sdk.Context;
import org.apache.flink.statefun.sdk.FunctionType;
import org.apache.flink.statefun.sdk.annotations.Persisted;
import org.apache.flink.statefun.sdk.state.PersistedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@StatefunFunction
public class CartStatefulFunction extends AbstractStatefulFunction {

    public static final String NAMESPACE = "example";
    public static final String TYPE = "cart";
    public static final FunctionType FUNCTION_TYPE = new FunctionType(NAMESPACE, TYPE);

    private static final Logger LOG = LoggerFactory.getLogger(CartStatefulFunction.class);

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    @Override
    public FunctionType getFunctionType() {
        return FUNCTION_TYPE;
    }

    @Persisted
    private final PersistedValue<CartStateDetails> state = PersistedValue.of("state", CartStateDetails.class);


    // FOR TESTING ONLY!
    @VisibleForTesting
    public CartStateDetails getStateValue() {
        return state.get();
    }

    @Override
    public void handleEvent(Context context, CloudEvent event) {
        switch (event.getType()) {
            case ExampleCloudEventType.CART_PRODUCT_EVENT_TYPE: {
                handleCartProductEvent(context, event);
                break;
            }
            case ExampleCloudEventType.PRODUCT_EVENT_TYPE: {
                handleProductEvent(context, event);
                break;
            }
        }
    }

    private void productSubscription(Context context, String cartId, String productId, FunctionSubscriptionAction action) {
        // Send a subscribe/query/unsubscribe message to the ProductStatefulFunction
        subscribe(context, ProductStatefulFunction.FUNCTION_TYPE, productId, CartStatefulFunction.FUNCTION_TYPE, cartId, action,null);
    }

    private void handleCartProductEvent(Context context, CloudEvent event) {
        CartProductEventDetails cartProduct = cloudEventDataAccess.toCartProductEventDetails(event);
        CartStateDetails cartState = state.get();
        if (cartState == null) {
            LOG.info("Creating state for {}", context.self().id());
            cartState = new CartStateDetails(cartProduct.getCartId());
        }
        else {
            LOG.info("Updating state for {}", context.self().id());
        }

        CartItemStateDetails cartItem = cartState.getItems().get(cartProduct.getProductId());

        final int startingItemQuantity;
        final int resultingItemQuantity;

        if (cartItem == null) {
            resultingItemQuantity = cartProduct.getQuantity();
        }
        else {
            startingItemQuantity = cartItem.getQuantity();
            resultingItemQuantity = Math.max(0, startingItemQuantity + cartItem.getQuantity());
        }

        if (cartItem == null && resultingItemQuantity > 0) {
            cartItem = new CartItemStateDetails();
            cartItem.setProductId(cartProduct.getProductId());
        }
        cartItem.setQuantity(resultingItemQuantity);
        cartItem.setOriginPrice(cartProduct.getOriginPrice());

        if (cartItem.getQuantity() > 0 && !cartItem.isSubscribedToProduct()) {
            // subscribe to the product for price/availability updates
            productSubscription(context, cartState.getId(), cartItem.getProductId(), FunctionSubscriptionAction.SUBSCRIBE);
            cartItem.setSubscribedToProduct(true);
        }
        if (cartItem.getQuantity() == 0) {
            if (cartItem.isSubscribedToProduct()) {
                // Unsubscribe from the product
                productSubscription(context, cartState.getId(), cartItem.getProductId(), FunctionSubscriptionAction.UNSUBSCRIBE);
                cartItem.setSubscribedToProduct(false);
            }
            cartState.getItems().remove(cartItem.getProductId());
        }
        else {
            cartState.getItems().put(cartItem.getProductId(), cartItem);
        }

        if (cartState.getItems().size() == 0) {
            state.clear();
        }
        else {
            state.set(cartState);
        }
    }

    private void handleProductEvent(Context context, CloudEvent event) {
        CartStateDetails cartState = state.get();
        if (cartState == null) {
            LOG.info("Nonexistent state for {}", context.self().id());
            // Nothing to do
            return;
        }
        else {
            LOG.info("Updating state for {}", context.self().id());
        }
        ProductEventDetails productDetails = cloudEventDataAccess.toProductEventDetails(event);

        CartItemStateDetails cartItem = cartState.getItems().get(productDetails.getId());
        if (cartItem == null) {
            // Nothing to do
            return;
        }

        cartItem.setPrice(productDetails.getPrice());
        cartItem.setAvailability(ProductStateAvailability.valueOf(productDetails.getAvailability().name()));

        state.set(cartState);

        egressCartStatus(context, cartState);
    }

    private void egressCartStatus(Context context, CartStateDetails cartState) {
        CartStatusEventDetails cartStatusEventDetails = new CartStatusEventDetails(cartState.getId());
        for (CartItemStateDetails itemStateDetails : cartState.getItems().values()) {
            cartStatusEventDetails.getCartItemStatuses().add(
                    new CartItemStatusDetails.Builder()
                            .productId(itemStateDetails.getProductId())
                            .quantity(itemStateDetails.getQuantity())
                            .originPrice(itemStateDetails.getOriginPrice())
                            .currentPrice(itemStateDetails.getPrice())
                            .availability(ProductAvailability.valueOf(itemStateDetails.getAvailability().name()))
                            .version(Configuration.APP_VERSION)
                            .build()
            );

        }
        CloudEvent cartStatusEvent = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(ExampleCloudEventType.CART_STATUS_EVENT_TYPE)
                .withSource(URI.create("http://stateful_functions.example.com/cart-function"))
                .withData("application/json", JsonCloudEventData.wrap(cloudEventDataAccess.getObjectMapper().valueToTree(cartStatusEventDetails)))
                .withTime(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        LOG.info("Publishing cart status event to egress: {}", cloudEventJsonFormat.serialize(cartStatusEvent));
        egressEvent(context, cartStatusEvent, cartState.getId());
    }
}