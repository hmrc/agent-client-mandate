/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientmandate.actors

import akka.actor.{Actor, ActorRef, Props}
import akka.contrib.throttle.Throttler._
import akka.contrib.throttle.TimerBasedThrottler
import play.api.Logger
import play.modules.reactivemongo.ReactiveMongoPlugin
import uk.gov.hmrc.agentclientmandate.config.ApplicationConfig
import uk.gov.hmrc.agentclientmandate.repositories.MandateRepository
import uk.gov.hmrc.lock.{LockKeeper, LockMongoRepository, LockRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ProcessingSupervisor extends Actor with ActorUtils {

  val connection = {
    import play.api.Play.current
    ReactiveMongoPlugin.mongoConnector.db
  }

  val lockRepo = LockMongoRepository(connection)

  val lockKeeper = new LockKeeper {

    override def repo: LockRepository = lockRepo

    override def lockId: String = "existingRelationshipProcessing"

    val FiveMinutes = 5

    override val forceLockReleaseAfter: org.joda.time.Duration = org.joda.time.Duration.standardMinutes(FiveMinutes)
  }

  // $COVERAGE-OFF$
  lazy val repository: MandateRepository = MandateRepository()
  lazy val processingActor: ActorRef = context.actorOf(ImportExistingRelationshipsActor.props, "import-existing-relationship-processor")
  lazy val throttler: ActorRef = context.actorOf(Props(classOf[TimerBasedThrottler],
    ApplicationConfig.etmpTps msgsPer 10.seconds), "throttler")
  // $COVERAGE-ON$

  throttler ! SetTarget(Some(processingActor))

  override def receive: Receive = {

    case STOP =>
      Logger.debug("[ProcessingSupervisor] received while not processing: STOP received")
      lockRepo.releaseLock(lockKeeper.lockId, lockKeeper.serverId)
    case START =>
      lockKeeper.tryLock {
        context become receiveWhenProcessRunning
        Logger.debug("Starting Existing Relationship Processing")

        repository.findGGRelationshipsToProcess().map { result =>
          if (result.nonEmpty) {
            for (ggRelationship <- result) {
              throttler ! ggRelationship
            }
            throttler ! STOP
          }
          else {
            throttler ! STOP
            context unbecome
          }
        }
      }.map {
        // $COVERAGE-OFF$
        case Some(thing) => Logger.debug(s"[ProcessingSupervisor][receive] obtained mongo lock")
        case _ => Logger.debug(s"[ProcessingSupervisor][receive] failed to obtain mongo lock")
        // $COVERAGE-ON$
      }

  }

  def receiveWhenProcessRunning: Receive = {
    case START => Logger.debug("[ProcessingSupervisor][received while processing] START ignored")
    case STOP =>
      Logger.debug("[ProcessingSupervisor][received while processing] STOP received")
      lockRepo.releaseLock(lockKeeper.lockId, lockKeeper.serverId)
      context unbecome
  }
}
