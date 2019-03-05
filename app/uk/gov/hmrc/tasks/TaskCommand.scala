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

// Akka message. Represents a command to execute a task. Contains
// the task to be executed as provided by the client as well as
// an ExecutionStatus (see below)
case class TaskCommand(status:ExecutionStatus)

// Specifies the current state of processing of a TaskCommand. This
// is used by the processing system o decide on the next action to be taken
// for the TaskCommand
sealed trait ExecutionStatus

//Brand new TaskCommand, sent to TaskExecutor to try and execute
// the first stage.
case class New(signal:Start) extends ExecutionStatus

// When an intermediate stage of the task got completed successfully by
// the TaskExecutor it sends this (to itself via the TaskManager), so that
// the next stage, as specified by the contained Signal (Next), can be executed
case class StageComplete(signal:Signal, phase: Phase.Phase) extends ExecutionStatus

//When a stage of the task throws an exception the TaskExecutor sends this
// to the FailureManager. Includes the original Signal well as the RetryState.
case class StageFailed(signal:Signal, phase: Phase.Phase, retryState:RetryState) extends ExecutionStatus

//When FailureManager determines that this TaskCommand can be retried
// it sends it to the TaskExecutor. It contains the Signal for the last
// failed Stage as well as the last RetryState.
case class Retrying(signal:Signal, phase: Phase.Phase, retryState:RetryState) extends ExecutionStatus

//---------------------------------------------------------------------------------------
//When the TaskExecutor has successfully completes the last stage of execution / rollback
// i.e. when the Signal returned is Finish, it sends this to the TaskManager to perform
// any clean up. This is a final message in the sequence for a task that is successfully
// completed / rolled back.
case class Complete(args: Map[String, String], phase:Phase.Phase) extends ExecutionStatus

//-------------------------------------------------------------------
//When FailureManager determines that this TaskCommand has exceeded the
// retry limit for execution / rollback it sends this to the TaskExecutor
// so that the rollback() / onRollbackFailed() method can be be called.
case class Failed(lastSignal:Signal, phase:Phase.Phase) extends ExecutionStatus

//--------------------------------------------------------------------
//When the TaskExecutor successfully completes onRollbackFailed(), it
// sends this to the TaskManager to perform any clean up. This
// is a final message in the sequence for a task that failed to rollback.
case class RollbackFailureHandled(args: Map[String, String]) extends ExecutionStatus

//Captures the state of retry for a task at the end of each retry. Used
// by failure manager to work out if and when to retry the task
case class RetryState(firstTryAt:Long, retryCount:Int, lastTryAt:Long)


object Phase extends Enumeration {
  type Phase = Value
  val Commit, Rollback = Value
}
