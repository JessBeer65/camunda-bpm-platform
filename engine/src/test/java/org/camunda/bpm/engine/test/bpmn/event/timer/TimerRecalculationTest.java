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
package org.camunda.bpm.engine.test.bpmn.event.timer;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.persistence.entity.JobEntity;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.JobQuery;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.junit.After;


/**
 * Test timer recalculation
 * 
 * @author Tobias Metzke
 */

public class TimerRecalculationTest extends PluggableProcessEngineTestCase {
	
  private Set<String> jobIds = new HashSet<>();
  
  @After
  public void tearDown() {
    clearMeterLog();

    for (String jobId : jobIds) {
      clearJobLog(jobId);
      clearJob(jobId);
    }
    
    jobIds = new HashSet<>();
  }
	  
  public void testUnknownId() {
    try {
      // when
      managementService.recalculateJobDuedate("unknownID");
      fail("The recalculation with an unknown job ID should not be possible");
    } catch (ProcessEngineException pe) {
      // then
      assertTextPresent("No job found with id '" + "unknownID", pe.getMessage());
    }
  }
  
  public void testEmptyId() {
    try {
      // when
      managementService.recalculateJobDuedate("");
      fail("The recalculation with an unknown job ID should not be possible");
    } catch (ProcessEngineException pe) {
      // then
      assertTextPresent("The job id is mandatory, but '' has been provided", pe.getMessage());
    }
  }
  
  public void testNullId() {
    try {
      // when
      managementService.recalculateJobDuedate(null);
      fail("The recalculation with an unknown job ID should not be possible");
    } catch (ProcessEngineException pe) {
      // then
      assertTextPresent("The job id is mandatory, but 'null' has been provided", pe.getMessage());
    }
  }

  @Deployment
  public void testFinishedJob() {
    // given
    HashMap<String, Object> variables1 = new HashMap<String, Object>();
    variables1.put("dueDate", new Date());

    ProcessInstance pi1 = runtimeService.startProcessInstanceByKey("intermediateTimerEventExample", variables1);
    assertEquals(1, managementService.createJobQuery().processInstanceId(pi1.getId()).count());

    JobQuery jobQuery = managementService.createJobQuery().executable();
    assertEquals(1L, jobQuery.count());

    // job duedate can be recalculated, job still exists in runtime
    String jobId = jobQuery.singleResult().getId();
    managementService.recalculateJobDuedate(jobId);
    // run the job, finish the process
    managementService.executeJob(jobId);
    assertEquals(0L, managementService.createJobQuery().processInstanceId(pi1.getId()).count());
    assertProcessEnded(pi1.getProcessInstanceId());
    
    try {
      // when
      managementService.recalculateJobDuedate(jobId);
      fail("The recalculation of a finished job should not be possible");
    } catch (ProcessEngineException pe) {
      // then
      assertTextPresent("No job found with id '" + jobId, pe.getMessage());
    }
  }
  
  public void testEverLivingJob() {
    // given
    Job job = historyService.cleanUpHistoryAsync(true);
    jobIds.add(job.getId());
    
    // when & then
    tryRecalculateUnsupported(job);
  }
  
  @Deployment
  public void testMessageJob() {
    // given
    runtimeService.startProcessInstanceByKey("asyncService");
    Job job = managementService.createJobQuery().singleResult();
    jobIds.add(job.getId());
    
    // when & then
    tryRecalculateUnsupported(job);
  }
  

  // helper /////////////////////////////////////////////////////////////////
  
  protected void tryRecalculateUnsupported(Job job) {
    try {
      // when
      managementService.recalculateJobDuedate(job.getId());
      fail("The recalculation with an unsupported type should not be possible");
    } catch (ProcessEngineException pe) {
      // then
      assertTextPresent("No recalculatable job found for job id '" + job.getId(), pe.getMessage());
    }
  }

  
  protected void clearMeterLog() {
    processEngineConfiguration.getCommandExecutorTxRequired()
      .execute(new Command<Object>() {
        public Object execute(CommandContext commandContext) {
          commandContext.getMeterLogManager().deleteAll();

          return null;
        }
      });
  }
  
  protected void clearJobLog(final String jobId) {
    CommandExecutor commandExecutor = processEngineConfiguration.getCommandExecutorTxRequired();
    commandExecutor.execute(new Command<Object>() {
      public Object execute(CommandContext commandContext) {
        commandContext.getHistoricJobLogManager().deleteHistoricJobLogByJobId(jobId);
        return null;
      }
    });
  }
  
  protected void clearJob(final String jobId) {
    processEngineConfiguration.getCommandExecutorTxRequired()
      .execute(new Command<Object>() {
        public Object execute(CommandContext commandContext) {
          JobEntity job = commandContext.getJobManager().findJobById(jobId);
          if (job != null) {
            commandContext.getJobManager().delete(job);
          }
          return null;
        }
      });
  }
}
