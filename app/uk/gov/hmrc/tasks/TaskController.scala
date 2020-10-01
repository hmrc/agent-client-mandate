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

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable}

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

trait TaskControllerT {

  protected def system: ActorSystem
  val taskManagers: mutable.Map[String, ActorRef] = mutable.Map()

  def setupExecutor[A <: Actor](config: ConfigProvider[A]): Unit = {
    val taskType = config.taskType

    if (!taskManagers.contains(taskType)) {
      val taskMgr = config.newTaskManager(system)
      taskManagers += (taskType -> taskMgr)
    }
  }

  def execute(task: Task): Unit = {
    if (!taskManagers.contains(task.`type`)) throw new Exception(s"Executor not set up for task of type '${task.`type`}'")

    val taskMgr = taskManagers(task.`type`)
    taskMgr ! task
  }

  private var cancellable: Cancellable = _

  protected def startClock(intervalSecs: Int): Unit = {
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    cancellable =
      system.scheduler.schedule(0 seconds, intervalSecs seconds) {
        taskManagers.values.foreach(tm => tm ! Tick)
      }
  }

  def shutdown(): Unit = {
    //TODO send poison pill
    cancellable.cancel()
  }
}

private case object Tick

case class TaskController(
                           system: ActorSystem,
                           intervalSecs: Int
                         ) extends TaskControllerT {
  startClock(intervalSecs)
}

object TaskController extends TaskController(ActorSystem("task-control-system"), 10)
