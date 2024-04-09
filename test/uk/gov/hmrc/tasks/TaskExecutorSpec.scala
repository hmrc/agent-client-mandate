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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{ ImplicitSender, DefaultTimeout, TestKit, TestActorRef }

import org.mockito.ArgumentMatchers._
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.agentclientmandate.metrics.ServiceMetrics
import uk.gov.hmrc.agentclientmandate.tasks.ActivationTaskService
import uk.gov.hmrc.agentclientmandate.utils.MockMetricsCache
import utils.ScheduledService

import scala.util.{Failure, Success, Try}

class TaskExecutorSpec extends TestKit(ActorSystem("test"))
  with AnyWordSpecLike with BeforeAndAfterAll with DefaultTimeout with ImplicitSender with MockitoSugar {

  val executorRef: TestActorRef[TestExecutorA] = TestActorRef[TestExecutorA]
  val executorActor: TestExecutorA = executorRef.underlyingActor
  val args1 = Map("a" -> "1", "b" -> "2")
  val retryState1: RetryState = RetryState(1000L,1,1000L)

  val phaseCommit: Phase.Value = Phase.Commit
  val phaseRollback: Phase.Value = Phase.Rollback

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  override def beforeAll(): Unit = {
    when(MockMetricsCache.mockMetrics.startTimer(any()))
      .thenReturn(null)
    super.beforeAll()
  }

  "Executor" must {
    val message = ActivationTaskMessage(mock[ActivationTaskService], MockMetricsCache.mockMetrics)

    "execute with a Start signal when sent a task command when status is New" in {
      executorRef ! TaskCommand(New(Start(args1)), message)
      executorActor.execSignal shouldBe Start(args1)
      expectMsg(TaskCommand(StageComplete(Next("2", Map("c"->"3")), phaseCommit), message))
    }

    "execute with the given signal when status is StageComplete" in {
      executorRef ! TaskCommand(StageComplete(Next("1", args1), phaseCommit), message)
      executorActor.execSignal shouldBe Next("1", args1)
      expectMsg(TaskCommand(StageComplete(Next("1",args1), phaseCommit), message))
    }

    "send back StageFailed when there is an error" in {
      executorRef ! TaskCommand(StageComplete(Next("error", args1), phaseCommit), message)
      expectMsg(TaskCommand(StageFailed(Next("error",args1), phaseCommit, retryState1), message))
    }

    "update the retry state when a Retrying call results in a error" in {
      executorRef ! TaskCommand(Retrying(Next("error", args1), phaseCommit, retryState1), message)
      expectMsg(TaskCommand(StageFailed(Next("error", args1), phaseCommit, RetryState(1000,2,2000)), message))
    }

    "handle failure when sent a Failed" in {
      executorRef ! TaskCommand(Failed(Next("1", args1), phaseCommit), message)
     // executorActor.failedSignal shouldBe Next("1", args1)
      expectMsg(TaskCommand(StageComplete(Next("1", args1), phaseRollback), message))
    }

    "send back Finish for task completion" in {
      executorRef ! TaskCommand(StageComplete(Next("finish", args1), phaseCommit), message)
      expectMsg(TaskCommand(Complete(args1, phaseCommit), message))
    }
  }

}

class TestExecutorA() extends TaskExecutor {

  var execSignal: Signal = null
  var failedSignal: Signal = null
  def metrics: ServiceMetrics = MockMetricsCache.mockMetrics

  override def execute(signal: Signal, service: ScheduledService): Try[Signal] = {
    execSignal = signal

    signal match {
      case Start(args) => Success(Next("2", Map("c"->"3")))
      case Next("error", _) => Failure(new Exception("Intentional"))
      case Next("finish", _) => Success(Finish)
      case _ => Success(signal)
    }
  }

  var ct = 0
  override def currentTime: Long = {
    ct = ct + 1000
    ct
  }

  override def rollback(signal: Signal, service: ScheduledService): Try[Signal] = {
    Success(signal)
  }

  override def onRollbackFailure(lastSignal: Signal, service: ScheduledService): Unit = {}
}
