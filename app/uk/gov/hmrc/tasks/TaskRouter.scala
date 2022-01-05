/*
 * Copyright 2022 HM Revenue & Customs
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

import akka.actor.{Actor, Terminated}
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}

/**
  * This is a simple round robin load balancing router that distributes
  * messages to one of N instances of Executors. The no. of instances
  * should be set to be equal to the number of parallel threads this
  * task can be carried out on.
  */
protected class TaskRouter[A <: Actor] (config:ConfigProvider[A]) extends Actor {

  //Create an akka router with N instances of the
  // Executor as routees and a round robbin
  // load balancing policy
  var router: Router = {
    val routees = Vector.fill(config.instances) {
      val r = config.newExecutor(context)
      context.watch(r)
      ActorRefRoutee(r)
    }

    Router(RoundRobinRoutingLogic(), routees)
  }

  def receive: Receive = {
    case cmd: TaskCommand =>
      router.route(cmd, sender())
    case Terminated(a) =>
      router = router.removeRoutee(a)
      val r = config.newExecutor(context)
      context watch r
      router = router.addRoutee(r)
  }
}
