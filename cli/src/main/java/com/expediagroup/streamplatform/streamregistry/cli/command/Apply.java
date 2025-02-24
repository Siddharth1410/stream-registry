/**
 * Copyright (C) 2018-2021 Expedia, Inc.
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
package com.expediagroup.streamplatform.streamregistry.cli.command;

import static com.expediagroup.streamplatform.streamregistry.state.model.event.Event.specification;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import com.expediagroup.streamplatform.streamregistry.cli.action.GraphQLEventSenderAction;
import com.expediagroup.streamplatform.streamregistry.cli.option.EntityOptions;
import com.expediagroup.streamplatform.streamregistry.cli.option.ObjectNodeConverter;
import com.expediagroup.streamplatform.streamregistry.cli.option.TagConverter;
import com.expediagroup.streamplatform.streamregistry.state.model.Entity.ConsumerBindingKey;
import com.expediagroup.streamplatform.streamregistry.state.model.Entity.ConsumerKey;
import com.expediagroup.streamplatform.streamregistry.state.model.Entity.DomainKey;
import com.expediagroup.streamplatform.streamregistry.state.model.Entity.InfrastructureKey;
import com.expediagroup.streamplatform.streamregistry.state.model.Entity.Key;
import com.expediagroup.streamplatform.streamregistry.state.model.Entity.ProducerBindingKey;
import com.expediagroup.streamplatform.streamregistry.state.model.Entity.ProducerKey;
import com.expediagroup.streamplatform.streamregistry.state.model.Entity.SchemaKey;
import com.expediagroup.streamplatform.streamregistry.state.model.Entity.StreamBindingKey;
import com.expediagroup.streamplatform.streamregistry.state.model.Entity.StreamKey;
import com.expediagroup.streamplatform.streamregistry.state.model.Entity.ZoneKey;
import com.expediagroup.streamplatform.streamregistry.state.model.event.Event;
import com.expediagroup.streamplatform.streamregistry.state.model.specification.DefaultSpecification;
import com.expediagroup.streamplatform.streamregistry.state.model.specification.Principal;
import com.expediagroup.streamplatform.streamregistry.state.model.specification.Specification;
import com.expediagroup.streamplatform.streamregistry.state.model.specification.StreamSpecification;
import com.expediagroup.streamplatform.streamregistry.state.model.specification.Tag;

@Command(name = "apply", subcommands = {
    Apply.Domain.class,
    Apply.Schema.class,
    Apply.Stream.class,
    Apply.Zone.class,
    Apply.Infrastructure.class,
    Apply.Producer.class,
    Apply.Consumer.class,
    Apply.StreamBinding.class,
    Apply.ProducerBinding.class,
    Apply.ConsumerBinding.class
})
public class Apply {
  static abstract class Base<K extends Key<S>, S extends Specification> extends GraphQLEventSenderAction {
    static final ObjectMapper mapper = new ObjectMapper();

    @Option(names = "--description", required = true)
    protected String description;
    @Option(names = "--tag", converter = TagConverter.class)
    protected List<Tag> tags = Collections.emptyList();
    @Option(names = "--type", required = true)
    protected String type;
    @Option(names = "--configuration", required = true, converter = ObjectNodeConverter.class)
    protected ObjectNode configuration;
    @Option(names = "--security", converter = ObjectNodeConverter.class)
    protected ObjectNode security = mapper.createObjectNode();
    @Option(names = "--function", defaultValue = "")
    protected String function;

    @Override
    public List<Event<?, ?>> events() {
      return Collections.singletonList(specification(getEntityOptions().key(), getSpecification()));
    }

    public Map<String, List<Principal>> convertSecurityMap(ObjectNode security) {
      return mapper.convertValue(security, new TypeReference<Map<String, List<String>>>(){})
        .entrySet().stream()
        .collect(Collectors.toMap(
          Map.Entry::getKey,
          entry -> entry.getValue().stream().map(Principal::new).collect(Collectors.toList())
        ));
    }

    protected abstract EntityOptions<K, S> getEntityOptions();

    protected abstract S getSpecification();
  }

  static abstract class Default<K extends Key<DefaultSpecification>> extends Base<K, DefaultSpecification> {
    @Override
    protected DefaultSpecification getSpecification() {
      return new DefaultSpecification(description, tags, type, configuration, convertSecurityMap(security), function);
    }
  }

  @Command(name = "domain")
  static class Domain extends Default<DomainKey> {
    @Mixin @Getter EntityOptions.Domain entityOptions;
  }

  @Command(name = "schema")
  static class Schema extends Default<SchemaKey> {
    @Mixin @Getter EntityOptions.Schema entityOptions;
  }

  @Command(name = "stream")
  static class Stream extends Base<StreamKey, StreamSpecification> {
    @Mixin @Getter EntityOptions.ApplyStream entityOptions;

    @Override
    protected StreamSpecification getSpecification() {
      return new StreamSpecification(description, tags, type, configuration, convertSecurityMap(security), function, entityOptions.schemaKey());
    }
  }

  @Command(name = "zone")
  static class Zone extends Default<ZoneKey> {
    @Mixin @Getter EntityOptions.Zone entityOptions;
  }

  @Command(name = "infrastructure")
  static class Infrastructure extends Default<InfrastructureKey> {
    @Mixin @Getter EntityOptions.Infrastructure entityOptions;
  }

  @Command(name = "producer")
  static class Producer extends Default<ProducerKey> {
    @Mixin @Getter EntityOptions.Producer entityOptions;
  }

  @Command(name = "consumer")
  static class Consumer extends Default<ConsumerKey> {
    @Mixin @Getter EntityOptions.Consumer entityOptions;
  }

  @Command(name = "streamBinding")
  static class StreamBinding extends Default<StreamBindingKey> {
    @Mixin @Getter EntityOptions.StreamBinding entityOptions;
  }

  @Command(name = "producerBinding")
  static class ProducerBinding extends Default<ProducerBindingKey> {
    @Mixin @Getter EntityOptions.ProducerBinding entityOptions;
  }

  @Command(name = "consumerBinding")
  static class ConsumerBinding extends Default<ConsumerBindingKey> {
    @Mixin @Getter EntityOptions.ConsumerBinding entityOptions;
  }
}
