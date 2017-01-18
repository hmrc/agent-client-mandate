/*
 * Copyright 2017 HM Revenue & Customs
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
import akka.testkit._
import org.scalatest._
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.mutable.ArrayBuffer

class FailureManagerSpec extends TestKit(ActorSystem("test"))
   with UnitSpec with BeforeAndAfterAll with DefaultTimeout with ImplicitSender  {

  val retryPolicy = new TestRetry
  val args1 = Map("a" -> "1", "b" -> "2")
  val retryState1 = RetryState(1000L,1,1000L)

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Failure Manager" must {
    "enqueue any task command sent to it" in {
      val failureManagerActorRef = TestActorRef[FailureManager](Props(new FailureManager(retryPolicy)))
      val taskCommand = TaskCommand(StageFailed(Next("1", args1), retryState1))
      failureManagerActorRef ! taskCommand
      failureManagerActorRef.underlyingActor.retryQueue.size should be (1)
      val tc = failureManagerActorRef.underlyingActor.retryQueue.dequeue()
      tc should be(taskCommand)
    }

    "on clock tick send a Retrying command to its supervisor for each queued message" in {
      val svRef = TestActorRef[TestSupervisor]
      val failureManagerActorRef = TestActorRef[FailureManager](Props(new FailureManager(retryPolicy)), svRef, "")

      retryPolicy.setExpectedResult(RetryNow)
      failureManagerActorRef ! TaskCommand(StageFailed(Next("1", args1), retryState1))
      failureManagerActorRef ! TaskCommand(StageFailed(Next("2", args1), retryState1))
      failureManagerActorRef ! TaskCommand(StageFailed(Next("3", args1), retryState1))

      failureManagerActorRef.underlyingActor.retryQueue.size should be (3)

      failureManagerActorRef ! Tick

      failureManagerActorRef.underlyingActor.retryQueue.size should be (0)

      svRef.underlyingActor.cmds(0) shouldBe TaskCommand(Retrying(Next("1", args1), retryState1))
      svRef.underlyingActor.cmds(1) shouldBe TaskCommand(Retrying(Next("2", args1), retryState1))
      svRef.underlyingActor.cmds(2) shouldBe TaskCommand(Retrying(Next("3", args1), retryState1))
    }

    "on clock tick, if no more retries left, send a Failed command to its supervisor for each queued message" in {
      val svRef = TestActorRef[TestSupervisor]
      val failureManagerActorRef = TestActorRef[FailureManager](Props(new FailureManager(retryPolicy)), svRef, "")

      retryPolicy.setExpectedResult(StopRetrying)
      failureManagerActorRef ! TaskCommand(StageFailed(Next("1", args1), retryState1))
      failureManagerActorRef ! TaskCommand(StageFailed(Next("2", args1), retryState1))
      failureManagerActorRef ! TaskCommand(StageFailed(Next("3", args1), retryState1))

      failureManagerActorRef.underlyingActor.retryQueue.size should be (3)

      failureManagerActorRef ! Tick

      failureManagerActorRef.underlyingActor.retryQueue.size should be (0)

      svRef.underlyingActor.cmds(0) shouldBe TaskCommand(TaskFailed(Next("1", args1)))
      svRef.underlyingActor.cmds(1) shouldBe TaskCommand(TaskFailed(Next("2", args1)))
      svRef.underlyingActor.cmds(2) shouldBe TaskCommand(TaskFailed(Next("3", args1)))
    }

//    "on clock tick, throw exception for unknown status" in {
//      val svRef = TestActorRef[TestSupervisor]
//      val failureManagerActorRef = TestActorRef[FailureManager](Props(new FailureManager(retryPolicy)), svRef, "")
//
//      retryPolicy.setExpectedResult(RetryNow)
//
//      failureManagerActorRef ! TaskCommand(New(Start(Map())))
//
//      val x = intercept[RuntimeException] { failureManagerActorRef ! Tick }
//      x.getMessage should contain("[FailureManager] Unexpected extract status")
//    }
  }

}

class TestRetry extends RetryPolicy{
  var expected: RetryEvalResult = _
  def setExpectedResult(expected: RetryEvalResult):Unit = this.expected = expected

  override def evalRetry(now:Long, state:RetryState): RetryEvalResult = {
    expected
  }
}

class TestSupervisor extends Actor {
  var cmds:ArrayBuffer[TaskCommand] = ArrayBuffer()
  override def receive: Receive = {
    case x:TaskCommand => cmds += x
  }
}
