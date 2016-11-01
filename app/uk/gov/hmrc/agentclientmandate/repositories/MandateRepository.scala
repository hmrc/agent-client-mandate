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


trait MandateRepository extends Repository[Mandate, BSONObjectID] {

  def insertMandate(mandate: Mandate): Future[MandateCreate]

  def updateMandate(mandate: Mandate): Future[MandateUpdate]

  def fetchMandate(mandateId: String): Future[MandateFetchStatus]

  def getAllMandatesByServiceName(arn: String, serviceName: String): Future[Seq[Mandate]]

  def insertExistingRelationships(ggRelationshipDto: Seq[GGRelationshipDto]): Future[ExistingRelationshipsInsert]

  def agentAlreadyInserted(agentId: String): Future[ExistingAgentStatus]

  def existingRelationshipProcessed(ggRelationshipDto: GGRelationshipDto): Future[ExistingRelationshipProcess]

  def findGGRelationshipsToProcess(): Future[Seq[GGRelationshipDto]]
}

object MandateRepository extends MongoDbConnection {

  private lazy val mandateRepository = new MandateMongoRepository

  def apply(): MandateRepository = mandateRepository

}

class MandateMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Mandate, BSONObjectID]("mandates", mongo, Mandate.formats, ReactiveMongoFormats.objectIdFormats)
    with MandateRepository {

  override def indexes: Seq[Index] = {
    Seq(
      Index(Seq("id" -> IndexType.Ascending), name = Some("idIndex"), unique = true, sparse = true),
      Index(Seq("id" -> IndexType.Ascending, "service.name" -> IndexType.Ascending), name = Some("compoundIdServiceIndex"), unique = true, sparse = true),
      Index(Seq("id" -> IndexType.Ascending, "serviceName" -> IndexType.Ascending,
        "agentPartyId" -> IndexType.Ascending, "clientSubscriptionId" -> IndexType.Ascending), name = Some("existingRelationshipIndex"), sparse = true)
    )
  }

  def insertMandate(mandate: Mandate): Future[MandateCreate] = {
    collection.insert[Mandate](mandate).map { writeResult =>
      writeResult.ok match {
        case true => MandateCreated(mandate)
        case _ => MandateCreateError
      }
    }.recover {
      // $COVERAGE-OFF$
      case e => Logger.error("Failed to insert mandate", e)
        MandateCreateError
      // $COVERAGE-ON$
    }
  }

  def updateMandate(mandate: Mandate): Future[MandateUpdate] = {
    val query = BSONDocument(
      "id" -> mandate.id
    )
    collection.update(query, mandate, upsert = false).map(writeResult => MandateUpdated(mandate))

  }

  def fetchMandate(mandateId: String): Future[MandateFetchStatus] = {
    val query = BSONDocument(
      "id" -> mandateId
    )
    collection.find(query).one[Mandate] map {
      case Some(mandate) => MandateFetched(mandate)
      case _ => MandateNotFound
    }
  }

  def getAllMandatesByServiceName(arn: String, serviceName: String): Future[Seq[Mandate]] = {
    val query = BSONDocument(
      "agentParty.id" -> arn,
      "subscription.service.name" -> serviceName.toLowerCase
    )
    collection.find(query).cursor[Mandate]().collect[Seq]()
  }

  def insertExistingRelationships(ggRelationshipDtos: Seq[GGRelationshipDto]): Future[ExistingRelationshipsInsert] = {

    agentAlreadyInserted(ggRelationshipDtos.head.agentPartyId).flatMap {
      case ExistingAgentFound => Future.successful(ExistingRelationshipsAlreadyExist)
      case ExistingAgentNotFound => {

        val insertResult = Try {
          val bulkDocs = ggRelationshipDtos.map(implicitly[collection.ImplicitlyDocumentProducer](_))

          collection.bulkInsert(ordered=false)(bulkDocs: _*)
        }

        insertResult match {
          case Success(s) => {
            s.map {
              case x: MultiBulkWriteResult if x.writeErrors == Nil =>
                Logger.debug(s"[MandateRepository][insertExistingRelationships] $x")
                ExistingRelationshipsInserted
            }.recover {
              case e: Throwable =>
                // $COVERAGE-OFF$
                Logger.error("Error inserting document", e)
                ExistingRelationshipsInsertError
              // $COVERAGE-ON$
            }
          }

          case Failure(f) => {
            Logger.error(s"[MandateRepository][insertExistingRelationships] failed: ${f.getMessage}")
            Future.successful(ExistingRelationshipsInsertError)
          }
        }
      }
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
    val query = BSONDocument(
      "agentPartyId" -> ggRelationshipDto.agentPartyId,
      "clientSubscriptionId" -> ggRelationshipDto.clientSubscriptionId,
      "serviceName" -> ggRelationshipDto.serviceName
    )

    val modifier = BSONDocument("$set" -> BSONDocument("processed" -> true))

    val updateResult = Try { collection.update(query, modifier, multi = false, upsert = false) }

    updateResult match {
      case Success(s) => {
        s.map {
          case x: WriteResult if x.writeErrors == Nil && !x.hasErrors && x.ok =>
            Logger.debug(s"[MandateRepository][existingRelationshipProcessed] $x")
            ExistingRelationshipProcessed
        }.recover {
          case e: Throwable =>
            // $COVERAGE-OFF$
            Logger.error("Error updating document", e)
            ExistingRelationshipProcessError
          // $COVERAGE-ON$
        }
      }
      case Failure(f) => {
        Logger.error(s"[MandateRepository][existingRelationshipProcessed] failed: ${f.getMessage}")
        Future.successful(ExistingRelationshipProcessError)
      }
    }
  }

  def findGGRelationshipsToProcess(): Future[Seq[GGRelationshipDto]] = {

    val result = Try {

      val query = BSONDocument(
        "agentPartyId" -> BSONDocument("$exists" -> true),
        "processed" -> BSONDocument("$exists" -> false)
      )
      collection.find(query).cursor[GGRelationshipDto]().collect[Seq]()
    }

    result match {
      case Success(s) => {
        s.map { x =>
          Logger.info(s"[MandateRepository][findGGRelationshipsToProcess] found relationships: ${x.size}")
          x
        }
      }
      case Failure(f) => {
        Logger.error(s"[MandateRepository][findGGRelationshipsToProcess] failed: ${f.getMessage}")
        Future.successful(Nil)
      }
    }

  }
}
