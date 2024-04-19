package com.example.stateful_functions.isolation;

import com.example.stateful_functions.function.product.ProductStateDetails;
import com.example.stateful_functions.function.product.ProductStatefulFunction;
import com.example.stateful_functions.protobuf.ExampleProtobuf;
import com.example.stateful_functions.util.TestMessageLoader;
import org.apache.flink.statefun.testutils.function.FunctionTestHarness;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Iterator;

import static org.junit.Assert.assertNotNull;

/*
 * Test of ProductDetailsStatefulFunction
 *
 */

public class ProductStatefulFunctionTest extends StatefulFunctionIsolationTest {
    private static final Logger LOG = LoggerFactory.getLogger(ProductStatefulFunctionTest.class);

    @Autowired
    ProductStatefulFunction statefulFunction;

    @Autowired
    TestMessageLoader testMessageLoader;

    @Test
    public void run() throws Exception {

        FunctionTestHarness harness = getHarnessForFunction(statefulFunction);

        Iterator<ExampleProtobuf.Envelope> messages = testMessageLoader.loadMessages("product-function-isolated-test-events.jsonl").iterator();

        // 1st Message and Asserts
        harness.invoke(messages.next());
        ProductStateDetails stateModel = statefulFunction.getStateValue();
        assertNotNull(stateModel);
        // TODO: assert on values in the state model
    }


}
