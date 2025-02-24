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
package com.expediagroup.streamplatform.streamregistry.core.services;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import lombok.RequiredArgsConstructor;
import lombok.val;

import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import com.expediagroup.streamplatform.streamregistry.core.handlers.HandlerService;
import com.expediagroup.streamplatform.streamregistry.core.validators.ProcessBindingValidator;
import com.expediagroup.streamplatform.streamregistry.core.validators.ValidationException;
import com.expediagroup.streamplatform.streamregistry.core.views.ConsumerView;
import com.expediagroup.streamplatform.streamregistry.core.views.ProcessBindingView;
import com.expediagroup.streamplatform.streamregistry.core.views.ProducerView;
import com.expediagroup.streamplatform.streamregistry.model.ProcessBinding;
import com.expediagroup.streamplatform.streamregistry.model.Status;
import com.expediagroup.streamplatform.streamregistry.model.keys.ConsumerKey;
import com.expediagroup.streamplatform.streamregistry.model.keys.ProcessBindingKey;
import com.expediagroup.streamplatform.streamregistry.model.keys.ProducerKey;
import com.expediagroup.streamplatform.streamregistry.repository.ProcessBindingRepository;

@Component
@RequiredArgsConstructor
public class ProcessBindingService {
  private final ProcessBindingView processBindingView;
  private final HandlerService handlerService;
  private final ProcessBindingValidator processBindingValidator;
  private final ProcessBindingRepository processBindingRepository;
  private final ConsumerView consumerView;
  private final ConsumerService consumerService;
  private final ProducerView producerView;
  private final ProducerService producerService;

  @PreAuthorize("hasPermission(#processBinding, 'CREATE')")
  public Optional<ProcessBinding> create(ProcessBinding processBinding) throws ValidationException {
    if (processBindingView.exists(processBinding.getKey())) {
      throw new ValidationException("Can't create " + processBinding.getKey() + " because it already exists");
    }
    processBindingValidator.validateForCreate(processBinding);
    processBinding.setSpecification(handlerService.handleInsert(processBinding));
    return save(processBinding);
  }

  @PreAuthorize("hasPermission(#processBinding, 'UPDATE')")
  public Optional<ProcessBinding> update(ProcessBinding processBinding) throws ValidationException {
    val existing = processBindingView.get(processBinding.getKey());
    if (!existing.isPresent()) {
      throw new ValidationException("Can't update " + processBinding.getKey() + " because it doesn't exist");
    }
    processBindingValidator.validateForUpdate(processBinding, existing.get());
    processBinding.setSpecification(handlerService.handleUpdate(processBinding, existing.get()));
    return save(processBinding);
  }

  @PreAuthorize("hasPermission(#processBinding, 'UPDATE_STATUS')")
  public Optional<ProcessBinding> updateStatus(ProcessBinding processBinding, Status status) {
    processBinding.setStatus(status);
    return save(processBinding);
  }

  private Optional<ProcessBinding> save(ProcessBinding processBinding) {
    return Optional.ofNullable(processBindingRepository.save(processBinding));
  }

  @PostAuthorize("returnObject.isPresent() ? hasPermission(returnObject, 'READ') : true")
  public Optional<ProcessBinding> get(ProcessBindingKey key) {
    return processBindingView.get(key);
  }

  @PostFilter("hasPermission(filterObject, 'READ')")
  public List<ProcessBinding> findAll(Predicate<ProcessBinding> filter) {
    return processBindingView.findAll(filter).collect(toList());
  }

  @PreAuthorize("hasPermission(#processBinding, 'DELETE')")
  public void delete(ProcessBinding processBinding) {
    handlerService.handleDelete(processBinding);

    // Remove producers AFTER consumers - a consumer is nothing without a producer
    processBinding.getInputs().forEach(input ->
      consumerView
        .findAll(c -> c.getKey().equals(new ConsumerKey(
          input.getStreamBindingKey().getStreamDomain(),
          input.getStreamBindingKey().getStreamName(),
          input.getStreamBindingKey().getStreamVersion(),
          input.getStreamBindingKey().getInfrastructureZone(),
          processBinding.getKey().getProcessName()
        )))
        .forEach(consumerService::delete)
    );

    // We have no consumers so we can now remove the producers
    processBinding.getOutputs().forEach(output ->
      producerView
        .findAll(c -> c.getKey().equals(new ProducerKey(
          output.getStreamBindingKey().getStreamDomain(),
          output.getStreamBindingKey().getStreamName(),
          output.getStreamBindingKey().getStreamVersion(),
          output.getStreamBindingKey().getInfrastructureZone(),
          processBinding.getKey().getProcessName()
        )))
        .forEach(producerService::delete)
    );

    processBindingRepository.delete(processBinding);
  }
}
