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

package uk.gov.hmrc.agentclientmandate.repositories

import play.api.Logger
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DB
import reactivemongo.api.commands.{MultiBulkWriteResult, WriteResult}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.agentclientmandate.metrics.{Metrics, MetricsEnum}
import uk.gov.hmrc.agentclientmandate.models.{GGRelationshipDto, Mandate}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

sealed trait MandateCreate
case class MandateCreated(mandate: Mandate) extends MandateCreate
case object MandateCreateError extends MandateCreate

sealed trait MandateUpdate
case class MandateUpdated(mandate: Mandate) extends MandateUpdate
case object MandateUpdateError extends MandateUpdate

sealed trait MandateFetchStatus
case class MandateFetched(mandate: Mandate) extends MandateFetchStatus
case object MandateNotFound extends MandateFetchStatus

sealed trait ExistingRelationshipsInsert
case object ExistingRelationshipsInserted extends ExistingRelationshipsInsert
case object ExistingRelationshipsInsertError extends ExistingRelationshipsInsert
case object ExistingRelationshipsAlreadyExist extends ExistingRelationshipsInsert

sealed trait ExistingAgentStatus
case object ExistingAgentFound extends ExistingAgentStatus
case object ExistingAgentNotFound extends ExistingAgentStatus

sealed trait ExistingRelationshipProcess
case object ExistingRelationshipProcessed extends ExistingRelationshipProcess
case object ExistingRelationshipProcessError extends ExistingRelationshipProcess

sealed trait MandateRemove
case object MandateRemoved extends MandateRemove
case object MandateRemoveError extends MandateRemove

trait MandateRepository extends Repository[Mandate, BSONObjectID] {

  def insertMandate(mandate: Mandate): Future[MandateCreate]

  def updateMandate(mandate: Mandate): Future[MandateUpdate]

  def fetchMandate(mandateId: String): Future[MandateFetchStatus]

  def getAllMandatesByServiceName(arn: String, serviceName: String): Future[Seq[Mandate]]

  def insertExistingRelationships(ggRelationshipDto: Seq[GGRelationshipDto]): Future[ExistingRelationshipsInsert]

  def agentAlreadyInserted(agentId: String): Future[ExistingAgentStatus]

  def existingRelationshipProcessed(ggRelationshipDto: GGRelationshipDto): Future[ExistingRelationshipProcess]

  def findGGRelationshipsToProcess(): Future[Seq[GGRelationshipDto]]

  // $COVERAGE-OFF$
  def removeMandate(mandateId: String): Future[MandateRemove]
  // $COVERAGE-ON$

  def metrics: Metrics
}

object MandateRepository extends MongoDbConnection {

  private lazy val mandateRepository = new MandateMongoRepository

  def apply(): MandateRepository = mandateRepository

}

class MandateMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Mandate, BSONObjectID]("mandates", mongo, Mandate.formats, ReactiveMongoFormats.objectIdFormats)
    with MandateRepository {

  val metrics: Metrics = Metrics

  override def indexes: Seq[Index] = {
    Seq(
      Index(Seq("id" -> IndexType.Ascending), name = Some("idIndex"), unique = true, sparse = true),
      Index(Seq("id" -> IndexType.Ascending, "service.name" -> IndexType.Ascending), name = Some("compoundIdServiceIndex"), unique = true, sparse = true),
      Index(Seq("id" -> IndexType.Ascending, "serviceName" -> IndexType.Ascending,
        "agentPartyId" -> IndexType.Ascending, "clientSubscriptionId" -> IndexType.Ascending), name = Some("existingRelationshipIndex"), sparse = true)
    )
  }

  def insertMandate(mandate: Mandate): Future[MandateCreate] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryInsertMandate)
    collection.insert[Mandate](mandate).map { writeResult =>
      timerContext.stop()
      writeResult.ok match {
        case true => MandateCreated(mandate)
        case _ => MandateCreateError
      }
    }.recover {
      // $COVERAGE-OFF$
      case e => Logger.warn("Failed to insert mandate", e)
        timerContext.stop()
        MandateCreateError
      // $COVERAGE-ON$
    }
  }

  def updateMandate(mandate: Mandate): Future[MandateUpdate] = {
    val query = BSONDocument(
      "id" -> mandate.id
    )
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryUpdateMandate)
    collection.update(query, mandate, upsert = false).map { writeResult =>
      timerContext.stop()
      writeResult.ok match {
        case true => MandateUpdated(mandate)
        case _ => MandateUpdateError
      }
    }.recover {
      // $COVERAGE-OFF$
      case e => Logger.warn("Failed to update mandate", e)
        timerContext.stop()
        MandateUpdateError
      // $COVERAGE-ON$
    }
  }

  def fetchMandate(mandateId: String): Future[MandateFetchStatus] = {
    val query = BSONDocument(
      "id" -> mandateId
    )
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchMandate)
    collection.find(query).one[Mandate] map {
      case Some(mandate) =>
        timerContext.stop()
        MandateFetched(mandate)
      case _ =>
        // $COVERAGE-OFF$
        timerContext.stop()
        // $COVERAGE-ON$
        MandateNotFound
    }
  }

  def getAllMandatesByServiceName(arn: String, serviceName: String): Future[Seq[Mandate]] = {
    val query = BSONDocument(
      "agentParty.id" -> arn,
      "subscription.service.name" -> serviceName.toLowerCase
    )
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchMandatesByService)
    val result = collection.find(query).cursor[Mandate]().collect[Seq]()

    result onComplete {
      _ => timerContext.stop()
    }
    result
  }

  def insertExistingRelationships(ggRelationshipDtos: Seq[GGRelationshipDto]): Future[ExistingRelationshipsInsert] = {

    val timerContext = metrics.startTimer(MetricsEnum.RepositoryInsertExistingRelationships)
    agentAlreadyInserted(ggRelationshipDtos.head.agentPartyId).flatMap {
      case ExistingAgentFound =>
        timerContext.stop()
        Future.successful(ExistingRelationshipsAlreadyExist)
      case ExistingAgentNotFound =>

        val insertResult = Try {
          val bulkDocs = ggRelationshipDtos.map(implicitly[collection.ImplicitlyDocumentProducer](_))

          val result = collection.bulkInsert(ordered = false)(bulkDocs: _*)

          result onComplete {
            _ => timerContext.stop()
          }

          result
        }

        insertResult match {
          case Success(s) =>
            s.map {
              case x: MultiBulkWriteResult if x.writeErrors == Nil =>
                Logger.debug(s"[MandateRepository][insertExistingRelationships] $x")
                ExistingRelationshipsInserted
            }.recover {
              case e: Throwable =>
                // $COVERAGE-OFF$
                Logger.warn("Error inserting document", e)
                ExistingRelationshipsInsertError
              // $COVERAGE-ON$
            }

          case Failure(f) =>
            Logger.warn(s"[MandateRepository][insertExistingRelationships] failed: ${f.getMessage}")
            Future.successful(ExistingRelationshipsInsertError)
        }
    }.recover {
      // $COVERAGE-OFF$
      case e => Logger.warn("Failed to insert existing relationship", e)
        timerContext.stop()
        ExistingRelationshipsInsertError
      // $COVERAGE-ON$
    }
  }

  def agentAlreadyInserted(agentId: String): Future[ExistingAgentStatus] = {
    val query = BSONDocument(
      "agentPartyId" -> agentId
    )
    collection.find(query).one[GGRelationshipDto] map {
      case Some(existingAgent) => ExistingAgentFound
      case _ => ExistingAgentNotFound
    }
  }

  def existingRelationshipProcessed(ggRelationshipDto: GGRelationshipDto): Future[ExistingRelationshipProcess] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryExistingRelationshipProcessed)
    val query = BSONDocument(
      "agentPartyId" -> ggRelationshipDto.agentPartyId,
      "clientSubscriptionId" -> ggRelationshipDto.clientSubscriptionId,
      "serviceName" -> ggRelationshipDto.serviceName
    )

    val modifier = BSONDocument("$set" -> BSONDocument("processed" -> true))

    val updateResult = Try {
      val result = collection.update(query, modifier, multi = false, upsert = false)

      result onComplete {
        _ => timerContext.stop()
      }

      result
    }

    updateResult match {
      case Success(s) =>
        s.map {
          case x: WriteResult if x.writeErrors == Nil && !x.hasErrors && x.ok =>
            Logger.debug(s"[MandateRepository][existingRelationshipProcessed] $x")
            ExistingRelationshipProcessed
        }.recover {
          case e: Throwable =>
            // $COVERAGE-OFF$
            Logger.warn("Error updating document", e)
            ExistingRelationshipProcessError
          // $COVERAGE-ON$
        }
      case Failure(f) =>
        Logger.warn(s"[MandateRepository][existingRelationshipProcessed] failed: ${f.getMessage}")
        Future.successful(ExistingRelationshipProcessError)
    }
  }

  def findGGRelationshipsToProcess(): Future[Seq[GGRelationshipDto]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFindGGRelationshipsToProcess)
    val result = Try {

      val query = BSONDocument(
        "agentPartyId" -> BSONDocument("$exists" -> true),
        "processed" -> BSONDocument("$exists" -> false)
      )
      val result = collection.find(query).cursor[GGRelationshipDto]().collect[Seq]()

      result onComplete {
        _ => timerContext.stop()
      }

      result
    }

    result match {
      case Success(s) =>
        s.map { x =>
          Logger.debug(s"[MandateRepository][findGGRelationshipsToProcess] found relationships: ${x.size}")
          x
        }
      case Failure(f) =>
        Logger.warn(s"[MandateRepository][findGGRelationshipsToProcess] failed: ${f.getMessage}")
        Future.successful(Nil)
    }
  }

  // $COVERAGE-OFF$
  // used for test-only
  def removeMandate(mandateId: String): Future[MandateRemove] = {
    val query = BSONDocument("id" -> mandateId)

    collection.remove(query).map { writeResult =>
      writeResult.ok match {
        case true => MandateRemoved
        case _ => MandateRemoveError
      }
    }.recover {
      // $COVERAGE-OFF$
      case e => Logger.warn("Failed to delete mandate", e)
        MandateRemoveError
      // $COVERAGE-ON$
    }
  }
  // $COVERAGE-ON$
}
