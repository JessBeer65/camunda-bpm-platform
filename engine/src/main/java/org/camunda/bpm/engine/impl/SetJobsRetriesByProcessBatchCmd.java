/*
 * Copyright © 2013-2018 camunda services GmbH and various authors (info@camunda.com)
 *
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
package org.camunda.bpm.engine.impl;

import org.camunda.bpm.engine.impl.cmd.AbstractSetJobsRetriesBatchCmd;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class SetJobsRetriesByProcessBatchCmd extends AbstractSetJobsRetriesBatchCmd {
  protected final List<String> processInstanceIds;
  protected final ProcessInstanceQuery query;

  public SetJobsRetriesByProcessBatchCmd(List<String> processInstanceIds, ProcessInstanceQuery query, int retries) {
    this.processInstanceIds = processInstanceIds;
    this.query = query;
    this.retries = retries;
  }

  protected List<String> collectJobIds(CommandContext commandContext) {
    List<String> collectedJobIds = new ArrayList<String>();
    List<String> collectedProcessInstanceIds = new ArrayList<String>();

    if (query != null) {
      collectedProcessInstanceIds.addAll(((ProcessInstanceQueryImpl)query).listIds());
    }

    if (this.processInstanceIds != null) {
      collectedProcessInstanceIds.addAll(this.processInstanceIds);
    }

    for (String process : collectedProcessInstanceIds) {
      for (Job job : commandContext.getJobManager().findJobsByProcessInstanceId(process)) {
        collectedJobIds.add(job.getId());
      }
    }

    return collectedJobIds;
  }

}
