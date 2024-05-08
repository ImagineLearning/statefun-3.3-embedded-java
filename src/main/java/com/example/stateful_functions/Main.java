package com.example.stateful_functions;

import org.apache.flink.statefun.flink.core.StatefulFunctionsConfig;
import org.apache.flink.statefun.flink.core.StatefulFunctionsJob;
import org.apache.flink.statefun.flink.core.StatefulFunctionsUniverseProvider;
import org.apache.flink.statefun.flink.core.spi.Modules;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/** Use the main() method here instead of StatefulFunctionJob.main() as described in AWS Managed Flink docs */
public class Main {

    public static void main(String... args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        StatefulFunctionsConfig stateFunConfig = StatefulFunctionsConfig.fromEnvironment(env);
        stateFunConfig.setProvider((StatefulFunctionsUniverseProvider) (classLoader, statefulFunctionsConfig) -> {
            Modules modules = Modules.loadFromClassPath(stateFunConfig);
            return modules.createStatefulFunctionsUniverse();
        });


        StatefulFunctionsJob.main(env, stateFunConfig);
    }

}

