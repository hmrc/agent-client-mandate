/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.tasks

import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.ActorRef

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{ ImplicitSender, DefaultTimeout, TestKit, TestProbe }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers.{contain, convertToAnyShouldWrapper}
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.agentclientmandate.metrics.ServiceMetrics
import uk.gov.hmrc.agentclientmandate.tasks.ActivationTaskService
import uk.gov.hmrc.agentclientmandate.utils.MockMetricsCache
import utils.ScheduledService

import scala.util.{Success, Try}

class TaskControllerSpec extends TestKit(ActorSystem("test"))
  with AnyWordSpecLike with BeforeAndAfterAll with DefaultTimeout with ImplicitSender with MockitoSugar {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val mockActivationTaskService: ActivationTaskService = mock[ActivationTaskService]

  "TaskController" must {
    "setup a controller with config" in {
      val retryPolicy = new TestRetry
      retryPolicy.setExpectedResult(RetryNow)
      val testTaskManager = TestProbe()
      val config1 = TestRouterConfig_TaskController("test1", classOf[TestExecutor_TaskController], 1, retryPolicy, testTaskManager.ref)
      val config2 = TestRouterConfig_TaskController("test2", classOf[TestExecutor_TaskController], 1, retryPolicy, testTaskManager.ref)

      object TestTaskController extends TaskController(system, 5)
      TestTaskController.setupExecutor(config1)
      TestTaskController.setupExecutor(config2)

      TestTaskController.taskManagers.size shouldBe 2
      TestTaskController.taskManagers.keys should contain("test1")
      TestTaskController.taskManagers.keys should contain("test2")
    }

    "execute the task" in {
      val retryPolicy = new TestRetry
      retryPolicy.setExpectedResult(RetryNow)
      val testTaskManager = TestProbe()
      val config1 = TestRouterConfig_TaskController("test1", classOf[TestExecutor_TaskController], 1, retryPolicy, testTaskManager.ref)

      object TestTaskController extends TaskController(system, 5) {
        override def startClock(intervalSecs:Int): Unit = {}
      }
      TestTaskController.setupExecutor(config1)

      val task = Task("test1", Map(), ActivationTaskMessage(mockActivationTaskService, MockMetricsCache.mockMetrics))
      TestTaskController.execute(task)
      testTaskManager.expectMsg(task)
    }
  }

}

case class TestRouterConfig_TaskController[A <: Actor](taskType:String,
                                                       executorType:Class[A],
                                                       instances:Int,
                                                       retryPolicy:RetryPolicy,
                                                       taskManager: ActorRef) extends ConfigProvider[A] {

  override def newTaskManager(system: ActorSystem): ActorRef = taskManager
}

class TestExecutor_TaskController(val metrics: ServiceMetrics) extends TaskExecutor {

  override def execute(signal: Signal, service: ScheduledService): Try[Signal] = {
    Success(signal)
  }

  //override def onFailed(lastSignal: Signal): Unit = { }
  override def rollback(signal: Signal, service: ScheduledService): Try[Signal] = ???

  override def onRollbackFailure(lastSignal: Signal, service: ScheduledService): Unit = ???
}
