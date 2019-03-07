/*
 * Copyright 2019 HM Revenue & Customs
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

import scala.util.{Success, Try}

class TaskRouterSpec extends TestKit(ActorSystem("test"))
  with UnitSpec with BeforeAndAfterAll with DefaultTimeout with ImplicitSender {

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
      routerRef ! TaskCommand(StageComplete(Next("1", args1), phaseCommit))

      expectMsg(TaskCommand(StageComplete(Next("1",args1), phaseCommit)))
    }
  }


}

case class TestRouterConfig_TaskRouter[A <: Actor](val taskType:String,
                                  val executorType:Class[A],
                                  val instances:Int,
                                  val retryPolicy:RetryPolicy
                                 ) extends ConfigProvider[A]{

}

class TestExecutor_TaskRouter extends TaskExecutor {

  override def execute(signal: Signal): Try[Signal] = {
    Success(signal)
  }

  override def rollback(signal: Signal): Try[Signal] = {
    Success(signal)
  }

  override def onRollbackFailure(lastSignal: Signal) = {}

}
