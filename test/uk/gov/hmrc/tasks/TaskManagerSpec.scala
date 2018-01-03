/*
 * Copyright 2018 HM Revenue & Customs
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
import org.scalatest.BeforeAndAfterAll
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}


class TaskManagerSpec extends TestKit(ActorSystem("test"))
  with UnitSpec with BeforeAndAfterAll with DefaultTimeout with ImplicitSender {

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
    TestKit.shutdownActorSystem(system)
  }

  "TaskManager" must {

    "send task command to router on receiving a task" in {
      taskManagerActorRef ! Task("test", args1)
      router.cmds(0) shouldBe (TaskCommand(New(Start(args1))))
      router.cmds.clear()
    }

    "forward any StageComplete task command to router" in {
      val msg = TaskCommand(StageComplete(Next("1", args1), phaseCommit))
      taskManagerActorRef ! msg
      router.cmds(0) shouldBe msg
      router.cmds.clear()
    }

    "forward any StageFailed task command to failure manager" in {
      val msg = TaskCommand(StageFailed(Next("1", args1), phaseCommit, retryState1))
      taskManagerActorRef ! msg
      fmgr.cmds(0) shouldBe msg
      fmgr.cmds.clear()
    }

    "forward any Retrying task command to router" in {
      val msg = TaskCommand(Retrying(Next("1", args1), phaseCommit, retryState1))
      taskManagerActorRef ! msg
      router.cmds(0) shouldBe msg
      router.cmds.clear()
    }

    "forward any TaskFailed task command to router" in {
      val msg = TaskCommand(Failed(Next("1", args1), phaseCommit))
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

case class TestConfig_TaskManager[A <: Actor](val taskType: String,
                                              val executorType: Class[A],
                                              val instances: Int,
                                              val retryPolicy: RetryPolicy,
                                              val router: ActorRef,
                                              val failureMgr: ActorRef
                                             ) extends ConfigProvider[A] {

  override def newRouter(context: ActorContext): ActorRef = router

  override def newFailureManager(context: ActorContext): ActorRef = failureMgr
}

class TestExecutor_TaskManager extends TaskExecutor {

  override def execute(signal: Signal): Try[Signal] = {
    Success(signal)
  }

  override def rollback(signal: Signal): Try[Signal] = {
    Success(signal)
  }

  override def onRollbackFailure(lastSignal: Signal) = {}

}
