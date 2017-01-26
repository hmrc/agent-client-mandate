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

import akka.actor.Actor


/**
  * The root level supervisor for a task type. It gets created
  * and initialsed when TaskController.setupExecutor() is called
  * by the client.
  *
  * It responsibilities are
  *  1) During initialisation to set up the actor sub system that
  *     handles tasks of a sepcific type. These are the Router and
  *     the FailureManager.
  *  2) During actual execution of tasks, to act as the central
  *     exchange and control the flow of messages getting passed
  *     between the TaskController, Executor and FailureManager
  *  3) After a task has either completed successfully or failed
  *     to perform any final logging and clean up of resources
  */
protected class TaskManager[A <: Actor] (config:ConfigProvider[A]) extends Actor {

  val router = config.newRouter(context)
  val failureMgr = config.newFailureManager(context)

  override def receive: Receive = {
    case task: Task =>
      val msg = TaskCommand(New(Start(task.args)))
      router ! msg

    case cmd: TaskCommand =>
      cmd.status match {
        // $COVERAGE-OFF$
        case _: New => throw new RuntimeException("[TaskManager] - Unexpected command New")
        // $COVERAGE-ON$
        case StageComplete(_, _) => router ! cmd
        case _: StageFailed => failureMgr ! cmd
        case _: Retrying => router ! cmd
        case TaskFailed(_) => router ! cmd
        case TaskComplete(_) | TaskFailureHandled(_) => cleanUp(cmd)
      }

    case Tick =>
      failureMgr ! Tick

  }

  def cleanUp(cmd: TaskCommand): Unit = {
    //TODO: perform clean up
    sender() ! "cleaned_up"
  }

}
