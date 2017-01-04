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

package uk.gov.hmrc.agentclientmandate.actors

import akka.actor.{Actor, ActorRef, Props}
import akka.contrib.throttle.Throttler._
import akka.contrib.throttle.TimerBasedThrottler
import play.api.Logger
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.agentclientmandate.config.ApplicationConfig
import uk.gov.hmrc.agentclientmandate.repositories.MandateRepository
import uk.gov.hmrc.lock.{LockKeeper, LockMongoRepository, LockRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class ProcessingSupervisor extends Actor with ActorUtils with MongoDbConnection {

  val connection = db

  val lockRepo = LockMongoRepository(connection)

  val lockKeeper = new LockKeeper {

    override def repo: LockRepository = lockRepo

    override def lockId: String = "existingRelationshipProcessing"

    val FiveMinutes = 5

    override val forceLockReleaseAfter: org.joda.time.Duration = org.joda.time.Duration.standardMinutes(FiveMinutes)

    // $COVERAGE-OFF$
    override def tryLock[T](body: => Future[T])(implicit ec : ExecutionContext): Future[Option[T]] = {
      repo.lock(lockId, serverId, forceLockReleaseAfter)
        .flatMap { acquired =>
          if (acquired) { body.map { case x => Some(x) } }
          else Future.successful(None)
        }.recoverWith { case ex => repo.releaseLock(lockId, serverId).flatMap(_ => Future.failed(ex)) }
    }
    // $COVERAGE-ON$
  }

  // $COVERAGE-OFF$
  lazy val repository: MandateRepository = MandateRepository()
  lazy val processingActor: ActorRef = context.actorOf(ImportExistingRelationshipsActor.props, "import-existing-relationship-processor")
  lazy val throttler: ActorRef = context.actorOf(Props(classOf[TimerBasedThrottler],
    ApplicationConfig.etmpTps msgsPer 2.seconds), "throttler")
  // $COVERAGE-ON$

  throttler ! SetTarget(Some(processingActor))

  override def receive: Receive = {

    case STOP =>
      // $COVERAGE-OFF$
      lockRepo.releaseLock(lockKeeper.lockId, lockKeeper.serverId)
    // $COVERAGE-ON$
    case START =>
      lockKeeper.tryLock {
        context become receiveWhenProcessRunning

        repository.findGGRelationshipsToProcess().map { result =>
          if (result.nonEmpty) {
            for (ggRelationship <- result) {
              throttler ! ggRelationship
            }
            throttler ! STOP
          }
          else {
            throttler ! STOP
          }
        }
      }

  }

  def receiveWhenProcessRunning: Receive = {
    // $COVERAGE-OFF$
    case START =>
    case STOP =>
      lockRepo.releaseLock(lockKeeper.lockId, lockKeeper.serverId)
      context unbecome
    // $COVERAGE-ON$
  }
}
