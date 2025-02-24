/**
 * Copyright (C) 2018-2023 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.expediagroup.streamplatform.streamregistry.repository.kafka;

import com.expediagroup.streamplatform.streamregistry.state.DefaultEventCorrelator;
import com.expediagroup.streamplatform.streamregistry.state.EntityView;
import com.expediagroup.streamplatform.streamregistry.state.EntityViews;
import com.expediagroup.streamplatform.streamregistry.state.EventReceiver;
import com.expediagroup.streamplatform.streamregistry.state.EventSender;
import com.expediagroup.streamplatform.streamregistry.state.internal.EventCorrelator;
import com.expediagroup.streamplatform.streamregistry.state.kafka.KafkaEventReceiver;
import com.expediagroup.streamplatform.streamregistry.state.kafka.KafkaEventSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Configuration
public class KafkaConfiguration {
  @Bean
  EventCorrelator eventCorrelator() {
    return new DefaultEventCorrelator();
  }

  @Bean
  EventSender eventSender(
    @Value("${repository.kafka.bootstrapServers}") String bootstrapServers,
    @Value("${repository.kafka.topic:_streamregistry}") String topic,
    @Value("${repository.kafka.schemaRegistryUrl}") String schemaRegistryUrl,
    @Value("${repository.kafka.propertiesPath:}") String propertiesPath,
    EventCorrelator eventCorrelator
  ) {
    KafkaEventSender.Config config = KafkaEventSender.Config.builder()
      .bootstrapServers(bootstrapServers)
      .topic(topic)
      .schemaRegistryUrl(schemaRegistryUrl)
      .properties(readPropertiesFile(propertiesPath))
      .build();
    return new KafkaEventSender(config, eventCorrelator);
  }

  @Bean
  EventReceiver eventReceiver(
    @Value("${repository.kafka.bootstrapServers}") String bootstrapServers,
    @Value("${repository.kafka.topic:_streamregistry}") String topic,
    @Value("${repository.kafka.groupId:stream-registry}") String groupId,
    @Value("${repository.kafka.schemaRegistryUrl}") String schemaRegistryUrl,
    @Value("${repository.kafka.propertiesPath:}") String propertiesPath,
    EventCorrelator eventCorrelator
  ) {
    KafkaEventReceiver.Config receiverConfig = KafkaEventReceiver.Config.builder()
      .bootstrapServers(bootstrapServers)
      .topic(topic)
      .groupId(groupId)
      .schemaRegistryUrl(schemaRegistryUrl)
      .properties(readPropertiesFile(propertiesPath))
      .build();
    return new KafkaEventReceiver(receiverConfig, eventCorrelator);
  }

  @Bean
  EntityView entityView(EventReceiver eventReceiver) {
    EntityView entityView = EntityViews.defaultEntityView(eventReceiver);
    PurgingEntityViewListener entityViewListener = new PurgingEntityViewListener(entityView);
    entityView.load(entityViewListener)
      .thenAccept(s -> entityViewListener.purgeAll())
      .join();
    return entityView;
  }

  private Map<String, Object> readPropertiesFile(String propertiesPath) {
    Map<String, Object> kafkaConfigs = new HashMap<>();

    if (propertiesPath != null && !propertiesPath.isEmpty()) {
      Properties properties = new Properties();

      try {
        File propertiesFile = new File(propertiesPath);
        properties.load(new FileReader(propertiesFile));
      } catch (FileNotFoundException e) {
        throw new IllegalArgumentException("Could not find properties file: [" + propertiesPath + "].");
      } catch (IOException e) {
        throw new IllegalArgumentException("Could not read properties file: [" + propertiesPath + "].");
      }

      for (Map.Entry<Object, Object> property: properties.entrySet()) {
        kafkaConfigs.put(property.getKey().toString(), property.getValue());
      }
    }

    return kafkaConfigs;
  }
}
