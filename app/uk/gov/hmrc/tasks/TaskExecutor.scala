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

import scala.util.{Failure, Success, Try}

trait TaskExecutor extends Actor {

  def execute(signal: Signal): Try[Signal]
  def onFailed(lastSignal: Signal): Unit

  override def receive: Receive = {
    case cmd: TaskCommand => {

      cmd.status match {

        case New(startSig) => executeStage(startSig)
        case StageComplete(sig) => executeStage(sig)
        case Retrying(sig, retryState) => executeStage(sig, Some(retryState))
        case TaskFailed(sig) => handleFailure(sig)
        case other => throw new RuntimeException("Unexpected status " + other)
      }
    }

    case x  => throw new RuntimeException("Unexpected message " + x)
  }

  private def executeStage(signal:Signal, retryStateOpt: Option[RetryState] = None): Unit = {
    val newSignalTry = execute(signal)

    newSignalTry match {
      case Success(newSignal) => {
        if(newSignal == Finish) sender() ! TaskCommand(TaskComplete(signal.args))
        else sender() ! TaskCommand(StageComplete(newSignal))
      }
      case Failure(ex) => {
        val ct = currentTime
        val retryState = retryStateOpt match {
          case Some(rs) => RetryState(rs.firstTryAt, rs.retryCount + 1, ct)
          case None => RetryState(ct, 1, ct)
        }
        sender() ! TaskCommand(StageFailed(signal, retryState))
      }
    }
  }

  private def handleFailure(signal:Signal) : Unit = {
    onFailed(signal)
    sender() ! TaskCommand(TaskFailureHandled(signal.args))

  }

  protected def currentTime = System.currentTimeMillis
}
