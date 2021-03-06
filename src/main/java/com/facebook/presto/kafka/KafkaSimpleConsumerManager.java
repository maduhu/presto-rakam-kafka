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

import com.facebook.presto.spi.HostAddress;
import com.facebook.presto.spi.NodeManager;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.primitives.Ints;
import io.airlift.log.Logger;
import kafka.javaapi.consumer.SimpleConsumer;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static java.lang.String.format;

/**
 * Manages connections to the Kafka nodes. A worker may connect to multiple Kafka nodes depending on the segments and partitions
 * it needs to process. According to the Kafka source code, a Kafka {@link kafka.javaapi.consumer.SimpleConsumer} is thread-safe.
 */
public class KafkaSimpleConsumerManager
{
    private static final Logger log = Logger.get(KafkaSimpleConsumerManager.class);

    private final LoadingCache<HostAddress, SimpleConsumer> consumerCache;

    private final String connectorId;
    private final KafkaConnectorConfig kafkaConnectorConfig;
    private final NodeManager nodeManager;

    @Inject
    KafkaSimpleConsumerManager(@Named("connectorId") String connectorId,
            KafkaConnectorConfig kafkaConnectorConfig,
            NodeManager nodeManager)
    {
        this.connectorId = Objects.requireNonNull(connectorId, "connectorId is null");
        this.kafkaConnectorConfig = Objects.requireNonNull(kafkaConnectorConfig, "kafkaConfig is null");
        this.nodeManager = Objects.requireNonNull(nodeManager, "nodeManager is null");

        this.consumerCache = CacheBuilder.newBuilder().build(new SimpleConsumerCacheLoader());
    }

    @PreDestroy
    public void tearDown()
    {
        for (Map.Entry<HostAddress, SimpleConsumer> entry : consumerCache.asMap().entrySet()) {
            try {
                entry.getValue().close();
            }
            catch (Exception e) {
                log.warn(e, "While closing consumer %s:", entry.getKey());
            }
        }
    }

    public SimpleConsumer getConsumer(HostAddress host)
    {
        Objects.requireNonNull(host, "host is null");
        try {
            return consumerCache.get(host);
        }
        catch (ExecutionException e) {
            throw Throwables.propagate(e.getCause());
        }
    }

    public void refreshConsumer(HostAddress host)
    {
        Objects.requireNonNull(host, "host is null");
        consumerCache.refresh(host);
    }

    private class SimpleConsumerCacheLoader
            extends CacheLoader<HostAddress, SimpleConsumer>
    {
        @Override
        public SimpleConsumer load(HostAddress host)
                throws Exception
        {
            log.info("Creating new Consumer for %s", host);
            return new SimpleConsumer(host.getHostText(),
                    host.getPort(),
                    Ints.checkedCast(kafkaConnectorConfig.getKafkaConnectTimeout().toMillis()),
                    Ints.checkedCast(kafkaConnectorConfig.getKafkaBufferSize().toBytes()),
                    format("presto-kafka-%s-%s", connectorId, nodeManager.getCurrentNode().getNodeIdentifier()));
        }
    }
}
