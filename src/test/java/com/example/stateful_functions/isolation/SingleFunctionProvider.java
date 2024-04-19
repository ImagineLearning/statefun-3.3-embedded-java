package com.example.stateful_functions.isolation;

import com.example.stateful_functions.function.AbstractStatefulFunction;
import org.apache.flink.statefun.sdk.FunctionType;
import org.apache.flink.statefun.sdk.StatefulFunction;
import org.apache.flink.statefun.sdk.StatefulFunctionProvider;

/**
 * SingleFunctionProvider is simple. For unit tests, create a StatefulFunction, FunctionTestHarness,
 * and a SingleFunctionProvider to pass the StatefulFunction to the FunctionTestHarness.  After
 * invoking whatever messages to the StatefulFunction through the FunctionTestHarness, you can get
 * the internal state of the StatefulFunction to make assertions.
 *
 *
 * public void test() {
 *     MyStatefulFunction statefulFunction = new MyStatefulFunction(...);
 *     FunctionTestHarness harness = FunctionTestHarness.test(
 *          new SingleFunctionProvider(statefulFunction), statefulFunction.FUNCTION_TYPE, "single-id-ignored");
 *     ...
 *     harness.invoke(message);
 *     ...
 *     MyModel model = statefulFunction.getValueState();
 *     Assert(...)
 * }
 */

public class SingleFunctionProvider implements StatefulFunctionProvider {

    AbstractStatefulFunction function;

    SingleFunctionProvider(AbstractStatefulFunction function) {
        this.function = function;
    }

    public StatefulFunction functionOfType(FunctionType functionType) {
        return this.function;
    }
}

