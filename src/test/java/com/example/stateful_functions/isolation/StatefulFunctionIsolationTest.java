package com.example.stateful_functions.isolation;

import com.example.stateful_functions.AbstractStatefulFunctionTest;
import com.example.stateful_functions.function.AbstractStatefulFunction;
import org.apache.flink.statefun.testutils.function.FunctionTestHarness;

public abstract class StatefulFunctionIsolationTest extends AbstractStatefulFunctionTest {

    protected FunctionTestHarness getHarnessForFunction(AbstractStatefulFunction statefulFunction, String functionAddressId) {
        return FunctionTestHarness.test(
                new SingleFunctionProvider(statefulFunction), statefulFunction.getFunctionType(), functionAddressId);
    }

    protected FunctionTestHarness getHarnessForFunction(AbstractStatefulFunction statefulFunction) {
        return getHarnessForFunction(statefulFunction, "isolated-test-function-id");
    }
}
