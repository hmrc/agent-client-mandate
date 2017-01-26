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
import Phase._

import scala.util.{Failure, Success, Try}

trait TaskExecutor extends Actor {

  def execute(signal: Signal): Try[Signal]
  def rollback(signal: Signal): Try[Signal]

  override def receive: Receive = {
    case cmd: TaskCommand => {

      cmd.status match {

        case New(startSig) => doTaskCommand(startSig, Commit)
        case StageComplete(sig, phase) => doTaskCommand(sig, phase)
        case Retrying(sig, phase, retryState) => doTaskCommand(sig, phase, Some(retryState))
        case TaskFailed(lastSig) => doTaskCommand(StartRollback(lastSig), Rollback)
        // $COVERAGE-OFF$
        case other => throw new RuntimeException("Unexpected status " + other)
        // $COVERAGE-ON$
      }
    }
    // $COVERAGE-OFF$
    case x  => throw new RuntimeException("Unexpected message " + x)
    // $COVERAGE-ON$
  }


  private def doTaskCommand(signal:Signal, phase:Phase, retryStateOpt: Option[RetryState] = None): Unit = {

    val newSignalTry = if(phase == Commit) execute(signal) else rollback(signal)

    newSignalTry match {
      case Success(newSignal) => {
        if(newSignal == Finish) {
          val ts = if(phase == Commit) TaskComplete(signal.args) else TaskFailureHandled(signal.args)
          sender() ! TaskCommand(ts)
        }
        else sender() ! TaskCommand(StageComplete(newSignal, phase))
      }
      case Failure(ex) => {
        val ct = currentTime
        val retryState =
          retryStateOpt match {
            case Some(rs) => RetryState(rs.firstTryAt, rs.retryCount + 1, ct)
            case None => RetryState(ct, 1, ct)
          }
        sender() ! TaskCommand(StageFailed(signal, phase, retryState))
      }
    }
  }

  // $COVERAGE-OFF$
  protected def currentTime = System.currentTimeMillis
  // $COVERAGE-ON$
}
