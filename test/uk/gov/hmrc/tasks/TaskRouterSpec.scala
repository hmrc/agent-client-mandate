/*
 * Copyright 2021 HM Revenue & Customs
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

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestActorRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import org.mockito.MockitoSugar
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.agentclientmandate.tasks.ActivationTaskService
import uk.gov.hmrc.agentclientmandate.utils.MockMetricsCache
import utils.ScheduledService

import scala.util.{Success, Try}

class TaskRouterSpec extends TestKit(ActorSystem("test"))
  with AnyWordSpecLike with BeforeAndAfterAll with DefaultTimeout with ImplicitSender with MockitoSugar {

  val retryPolicy = new TestRetry
  retryPolicy.setExpectedResult(RetryNow)
  val config = TestRouterConfig_TaskRouter("test", classOf[TestExecutor_TaskRouter], 1, retryPolicy)
  val routerRef = TestActorRef[TaskRouter[TestExecutor_TaskRouter]](Props(new TaskRouter(config)))
  val routerActor = routerRef.underlyingActor
  val args1 = Map("a" -> "1", "b" -> "2")

  val phaseCommit = Phase.Commit

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "TaskRouter" must {
    "receive a task and route to an executor" in {
      val message = ActivationTaskMessage(mock[ActivationTaskService], MockMetricsCache.mockMetrics)

      routerRef ! TaskCommand(StageComplete(Next("1", args1), phaseCommit), message)

      expectMsg(TaskCommand(StageComplete(Next("1",args1), phaseCommit), message))
    }
  }


}

case class TestRouterConfig_TaskRouter[A <: Actor](taskType:String,
                                                   executorType:Class[A],
                                                   instances:Int,
                                                   retryPolicy:RetryPolicy
                                                  ) extends ConfigProvider[A] {
}

class TestExecutor_TaskRouter extends TaskExecutor {

  override def execute(signal: Signal, service: ScheduledService): Try[Signal] = Success(signal)
  override def rollback(signal: Signal, service: ScheduledService): Try[Signal] = Success(signal)
  override def onRollbackFailure(lastSignal: Signal, service: ScheduledService): Unit = {}

}
