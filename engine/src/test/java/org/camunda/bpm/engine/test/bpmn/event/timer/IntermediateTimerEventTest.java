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

import static org.junit.Assert.assertNotEquals;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.JobQuery;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstanceWithVariables;
import org.camunda.bpm.engine.test.Deployment;
import org.joda.time.LocalDateTime;


public class IntermediateTimerEventTest extends PluggableProcessEngineTestCase {

  @Deployment
  public void testCatchingTimerEvent() throws Exception {

    // Set the clock fixed
    Date startTime = new Date();

    // After process start, there should be timer created
    ProcessInstance pi = runtimeService.startProcessInstanceByKey("intermediateTimerEventExample");
    JobQuery jobQuery = managementService.createJobQuery().processInstanceId(pi.getId());
    assertEquals(1, jobQuery.count());

    // After setting the clock to time '50minutes and 5 seconds', the second timer should fire
    ClockUtil.setCurrentTime(new Date(startTime.getTime() + ((50 * 60 * 1000) + 5000)));
    waitForJobExecutorToProcessAllJobs(5000L);

    assertEquals(0, jobQuery.count());
    assertProcessEnded(pi.getProcessInstanceId());
  }

  @Deployment
  public void testExpression() {
    // Set the clock fixed
    HashMap<String, Object> variables1 = new HashMap<String, Object>();
    variables1.put("dueDate", new Date());

    HashMap<String, Object> variables2 = new HashMap<String, Object>();
    variables2.put("dueDate", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));

    // After process start, there should be timer created
    ProcessInstance pi1 = runtimeService.startProcessInstanceByKey("intermediateTimerEventExample", variables1);
    ProcessInstance pi2 = runtimeService.startProcessInstanceByKey("intermediateTimerEventExample", variables2);

    assertEquals(1, managementService.createJobQuery().processInstanceId(pi1.getId()).count());
    assertEquals(1, managementService.createJobQuery().processInstanceId(pi2.getId()).count());

    // After setting the clock to one second in the future the timers should fire
    List<Job> jobs = managementService.createJobQuery().executable().list();
    assertEquals(2, jobs.size());
    for (Job job : jobs) {
      managementService.executeJob(job.getId());
    }

    assertEquals(0, managementService.createJobQuery().processInstanceId(pi1.getId()).count());
    assertEquals(0, managementService.createJobQuery().processInstanceId(pi2.getId()).count());

    assertProcessEnded(pi1.getProcessInstanceId());
    assertProcessEnded(pi2.getProcessInstanceId());
  }
  
  @Deployment(resources = "org/camunda/bpm/engine/test/bpmn/event/timer/IntermediateTimerEventTest.testExpression.bpmn20.xml")
  public void testExpressionRecalculate() {
    // Set the clock fixed
    HashMap<String, Object> variables1 = new HashMap<String, Object>();
    Date firstDate = new Date();
    variables1.put("dueDate", firstDate);

    // After process start, there should be timer created
    ProcessInstanceWithVariables pi1 = (ProcessInstanceWithVariables) runtimeService.startProcessInstanceByKey("intermediateTimerEventExample", variables1);
    JobQuery jobQuery = managementService.createJobQuery().processInstanceId(pi1.getId());
    assertEquals(1, jobQuery.count());
    assertEquals(firstDate, jobQuery.singleResult().getDuedate());

    // After variable change and recalculation, there should still be one timer only, with a changed due date
    Date secondDate = LocalDateTime.now().plusSeconds(10).toDate();
    runtimeService.setVariable(pi1.getProcessInstanceId(), "dueDate", secondDate);
    Job timerJob = jobQuery.singleResult();
    processEngine.getManagementService().recalculateJobDuedate(timerJob.getId());
    
    assertEquals(1, jobQuery.count());
    assertNotEquals(firstDate, jobQuery.singleResult().getDuedate());
    assertEquals(secondDate, jobQuery.singleResult().getDuedate());
    
    // After waiting for fifteen seconds the timer should fire
    ClockUtil.setCurrentTime(new Date(firstDate.getTime() + TimeUnit.SECONDS.toMillis(15L)));
    waitForJobExecutorToProcessAllJobs(5000L);

    assertEquals(0, managementService.createJobQuery().processInstanceId(pi1.getId()).count());
    assertProcessEnded(pi1.getProcessInstanceId());
  }

  @Deployment
  public void testTimeCycle() {
    String processInstanceId = runtimeService.startProcessInstanceByKey("process").getId();

    JobQuery query = managementService.createJobQuery();
    assertEquals(1, query.count());

    String jobId = query.singleResult().getId();
    managementService.executeJob(jobId);

    assertEquals(0, query.count());

    String taskId = taskService.createTaskQuery().singleResult().getId();
    taskService.complete(taskId);

    assertProcessEnded(processInstanceId);
  }

}