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

import akka.actor.Actor
import play.api.Logger
import uk.gov.hmrc.agentclientmandate.metrics.{MetricsEnum, ServiceMetrics}
import uk.gov.hmrc.tasks.Phase._
import utils.ScheduledService

import scala.util.{Failure, Success, Try}

trait TaskExecutor extends Actor {

  def execute(signal: Signal, service: ScheduledService): Try[Signal] = service.execute(signal)
  def rollback(signal: Signal, service: ScheduledService): Try[Signal] = service.rollback(signal)
  def onRollbackFailure(lastSignal: Signal, service: ScheduledService): Unit = service.onRollbackFailure(lastSignal)

  override def receive: Receive = {
    case cmd: TaskCommand =>
      cmd.status match {
        case New(startSig) => doTaskCommand(startSig, Commit, cmd.message)
        case StageComplete(sig, phase) => doTaskCommand(sig, phase, cmd.message)
        case Retrying(sig, phase, retryState) =>
          sig match {
            case Start(args) => cmd.message.metrics.incrementFailedCounter(MetricsEnum.StageStartSignalFailed)
            case Next(stage, args) => cmd.message.metrics.incrementFailedCounter(stage)
            case _ => println(s"Signal Type:::$sig")
          }

          doTaskCommand(sig, phase, cmd.message, Some(retryState))
        case Failed(sig, Commit) => doTaskCommand(sig, Rollback, cmd.message)
        case Failed(sig, Rollback) => handleRollbackFailure(sig, cmd.message)
        case other => throw new RuntimeException("Unexpected status " + other)
      }
    case x  => throw new RuntimeException("Unexpected message " + x)
  }

  private def doTaskCommand(signal: Signal, phase: Phase, message: ScheduledMessage, retryStateOpt: Option[RetryState] = None): Unit = {

    val newSignalTry = if (phase == Commit) {
      execute(signal, message.service)
    } else {
      rollback(signal, message.service)
    }

    newSignalTry match {
      case Success(newSignal) =>
        if (newSignal == Finish) {
          sender() ! TaskCommand(Complete(signal.args, phase), message)
        } else {
          sender() ! TaskCommand(StageComplete(newSignal, phase), message)
        }
      case Failure(ex) =>
        Logger.warn(s"[TaskExecutor][doTaskCommand] Failure Exception:::${ex.getMessage}")
        val ct = currentTime
        val retryState =
          retryStateOpt match {
            case Some(rs) => RetryState(rs.firstTryAt, rs.retryCount + 1, ct)
            case None     => RetryState(ct, 1, ct)
          }
        sender() ! TaskCommand(StageFailed(signal, phase, retryState), message)
    }
  }

  private def handleRollbackFailure(lastSignal: Signal, message: ScheduledMessage): Unit ={
    onRollbackFailure(lastSignal, message.service)
    sender() ! TaskCommand(RollbackFailureHandled(lastSignal.args), message)
  }

  protected def currentTime: Long = System.currentTimeMillis
}
