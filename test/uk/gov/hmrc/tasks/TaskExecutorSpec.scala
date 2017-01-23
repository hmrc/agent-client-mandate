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

import akka.actor.ActorSystem
import akka.testkit._
import org.scalatest._
import uk.gov.hmrc.play.test.UnitSpec

import scala.util.{Failure, Success, Try}

class TaskExecutorSpec extends TestKit(ActorSystem("test"))
  with UnitSpec with BeforeAndAfterAll with DefaultTimeout with ImplicitSender {

  val executorRef = TestActorRef[TestExecutorA]
  val executorActor = executorRef.underlyingActor
  val args1 = Map("a" -> "1", "b" -> "2")
  val retryState1 = RetryState(1000L,1,1000L)

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Executor" must {
    "execute with a Start signal when sent a task command when status is New" in {
      executorRef ! TaskCommand(New(Start(args1)))
      executorActor.execSignal shouldBe Start(args1)
      expectMsg(TaskCommand(StageComplete(Next("2", Map("c"->"3")))))
    }

    "execute with the given signal when status is StageComplete" in {
      executorRef ! TaskCommand(StageComplete(Next("1", args1)))
      executorActor.execSignal shouldBe Next("1", args1)
      expectMsg(TaskCommand(StageComplete(Next("1",args1))))
    }

    "send back StageFailed when there is an error" in {
      executorRef ! TaskCommand(StageComplete(Next("error", args1)))
      expectMsg(TaskCommand(StageFailed(Next("error",args1),retryState1)))
    }

    "update the retry state when a Retrying call results in a error" in {
      executorRef ! TaskCommand(Retrying(Next("error", args1), retryState1))
      expectMsg(TaskCommand(StageFailed(Next("error", args1),RetryState(1000,2,2000))))
    }

    "handle failure when sent a Failed" in {
      executorRef ! TaskCommand( TaskFailed(Next("1", args1)) )
      executorActor.failedSignal shouldBe Next("1", args1)
      expectMsg(TaskCommand(TaskFailureHandled(args1)))
    }

    "send back Finish for task completion" in {
      executorRef ! TaskCommand(StageComplete(Next("finish", args1)))
      expectMsg(TaskCommand(TaskComplete(args1)))
    }
  }

}

class TestExecutorA extends TaskExecutor {

  var execSignal: Signal = null
  var failedSignal: Signal = null

  override def execute(signal: Signal): Try[Signal] = {
    execSignal = signal

    signal match {
      case Start(args) => Success(Next("2", Map("c"->"3")))
      case Next("error", _) => Failure(new Exception("Intentional"))
      case Next("finish", _) => Success(Finish)
      case _ => Success(signal)
    }
  }

  override def onFailed(lastSignal: Signal): Unit = {
    failedSignal = lastSignal
  }

  var ct = 0
  override def currentTime: Long = {
    ct = ct + 1000
    ct
  }
}
