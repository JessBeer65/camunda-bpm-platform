/*
 * Copyright © 2013-2019 camunda services GmbH and various authors (info@camunda.com)
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
package org.camunda.bpm.engine.impl.cmd;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.bpmn.helper.BpmnProperties;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParse;
import org.camunda.bpm.engine.impl.cfg.CommandChecker;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.jobexecutor.TimerCatchIntermediateEventJobHandler;
import org.camunda.bpm.engine.impl.jobexecutor.TimerDeclarationImpl;
import org.camunda.bpm.engine.impl.jobexecutor.TimerExecuteNestedActivityJobHandler;
import org.camunda.bpm.engine.impl.jobexecutor.TimerStartEventJobHandler;
import org.camunda.bpm.engine.impl.jobexecutor.TimerStartEventSubprocessJobHandler;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.JobEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TimerEntity;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;


/**
 * @author Tobias Metzke
 */
public class RecalculateJobDuedateCmd implements Command<Void>, Serializable {

  private static final long serialVersionUID = 1L;

  private final String jobId;

  public RecalculateJobDuedateCmd(String jobId) {
    if (jobId == null || jobId.length() < 1) {
      throw new ProcessEngineException("The job id is mandatory, but '" + jobId + "' has been provided.");
    }
    this.jobId = jobId;
  }

  public Void execute(CommandContext commandContext) {
    JobEntity job = commandContext.getJobManager().findJobById(jobId);
    if (job == null) {
      throw new ProcessEngineException("No job found with id '" + jobId + "'.");
    }

    // allow timer jobs only
    checkJobType(job);
    
    TimerDeclarationImpl timerDeclaration = findTimerDeclaration(commandContext, job);
    
    for(CommandChecker checker : commandContext.getProcessEngineConfiguration().getCommandCheckers()) {
      checker.checkUpdateJob(job);
    }

    TimerEntity timer = (TimerEntity) job;
    timerDeclaration.resolveAndSetDuedate(timer.getExecution(), timer);
    return null;
  }

  protected void checkJobType(JobEntity job) {
    String type = job.getJobHandlerType();
    if (!(TimerExecuteNestedActivityJobHandler.TYPE.equals(type) || 
        TimerCatchIntermediateEventJobHandler.TYPE.equals(type) || 
        TimerStartEventJobHandler.TYPE.equals(type) || 
        TimerStartEventSubprocessJobHandler.TYPE.equals(type)) ||
        !(job instanceof TimerEntity)) {
      throw new ProcessEngineException("No recalculatable job found for job id '" + jobId + "'."); 
    }
  }
  
  protected TimerDeclarationImpl findTimerDeclaration(CommandContext commandContext, JobEntity job) {
    TimerDeclarationImpl timerDeclaration = null;
    if (job.getExecutionId() != null) {
      // boundary or intermediate or subprocess start event
      timerDeclaration = findTimerDeclarationForActivity(commandContext, job);
    } else {
      // process instance start event
      timerDeclaration = findTimerDeclarationForProcessStartEvent(commandContext, job);
    }
    
    if (timerDeclaration == null) {
      throw new ProcessEngineException("No timer declaration found for job id '" + jobId + "'.");
    }
    return timerDeclaration;
  }

  protected TimerDeclarationImpl findTimerDeclarationForActivity(CommandContext commandContext, JobEntity job) {
    TimerDeclarationImpl timerDeclaration;
    ExecutionEntity execution = commandContext.getExecutionManager().findExecutionById(job.getExecutionId());
    if (execution == null) {
      throw new ProcessEngineException("No execution found with id '" + job.getExecutionId() + "' for job id '" + jobId + "'.");
    }
    ActivityImpl activity = execution.getProcessDefinition().findActivity(job.getActivityId());
    if (activity == null) {
      throw new ProcessEngineException("No activity with id '" + job.getActivityId() + "' found for job id '" + jobId + "'.");
    }
    Map<String, TimerDeclarationImpl> timerDeclarations = activity.getEventScope().getProperties().get(BpmnProperties.TIMER_DECLARATIONS);
    if (timerDeclarations == null || timerDeclarations.isEmpty()) {
      throw new ProcessEngineException("No timer declarations found in activity event scope with activity id '" + job.getActivityId() + "' for job id '" + jobId + "'.");
    }
    timerDeclaration = timerDeclarations.get(job.getActivityId());
    return timerDeclaration;
  }
  
  protected TimerDeclarationImpl findTimerDeclarationForProcessStartEvent(CommandContext commandContext, JobEntity job) {
    /*
     * XXX: fetching from cache yields the properties as well, fetching from DB doesn't 
     *      => is fetching from cache always appropriate here?
     *      => should we re-parse the definition again when from DB? how to correlate to our job (if there are e.g. multiple start timer events?)?
     */
    ProcessDefinitionEntity processDefinition = commandContext.getProcessEngineConfiguration().getDeploymentCache().findProcessDefinitionFromCache(job.getProcessDefinitionId());
    if (processDefinition == null) {
      processDefinition = commandContext.getProcessDefinitionManager().findLatestDefinitionByKeyAndTenantId(job.getProcessDefinitionKey(), job.getTenantId());
    }
    if (processDefinition == null) {
      throw new ProcessEngineException("No process definition found with id '" + job.getProcessDefinitionId() + "' for job id '" + jobId + "'.");
    }
    @SuppressWarnings("unchecked")
    List<TimerDeclarationImpl> timerDeclarations = (List<TimerDeclarationImpl>) processDefinition.getProperty(BpmnParse.PROPERTYNAME_START_TIMER);
    if (timerDeclarations == null || timerDeclarations.isEmpty()) {
      throw new ProcessEngineException("No timer declarations found in process definition with id '" + job.getProcessDefinitionId() + "' for job id '" + jobId + "'.");
    }
    for (TimerDeclarationImpl timerDeclarationCandidate : timerDeclarations) {
      if (timerDeclarationCandidate.getJobDefinitionId().equals(job.getJobDefinitionId())) {
        return timerDeclarationCandidate;
      }
    }
    return null;
  }
}
