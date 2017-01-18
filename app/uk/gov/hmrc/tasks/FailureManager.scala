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

import scala.collection.mutable.Queue
import scala.language.postfixOps

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

  val retryQueue: Queue[TaskCommand] = Queue()

  override def receive: Receive = {

    //These are failed TaskCommands from TaskExecutors
    // Enqueue them for retry later
    case cmd: TaskCommand => {
      retryQueue += cmd
    }

    //Tick from the clock. Wake up to evaluate if any TaskCommands
    // can be retried now, or if any have exceeded the retry limit
    case Tick => {

      //helper to get the ExecutionStatus of a TaskCommand
      def extractStatus(tc:TaskCommand):StageFailed = {
        tc.status match {
          case s:StageFailed => s
          case _ => throw new RuntimeException("[FailureManager] Unexpected extract status " + tc)
        }
      }

      //current time
      val now = System.currentTimeMillis()

      //Dequeue all TaskCommands that can be retried now according to the RetryPolicy and hold them in a list
      val retryList = retryQueue.dequeueAll(cmd => retryPolicy.evalRetry(now, extractStatus(cmd).retryState) == RetryNow)

      //Dequeue all TaskCommands that have exceeded retry limit according to the RetryPolicy and hold them in a list
      val failedList = retryQueue.dequeueAll(cmd => retryPolicy.evalRetry(now, extractStatus(cmd).retryState) == StopRetrying)

      //For retries, send a new TaskCommand with status Retrying
      retryList.foreach { cmd =>
        val st = extractStatus(cmd)
        context.parent ! TaskCommand(Retrying(st.signal, st.retryState))
      }

      //For failures, send a new TaskCommand with status Failed
      failedList.foreach { cmd =>
        val st = extractStatus(cmd)
        context.parent ! TaskCommand(TaskFailed(st.signal))
      }
    }
  }

}
