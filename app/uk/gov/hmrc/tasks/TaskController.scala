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

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable}

import scala.collection.mutable.Map
import scala.concurrent.duration._

trait TaskControllerT {

  //let the concrete object supply the actor system
  protected def system: ActorSystem

  //collection for TaskManagers, once for each task type
  val taskManagers: Map[String, ActorRef] = Map()

  // Client calls this once to set up the execution subsystem for a specific task type
  def setupExecutor[A <: Actor](config: ConfigProvider[A]): Unit = {

    val taskType = config.taskType
    // $COVERAGE-OFF$
    if (taskManagers.contains(taskType)) throw new Exception(s"Executor '$taskType' already set up")
    // $COVERAGE-ON$

    val taskMgr = config.newTaskManager(system)
    taskManagers += (taskType -> taskMgr)
  }

  // Client calls this each time a task needs to be executed
  def execute(task: Task): Unit = {
    // $COVERAGE-OFF$
    if (!taskManagers.contains(task.`type`)) throw new Exception(s"Executor not set up for task of type '${task.`type`}'")
    // $COVERAGE-ON$
    val taskMgr = taskManagers(task.`type`)
    taskMgr ! task
  }

  // Set up the clock to sends a periodic tick to the ErrorManager
  private var cancellable: Cancellable = _

  protected def startClock(intervalSecs: Int): Unit = {
    implicit val ec = system.dispatcher
    cancellable =
      system.scheduler.schedule(0 seconds, intervalSecs seconds) {
        taskManagers.values.foreach(tm => tm ! Tick)
      }
  }

  // $COVERAGE-OFF$
  //Shut down the entire task execution framewok
  def shutdown(): Unit = {

    //TODO send poison pill
    //This cancels further Ticks to be sent
    cancellable.cancel()
  }

  // $COVERAGE-ON$
}

// Akka message. Represents a clock tick
private case object Tick


case class TaskController(
                           val system: ActorSystem,
                           intervalSecs: Int
                         ) extends TaskControllerT {
  startClock(intervalSecs)
}

object TaskController extends TaskController(ActorSystem("task-control-system"), 10)
