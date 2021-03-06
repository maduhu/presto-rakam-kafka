/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.kafka;

import com.facebook.presto.spi.Connector;
import com.facebook.presto.spi.ConnectorFactory;
import com.facebook.presto.spi.NodeManager;
import com.facebook.presto.spi.type.TypeManager;
import com.google.common.base.Throwables;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.json.JsonModule;

import java.util.Map;
import java.util.Objects;

/**
 * Creates Kafka Connectors based off connectorId and specific configuration.
 */
public class KafkaConnectorFactory
        implements ConnectorFactory
{
    private final TypeManager typeManager;
    private final NodeManager nodeManager;
    private final Map<String, String> optionalConfig;

    KafkaConnectorFactory(TypeManager typeManager,
            NodeManager nodeManager,
            Map<String, String> optionalConfig)
    {
        this.typeManager = Objects.requireNonNull(typeManager, "typeManager is null");
        this.nodeManager = Objects.requireNonNull(nodeManager, "nodeManager is null");
        this.optionalConfig = Objects.requireNonNull(optionalConfig, "optionalConfig is null");
    }

    @Override
    public String getName()
    {
        return "rakam-kafka";
    }

    @Override
    public Connector create(final String connectorId, Map<String, String> config)
    {
        Objects.requireNonNull(connectorId, "connectorId is null");
        Objects.requireNonNull(config, "config is null");

        try {
            Bootstrap app = new Bootstrap(
                    new JsonModule(),
                    new KafkaConnectorModule(),
                    new MetastoreModule(),
                    binder -> {
                        binder.bindConstant().annotatedWith(Names.named("connectorId")).to(connectorId);
                        binder.bind(TypeManager.class).toInstance(typeManager);
                        binder.bind(NodeManager.class).toInstance(nodeManager);
                    }
            );

            Injector injector = app.strictConfig()
                    .doNotInitializeLogging()
                    .setRequiredConfigurationProperties(config)
                    .setOptionalConfigurationProperties(optionalConfig)
                    .initialize();

            return injector.getInstance(KafkaConnector.class);
        }
        catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
