/*
 * Copyright 2023 HM Revenue & Customs
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

import scala.collection.mutable

/**
  * Actor that deals with TaskCommands that got returned by a TaskExecutor
  * with status StageFailed. It first stores the falied TaskCommand in an
  * in memory queue. Then at periodic intervals (when it receives a Tick
  * message from the Clock) if checks A) if any of the queued TaskCommands
  * need to be retried, in which case it sends the command to the executor
  * with status Retrying and B) if for any of them the max retries has been
  * exceeded, in which case it sends the command to the executor with status
  * Failed, so that the executor can perform any rollbacks/cleanups
  */
protected class FailureManager(val retryPolicy: RetryPolicy) extends Actor {
  val retryQueue: mutable.Queue[TaskCommand] = mutable.Queue()

  override def receive: Receive = {
    case cmd: TaskCommand => retryQueue += cmd
    case Tick             =>
      def extractStatus(tc: TaskCommand):StageFailed = {
        tc.status match {
          case s: StageFailed  => s
          case _               => throw new RuntimeException("Unexpected command " + tc)
        }
      }

      val now = System.currentTimeMillis()

      val retryList = retryQueue.dequeueAll(cmd => retryPolicy.evalRetry(now, extractStatus(cmd).retryState) == RetryNow)
      val failedList = retryQueue.dequeueAll(cmd => retryPolicy.evalRetry(now, extractStatus(cmd).retryState) == StopRetrying)

      retryList.foreach { cmd =>
        val sfStatus = extractStatus(cmd)
        context.parent ! TaskCommand(Retrying(sfStatus.signal, sfStatus.phase, sfStatus.retryState), cmd.message)
      }

      failedList.foreach { cmd =>
        val sfStatus = extractStatus(cmd)
        context.parent ! TaskCommand(Failed(sfStatus.signal, sfStatus.phase), cmd.message)
      }
  }
}
