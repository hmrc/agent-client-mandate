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

trait ConfigProvider[A <: Actor] {
  def taskType: String
  def executorType: Class[A]
  def instances: Int
  def retryPolicy: RetryPolicy

  def newTaskManager(system: ActorSystem): ActorRef = {
    system.actorOf(Props(new TaskManager(this)), name = taskType + "-mgr")
  }

  def newRouter(context: ActorContext): ActorRef = {
    context.actorOf(Props(new TaskRouter(this)), name = taskType + "-router")
  }

  def newExecutor(context: ActorContext): ActorRef = {
    context.actorOf(Props(executorType))
  }

  def newFailureManager(context: ActorContext): ActorRef = {
    context.actorOf(Props(new FailureManager(retryPolicy)), name = taskType + "-fmgr")
  }
}

case class TaskConfig[A <: Actor](taskType:String,
                                  executorType:Class[A],
                                  instances:Int,
                                  retryPolicy:RetryPolicy
                                 ) extends ConfigProvider[A]
