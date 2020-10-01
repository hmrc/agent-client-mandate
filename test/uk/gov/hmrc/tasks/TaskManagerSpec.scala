/*
 * Copyright 2020 HM Revenue & Customs
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

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, Props}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestActorRef, TestKit}
import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.agentclientmandate.tasks.ActivationTaskService
import uk.gov.hmrc.agentclientmandate.utils.MockMetricsCache
import utils.ScheduledService

import scala.collection.mutable.ArrayBuffer
import scala.util.{Success, Try}


class TaskManagerSpec extends TestKit(ActorSystem("test"))
  with WordSpecLike with BeforeAndAfterAll with DefaultTimeout with ImplicitSender with MockitoSugar {

  val retryPolicy = new TestRetry
  retryPolicy.setExpectedResult(RetryNow)
  val routerRef = TestActorRef[TestRouter_TaskManager]
  val fmgrRef = TestActorRef[TestFailureManager_TaskManager]
  val router = routerRef.underlyingActor
  val fmgr = fmgrRef.underlyingActor

  val config = TestConfig_TaskManager("test", classOf[TestExecutor_TaskManager], 1, retryPolicy, routerRef, fmgrRef)
  val taskManagerActorRef = TestActorRef[TaskManager[TestExecutor_TaskManager]](Props(new TaskManager(config)))
  val actor = taskManagerActorRef.underlyingActor
  val args1 = Map("a" -> "1", "b" -> "2")
  val retryState1 = RetryState(1000L, 1, 1000L)

  val phaseCommit = Phase.Commit

  override def afterAll {
//    TestKit.shutdownActorSystem(system)
  }

  "TaskManager" must {

    val message = ActivationTaskMessage(mock[ActivationTaskService], MockMetricsCache.mockMetrics)

    "send task command to router on receiving a task" in {
      taskManagerActorRef ! Task("test", args1, message)
      router.cmds(0) shouldBe (TaskCommand(New(Start(args1)), message))
      router.cmds.clear()
    }

    "forward any StageComplete task command to router" in {
      val msg = TaskCommand(StageComplete(Next("1", args1), phaseCommit), message)
      taskManagerActorRef ! msg
      router.cmds(0) shouldBe msg
      router.cmds.clear()
    }

    "forward any StageFailed task command to failure manager" in {
      val msg = TaskCommand(StageFailed(Next("1", args1), phaseCommit, retryState1), message)
      taskManagerActorRef ! msg
      fmgr.cmds(0) shouldBe msg
      fmgr.cmds.clear()
    }

    "forward any Retrying task command to router" in {
      val msg = TaskCommand(Retrying(Next("1", args1), phaseCommit, retryState1), message)
      taskManagerActorRef ! msg
      router.cmds(0) shouldBe msg
      router.cmds.clear()
    }

    "forward any TaskFailed task command to router" in {
      val msg = TaskCommand(Failed(Next("1", args1), phaseCommit), message)
      taskManagerActorRef ! msg
      router.cmds(0) shouldBe msg
      router.cmds.clear()
    }

    "send a Tick to failure manager" in {
      taskManagerActorRef ! Tick
      fmgr.cmds(0) shouldBe Tick
      fmgr.cmds.clear()
    }
  }

}


class TestRouter_TaskManager extends Actor {
  var cmds: ArrayBuffer[TaskCommand] = ArrayBuffer()

  override def receive: Receive = {
    case x: TaskCommand => cmds += x
  }
}

class TestFailureManager_TaskManager extends Actor {
  var cmds: ArrayBuffer[Any] = ArrayBuffer()

  override def receive: Receive = {
    case x: TaskCommand => cmds += x
    case Tick => cmds += Tick
  }
}

case class TestConfig_TaskManager[A <: Actor](taskType: String,
                                              executorType: Class[A],
                                              instances: Int,
                                              retryPolicy: RetryPolicy,
                                              router: ActorRef,
                                              failureMgr: ActorRef) extends ConfigProvider[A] {

  override def newRouter(context: ActorContext): ActorRef = router

  override def newFailureManager(context: ActorContext): ActorRef = failureMgr
}

class TestExecutor_TaskManager() extends TaskExecutor {
  override def execute(signal: Signal, service: ScheduledService): Try[Signal] = Success(signal)
  override def rollback(signal: Signal, service: ScheduledService): Try[Signal] = Success(signal)
  override def onRollbackFailure(lastSignal: Signal, service: ScheduledService): Unit = {}
}
