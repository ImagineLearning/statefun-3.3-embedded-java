/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.stateful_functions;

import com.google.common.annotations.VisibleForTesting;
import org.apache.flink.statefun.sdk.spi.StatefulFunctionModule;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.Map;

public final class StatefulFunctionsModule implements StatefulFunctionModule {

    static ApplicationContext applicationContext;

    @VisibleForTesting
    public static void setApplicationContext(ApplicationContext applicationContext) {
        StatefulFunctionsModule.applicationContext = applicationContext;
    }

    @Override
    public void configure(Map<String, String> globalConfiguration, Binder binder) {
        if (applicationContext == null) {
            applicationContext = new AnnotationConfigApplicationContext(SpringModule.class);
        }
        SpringModule module = applicationContext.getBean(SpringModule.class);
        module.configure(globalConfiguration, binder);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(60_000);
    }

}
