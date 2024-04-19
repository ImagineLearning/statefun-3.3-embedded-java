package com.example.stateful_functions.integration;

import com.example.stateful_functions.cloudevents.ExampleCloudEventDataAccess;
import com.example.stateful_functions.cloudevents.ExampleCloudEventJsonFormat;
import com.example.stateful_functions.cloudevents.ExampleCloudEventType;
import com.example.stateful_functions.cloudevents.data.CartItemStatusDetails;
import com.example.stateful_functions.cloudevents.data.CartStatusEventDetails;
import com.example.stateful_functions.cloudevents.data.ProductAvailability;
import com.example.stateful_functions.protobuf.ExampleProtobuf;
import io.cloudevents.CloudEvent;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;

/*
 * Test the interaction between the product and cart stateful functions.  Here, we are sending product and cart action
 * events through ingress, and checking to see that we get the proper cart status event sent to egress.
 */
public class ProductAndCartFunctionsIntegrationTest extends StatefulFunctionIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(ProductAndCartFunctionsIntegrationTest.class);

    @Autowired
    ExampleCloudEventJsonFormat cloudEventJsonFormat;

    @Autowired
    ExampleCloudEventDataAccess cloudEventDataAccess;


    @Test
    public void run() throws Exception {
        List<ExampleProtobuf.Envelope> envelopesSentToEgress = executeTestHarnessWith("product-cart-integration-test-events.jsonl");
        assertNotNull(envelopesSentToEgress);

        // Find the most recently sent cart status event sent to egress
        CloudEvent cartStatusEvent = envelopesSentToEgress.stream()
                .map(envelope -> cloudEventJsonFormat.deserialize(envelope.getPayload()))
                .filter(cloudEvent -> ExampleCloudEventType.CART_STATUS_EVENT_TYPE.equals(cloudEvent.getType()))
                .reduce((first, second) -> second)
                .orElse(null);

        assertNotNull("cart status event was not sent", cartStatusEvent);
        CartStatusEventDetails cartStatusEventDetails = cloudEventDataAccess.toCartStatusEventDetails(cartStatusEvent);
        assertEquals(1, cartStatusEventDetails.getCartItemStatuses().size());
        CartItemStatusDetails itemStatus = cartStatusEventDetails.getCartItemStatuses().get(0);
        assertEquals(BigDecimal.valueOf(42,0), itemStatus.getOriginPrice());
        assertEquals(BigDecimal.valueOf(4242,2), itemStatus.getCurrentPrice());
        assertEquals(ProductAvailability.IN_STOCK, itemStatus.getAvailability());
    }

}
