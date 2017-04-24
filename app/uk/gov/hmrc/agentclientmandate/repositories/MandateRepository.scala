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

package uk.gov.hmrc.agentclientmandate.repositories

import play.api.Logger
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.Json
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DB
import reactivemongo.api.collections.bson
import reactivemongo.api.commands.{MultiBulkWriteResult, WriteResult}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.agentclientmandate.metrics.{Metrics, MetricsEnum}
import uk.gov.hmrc.agentclientmandate.models._
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
case object MandateUpdatedEmail extends MandateUpdate

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

  def fetchMandateByClient(clientId: String, service: String): Future[MandateFetchStatus]

  def getAllMandatesByServiceName(arn: String, serviceName: String): Future[Seq[Mandate]]

  def insertExistingRelationships(ggRelationshipDto: Seq[GGRelationshipDto]): Future[ExistingRelationshipsInsert]

  def agentAlreadyInserted(agentId: String): Future[ExistingAgentStatus]

  def existingRelationshipProcessed(ggRelationshipDto: GGRelationshipDto): Future[ExistingRelationshipProcess]

  def findGGRelationshipsToProcess(): Future[Seq[GGRelationshipDto]]

  def findMandatesMissingAgentEmail(arn: String, service: String): Future[Seq[String]]

  def updateAgentEmail(mandateIds: Seq[String], email: String): Future[MandateUpdate]

  def updateClientEmail(mandateId: String, email: String): Future[MandateUpdate]

  // $COVERAGE-OFF$
  def removeMandate(mandateId: String): Future[MandateRemove]
  // $COVERAGE-ON$

  def metrics: Metrics
}

object MandateRepository extends MongoDbConnection {

  // $COVERAGE-OFF$
  private lazy val mandateRepository = new MandateMongoRepository
  // $COVERAGE-ON$

  def apply(): MandateRepository = mandateRepository

}

class MandateMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Mandate, BSONObjectID]("mandates", mongo, Mandate.formats, ReactiveMongoFormats.objectIdFormats)
    with MandateRepository {

  //Temporary code and should be removed after next deployment - start
  // $COVERAGE-OFF$
  collection.update(BSONDocument("currentStatus.status" -> "PendingActivation", "statusHistory.status" -> "Approved"), BSONDocument("$set" -> BSONDocument("currentStatus.status" -> "Approved")), upsert=false, multi=true)

  collection.find(BSONDocument("id" -> "F98A717D")).one[Mandate] map {
    case Some(mandate) =>
      val agentPartyCopy = mandate.agentParty
      val subscriptionCopy = mandate.subscription
      val createdByCopy = mandate.createdBy
      val mandateCopy = mandate.copy(agentParty = agentPartyCopy.copy(id="", name="", contactDetails=ContactDetails("", None)),
        subscription = subscriptionCopy.copy(referenceNumber = None),
        createdBy = createdByCopy.copy(credId="", name="", groupId=None))
      val isClient = mandate.clientParty.isDefined
      Logger.error("Found mandate F98A717D -> withClient" + isClient + ", details: " + mandateCopy)
    case _ => Logger.error("Could not find mandate F98A717D")
  }

  collection.find(BSONDocument("id" -> "24033514")).one[Mandate] map {
    case Some(mandate) =>
      val agentPartyCopy = mandate.agentParty
      val subscriptionCopy = mandate.subscription
      val createdByCopy = mandate.createdBy
      val mandateCopy = mandate.copy(agentParty = agentPartyCopy.copy(id="", name="", contactDetails=ContactDetails("", None)),
        subscription = subscriptionCopy.copy(referenceNumber = None),
        createdBy = createdByCopy.copy(credId="", name="", groupId=None))
      val isClient = mandate.clientParty.isDefined
      Logger.error("Found mandate 24033514 -> withClient" + isClient + ", details: " + mandateCopy)
    case _ => Logger.error("Could not find mandate 24033514")
  }

  collection.find(BSONDocument("id" -> "99E3D407")).one[Mandate] map {
    case Some(mandate) =>
      val agentPartyCopy = mandate.agentParty
      val subscriptionCopy = mandate.subscription
      val createdByCopy = mandate.createdBy
      val mandateCopy = mandate.copy(agentParty = agentPartyCopy.copy(id="", name="", contactDetails=ContactDetails("", None)),
        subscription = subscriptionCopy.copy(referenceNumber = None),
        createdBy = createdByCopy.copy(credId="", name="", groupId=None))
      val isClient = mandate.clientParty.isDefined
      Logger.error("Found mandate 99E3D407 -> withClient" + isClient + ", details: " + mandateCopy)
    case _ => Logger.error("Could not find mandate 99E3D407")
  }

  collection.find(BSONDocument("id" -> "3ABAD7FF")).one[Mandate] map {
    case Some(mandate) =>
      val agentPartyCopy = mandate.agentParty
      val subscriptionCopy = mandate.subscription
      val createdByCopy = mandate.createdBy
      val mandateCopy = mandate.copy(agentParty = agentPartyCopy.copy(id="", name="", contactDetails=ContactDetails("", None)),
        subscription = subscriptionCopy.copy(referenceNumber = None),
        createdBy = createdByCopy.copy(credId="", name="", groupId=None))
      val isClient = mandate.clientParty.isDefined
      Logger.error("Found mandate 3ABAD7FF -> withClient" + isClient + ", details: " + mandateCopy)
    case _ => Logger.error("Could not find mandate 3ABAD7FF")
  }

  collection.find(BSONDocument("id" -> "OAFBF977")).one[Mandate] map {
    case Some(mandate) =>
      val agentPartyCopy = mandate.agentParty
      val subscriptionCopy = mandate.subscription
      val createdByCopy = mandate.createdBy
      val mandateCopy = mandate.copy(agentParty = agentPartyCopy.copy(id="", name="", contactDetails=ContactDetails("", None)),
        subscription = subscriptionCopy.copy(referenceNumber = None),
        createdBy = createdByCopy.copy(credId="", name="", groupId=None))
      val isClient = mandate.clientParty.isDefined
      Logger.error("Found mandate OAFBF977 -> withClient" + isClient + ", details: " + mandateCopy)
    case _ => Logger.error("Could not find mandate OAFBF977")
  }

  // $COVERAGE-ON$
  //Temp code - end

  val metrics: Metrics = Metrics

  override def indexes: Seq[Index] = {
    Seq(
      Index(Seq("id" -> IndexType.Ascending), name = Some("idIndex"), unique = true, sparse = true),
      Index(Seq("id" -> IndexType.Ascending, "service.name" -> IndexType.Ascending), name = Some("compoundIdServiceIndex"), unique = true, sparse = true),
      Index(Seq("id" -> IndexType.Ascending, "serviceName" -> IndexType.Ascending,
        "agentPartyId" -> IndexType.Ascending, "clientSubscriptionId" -> IndexType.Ascending), name = Some("existingRelationshipIndex"), sparse = true),
      Index(Seq("id" -> IndexType.Ascending, "service.name" -> IndexType.Ascending, "clientParty.id" -> IndexType.Ascending), name = Some("compoundClientFetchIndex"), sparse = true)
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

  def fetchMandateByClient(clientId: String, service: String): Future[MandateFetchStatus] = {
    val query = Json.obj(
      "clientParty.id" -> clientId,
      "subscription.service.id" -> service.toUpperCase,
      "$or" -> Json.arr(Json.obj("currentStatus.status" -> Status.Active.toString), Json.obj("currentStatus.status" -> Status.Approved.toString), Json.obj("currentStatus.status" -> Status.Rejected.toString), Json.obj("currentStatus.status" -> Status.Cancelled.toString))
    )

    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchMandateByClient)
    collection.find(query).sort(Json.obj("_id" -> -1)).one[Mandate] map {
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
                ExistingRelationshipsInserted
            }.recover {
              case e: Throwable =>
                // $COVERAGE-OFF$
                Logger.warn("Error inserting document", e)
                ExistingRelationshipsInsertError
              // $COVERAGE-ON$
            }

          case Failure(f) =>
            // $COVERAGE-OFF$
            Logger.warn(s"[MandateRepository][insertExistingRelationships] failed: ${f.getMessage}")
            Future.successful(ExistingRelationshipsInsertError)
          // $COVERAGE-ON$
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
            ExistingRelationshipProcessed
        }.recover {
          case e: Throwable =>
            // $COVERAGE-OFF$
            Logger.warn("Error updating document", e)
            ExistingRelationshipProcessError
          // $COVERAGE-ON$
        }
      case Failure(f) =>
        // $COVERAGE-OFF$
        Logger.warn(s"[MandateRepository][existingRelationshipProcessed] failed: ${f.getMessage}")
        Future.successful(ExistingRelationshipProcessError)
      // $COVERAGE-ON$
    }
  }

  def findGGRelationshipsToProcess(): Future[Seq[GGRelationshipDto]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFindGGRelationshipsToProcess)
    val result = Try {

      val query = BSONDocument(
        "agentPartyId" -> BSONDocument("$exists" -> true),
        "processed" -> BSONDocument("$exists" -> false)
      )
      val queryResult = collection.find(query).cursor[GGRelationshipDto]().collect[Seq]()

      queryResult onComplete {
        _ => timerContext.stop()
      }

      queryResult
    }

    result match {
      case Success(s) =>
        s.map { x =>
          x
        }
      case Failure(f) =>
        // $COVERAGE-OFF$
        Logger.warn(s"[MandateRepository][findGGRelationshipsToProcess] failed: ${f.getMessage}")
        Future.successful(Nil)
      // $COVERAGE-ON$
    }
  }

  def findMandatesMissingAgentEmail(arn: String, service: String): Future[Seq[String]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFindAgentEmail)
    val query = BSONDocument(
      "agentParty.contactDetails.email" -> "",
      "agentParty.id" -> arn,
      "subscription.service.id" -> service.toUpperCase)

    val result = Try {
      val queryResult = collection.find(query).cursor[Mandate]().collect[Seq]()

      queryResult onComplete {
        _ => timerContext.stop()
      }

      queryResult
    }

    result match {
      case Success(s) =>
        s.map { x =>
          x.map { _.id }
        }
      case Failure(f) =>
        // $COVERAGE-OFF$
        Logger.warn(s"[MandateRepository][findMandatesMissingAgentEmail] failed: ${f.getMessage}")
        Future.successful(Nil)
        // $COVERAGE-ON$
    }
  }

  def updateAgentEmail(mandateIds: Seq[String], email: String): Future[MandateUpdate] = {

    val query = BSONDocument("id" -> BSONDocument("$in" -> mandateIds))
    val modifier = BSONDocument("$set" -> BSONDocument("agentParty.contactDetails.email" -> email))

    val timerContext = metrics.startTimer(MetricsEnum.RepositoryUpdateAgentEmail)

    collection.update(query, modifier, upsert = false, multi = true).map { writeResult =>
      timerContext.stop()
      writeResult.ok match {
        case true => MandateUpdatedEmail
        case _ => MandateUpdateError
      }
    }.recover {
      // $COVERAGE-OFF$
      case e => Logger.warn("Failed to update agent email", e)
        timerContext.stop()
        MandateUpdateError
      // $COVERAGE-ON$
    }
  }

  def updateClientEmail(mandateId: String, email: String): Future[MandateUpdate] = {
    val query = BSONDocument("id" -> mandateId)
    val modifier = BSONDocument("$set" -> BSONDocument("clientParty.contactDetails.email" -> email))

    val timerContext = metrics.startTimer(MetricsEnum.RepositoryUpdateClientEmail)

    collection.update(query, modifier, upsert = false, multi = false).map { writeResult =>
      timerContext.stop()
      writeResult.ok match {
        case true => MandateUpdatedEmail
        case _ => MandateUpdateError
      }
    }.recover {
      // $COVERAGE-OFF$
      case e => Logger.warn("Failed to update client email", e)
        timerContext.stop()
        MandateUpdateError
      // $COVERAGE-ON$
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
