package com.example.stateful_functions.cloudevents;

import com.example.stateful_functions.cloudevents.data.CartProductEventDetails;
import com.example.stateful_functions.cloudevents.data.ProductAvailability;
import com.example.stateful_functions.cloudevents.data.ProductEventDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonCloudEventData;
import io.cloudevents.jackson.JsonFormat;
import org.junit.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EventDetailsTest {

    private static final String WIDGET_PRODUCT_ID = "8f8339b5-6810-4c10-ae52-5262eb369f6e";
    private static final String CART_ID = "b0173b21-1609-4a1f-a7c9-0985c79e91e2";

    private byte[] serialize(EventFormat eventFormat, CloudEvent cloudEvent) {
        byte[] serializedEvent = eventFormat.serialize(cloudEvent);

        assertNotNull(serializedEvent);
        assertTrue(serializedEvent.length > 0);

        System.out.println(new String(serializedEvent));
        return serializedEvent;
    }


    @Test
    public void run() {

        ObjectMapper objectMapper = new ObjectMapper();
        EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE);
        ExampleCloudEventDataAccess cloudEventDataAccess = new ExampleCloudEventDataAccess(objectMapper);

        ProductEventDetails productDetails = new ProductEventDetails.Builder()
                .id(WIDGET_PRODUCT_ID)
                .title("Widget")
                .description("Something you should definitely buy!")
                .price(BigDecimal.valueOf(4200L,2))
                .availability(ProductAvailability.IN_STOCK)
                .build();

        CloudEvent productEvent = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(ExampleCloudEventType.PRODUCT_EVENT_TYPE)
                .withSource(URI.create("http://example.com/product-service"))
                .withData("application/json", JsonCloudEventData.wrap(objectMapper.valueToTree(productDetails)))
                .withTime(OffsetDateTime.now(ZoneOffset.UTC))
                .build();


        byte[] serializedEvent = serialize(eventFormat, productEvent);

        CloudEvent deserializedEvent = eventFormat.deserialize(serializedEvent);
        assertNotNull(deserializedEvent);

        ProductEventDetails deserializedProductDetails = cloudEventDataAccess.toProductEventDetails(deserializedEvent);
        assertEquals(productDetails.getId(), deserializedProductDetails.getId());

        CartProductEventDetails cartProductDetails = new CartProductEventDetails.Builder()
                .cartId(CART_ID)
                .productId(productDetails.getId())
                .originPrice(BigDecimal.valueOf(4200, 2))
                .quantity(1)
                .build();

        CloudEvent cartProductEvent = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(ExampleCloudEventType.CART_PRODUCT_EVENT_TYPE)
                .withSource(URI.create("http://example.com/cart-service"))
                .withData("application/json", JsonCloudEventData.wrap(objectMapper.valueToTree(cartProductDetails)))
                .withTime(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        serializedEvent = serialize(eventFormat, cartProductEvent);
        assertNotNull(serializedEvent);

        deserializedEvent = eventFormat.deserialize(serializedEvent);
        assertNotNull(deserializedEvent);

        CartProductEventDetails deserializedCartProductDetails = cloudEventDataAccess.toCartProductEventDetails(deserializedEvent);
        assertEquals(cartProductDetails.getProductId(), deserializedCartProductDetails.getProductId());

        productDetails = new ProductEventDetails.Builder(productDetails)
                .price(BigDecimal.valueOf(4242L,2))
                .build();
        productEvent = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(ExampleCloudEventType.PRODUCT_EVENT_TYPE)
                .withSource(URI.create("http://example.com/product-service"))
                .withData("application/json", JsonCloudEventData.wrap(objectMapper.valueToTree(productDetails)))
                .withTime(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        serialize(eventFormat, productEvent);
    }
}
