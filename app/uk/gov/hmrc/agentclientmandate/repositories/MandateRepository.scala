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

package uk.gov.hmrc.agentclientmandate.repositories

import javax.inject.Inject
import org.joda.time.DateTime
import play.api.{Logger, Play}
import play.api.libs.json.Json
import play.modules.reactivemongo.{MongoDbConnection, ReactiveMongoComponent}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, DB}
import reactivemongo.bson.{BSONArray, BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.agentclientmandate.metrics.{MetricsEnum, ServiceMetrics}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

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
case object MandateUpdatedCredId extends MandateUpdate

sealed trait MandateFetchStatus
case class MandateFetched(mandate: Mandate) extends MandateFetchStatus
case object MandateNotFound extends MandateFetchStatus

sealed trait MandateRemove
case object MandateRemoved extends MandateRemove
case object MandateRemoveError extends MandateRemove

class MandateRepositoryImpl @Inject()(val mongo: ReactiveMongoComponent,
                                      val serviceMetrics: ServiceMetrics) extends MandateRepo

trait MandateRepo {
  val mongo: ReactiveMongoComponent
  val serviceMetrics: ServiceMetrics

  lazy val repository: MandateRepository = new MandateMongoRepository(mongo.mongoConnector.db, serviceMetrics)
}

trait MandateRepository extends ReactiveRepository[Mandate, BSONObjectID] {
  def insertMandate(mandate: Mandate): Future[MandateCreate]
  def updateMandate(mandate: Mandate): Future[MandateUpdate]
  def fetchMandate(mandateId: String): Future[MandateFetchStatus]
  def fetchMandateByClient(clientId: String, service: String): Future[MandateFetchStatus]
  def getAllMandatesByServiceName(arn: String,
                                  serviceName: String,
                                  credId: Option[String],
                                  otherCredId: Option[String],
                                  displayName: Option[String]): Future[Seq[Mandate]]
  def findMandatesMissingAgentEmail(arn: String, service: String): Future[Seq[String]]
  def updateAgentEmail(mandateIds: Seq[String], email: String): Future[MandateUpdate]
  def updateClientEmail(mandateId: String, email: String): Future[MandateUpdate]
  def updateAgentCredId(oldCredId: String, newCredId: String): Future[MandateUpdate]
  def findOldMandates(dateFrom: DateTime): Future[Seq[Mandate]]
  def getClientCancelledMandates(dateFrom: DateTime, arn: String, serviceName: String): Future[Seq[String]]
  def removeMandate(mandateId: String): Future[MandateRemove]
  def metrics: ServiceMetrics
}

class MandateMongoRepository (mongo: () => DB, val metrics: ServiceMetrics)
  extends ReactiveRepository[Mandate, BSONObjectID]("mandates", mongo, Mandate.formats, ReactiveMongoFormats.objectIdFormats)
    with MandateRepository {

  //Temporary code and should be removed after next deployment - start

  collection.update(BSONDocument(
    "currentStatus.status" -> "PendingActivation",
    "statusHistory.status" -> "Approved"
  ), BSONDocument(
    "$set" -> BSONDocument("currentStatus.status" -> "Approved")
  ), upsert=false, multi=true)

  collection.remove(BSONDocument("processed" -> BSONDocument("$exists" -> true)))

  //Temp code - end

  override def indexes: Seq[Index] = {
    Seq(
      Index(Seq("id" -> IndexType.Ascending), name = Some("idIndex"), unique = true, sparse = true),
      Index(Seq("id" -> IndexType.Ascending,
        "service.name" -> IndexType.Ascending), name = Some("compoundIdServiceIndex"), unique = true, sparse = true),
      Index(Seq("id" -> IndexType.Ascending,
        "serviceName" -> IndexType.Ascending,
        "agentPartyId" -> IndexType.Ascending,
        "clientSubscriptionId" -> IndexType.Ascending), name = Some("existingRelationshipIndex"), sparse = true),
      Index(Seq("id" -> IndexType.Ascending,
        "service.name" -> IndexType.Ascending,
        "clientParty.id" -> IndexType.Ascending), name = Some("compoundClientFetchIndex"), sparse = true),
      Index(Seq("id" -> IndexType.Ascending,
        "createdBy.credId" -> IndexType.Ascending), name = Some("agentCreatedByCredId"))
    )
  }


  def insertMandate(mandate: Mandate): Future[MandateCreate] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryInsertMandate)
    collection.insert[Mandate](mandate).map { writeResult =>
      timerContext.stop()
      if (writeResult.ok) {
        MandateCreated(mandate)
      } else {
        MandateCreateError
      }
    }.recover {
      case e => Logger.warn("Failed to insert mandate", e)
        timerContext.stop()
        MandateCreateError
    }
  }

  def updateMandate(mandate: Mandate): Future[MandateUpdate] = {
    val query = BSONDocument(
      "id" -> mandate.id
    )
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryUpdateMandate)
    collection.update(query, mandate, upsert = false).map { writeResult =>
      timerContext.stop()
      if (writeResult.ok) {
        MandateUpdated(mandate)
      } else {
        MandateUpdateError
      }
    }.recover {
      case e => Logger.warn("Failed to update mandate", e)
        timerContext.stop()
        MandateUpdateError
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
        timerContext.stop()
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
        timerContext.stop()
        MandateNotFound
    }
  }

  def getAllMandatesByServiceName(arn: String,
                                  serviceName: String,
                                  credId: Option[String],
                                  otherCredId: Option[String],
                                  displayName: Option[String]): Future[Seq[Mandate]] = {
    val query = if (credId.isDefined && otherCredId.isDefined) {
      BSONDocument(
        "agentParty.id" -> arn,
        "subscription.service.name" -> serviceName.toLowerCase,
        "$or" -> BSONArray(BSONDocument("createdBy.credId" -> credId.get), BSONDocument("createdBy.credId" -> otherCredId.get))
      )
    } else {
      BSONDocument(
        "agentParty.id" -> arn,
        "subscription.service.name" -> serviceName.toLowerCase
      )
    }
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchMandatesByService)
    val result = collection.find(query).sort(Json.obj("clientDisplayName" -> 1)).cursor[Mandate]().collect[Seq](maxDocs = -1, Cursor.FailOnError()).map {
      x => if (displayName.isDefined) {
        x.filter(mandate =>
          mandate.currentStatus.status != Status.Active ||
            (mandate.currentStatus.status == Status.Active && mandate.clientDisplayName.toLowerCase.contains(displayName.get.toLowerCase))
        )
      }
      else {
        x
      }
    }

    result onComplete {
      _ => timerContext.stop()
    }
    result
  }

  def findMandatesMissingAgentEmail(arn: String, service: String): Future[Seq[String]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFindAgentEmail)
    val query = BSONDocument(
      "agentParty.contactDetails.email" -> "",
      "agentParty.id" -> arn,
      "subscription.service.id" -> service.toUpperCase)

    val result = Try {
      val queryResult = collection.find(query).cursor[Mandate]().collect[Seq](maxDocs = -1, Cursor.FailOnError())

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
        Logger.warn(s"[MandateRepository][findMandatesMissingAgentEmail] failed: ${f.getMessage}")
        Future.successful(Nil)
    }
  }

  def updateAgentEmail(mandateIds: Seq[String], email: String): Future[MandateUpdate] = {

    val query = BSONDocument("id" -> BSONDocument("$in" -> mandateIds))
    val modifier = BSONDocument("$set" -> BSONDocument("agentParty.contactDetails.email" -> email))

    val timerContext = metrics.startTimer(MetricsEnum.RepositoryUpdateAgentEmail)

    collection.update(query, modifier, upsert = false, multi = true).map { writeResult =>
      timerContext.stop()
      if (writeResult.ok) {
        MandateUpdatedEmail
      } else {
        MandateUpdateError
      }
    }.recover {
      case e => Logger.warn("Failed to update agent email", e)
        timerContext.stop()
        MandateUpdateError
    }
  }

  def updateClientEmail(mandateId: String, email: String): Future[MandateUpdate] = {
    val query = BSONDocument("id" -> mandateId)
    val modifier = BSONDocument("$set" -> BSONDocument("clientParty.contactDetails.email" -> email))

    val timerContext = metrics.startTimer(MetricsEnum.RepositoryUpdateClientEmail)

    collection.update(query, modifier, upsert = false, multi = false).map { writeResult =>
      timerContext.stop()
      if (writeResult.ok) {
        MandateUpdatedEmail
      } else {
        MandateUpdateError
      }
    }.recover {
      case e => Logger.warn("Failed to update client email", e)
        timerContext.stop()
        MandateUpdateError
    }
  }

  def updateAgentCredId(oldCredId: String, newCredId: String): Future[MandateUpdate] = {
    val query = BSONDocument("createdBy.credId" -> oldCredId)
    val modifier = BSONDocument("$set" -> BSONDocument("createdBy.credId" -> newCredId))

    val timerContext = metrics.startTimer(MetricsEnum.RepositoryUpdateAgentCredId)

    collection.update(query, modifier, upsert = false, multi = true).map { writeResult =>
      timerContext.stop()
      if (writeResult.ok) {
        MandateUpdatedCredId
      } else {
        MandateUpdateError
      }
    }.recover {
      case e => Logger.warn("Failed to update agent cred id", e)
        timerContext.stop()
        MandateUpdateError
    }
  }


  def findOldMandates(dateFrom: DateTime): Future[Seq[Mandate]] = {
    val query = BSONDocument(
      "currentStatus.timestamp" -> BSONDocument("$lt" -> dateFrom.getMillis),
      "$or" -> Json.arr(Json.obj("currentStatus.status" -> Status.New.toString), Json.obj("currentStatus.status" -> Status.Approved.toString), Json.obj("currentStatus.status" -> Status.PendingCancellation.toString), Json.obj("currentStatus.status" -> Status.PendingActivation.toString))
    )
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFindOldMandates)
    val result = collection.find(query).cursor[Mandate]().collect[Seq](maxDocs = -1, Cursor.FailOnError())

    result onComplete {
      _ => timerContext.stop()
    }
    result
  }


  def getClientCancelledMandates(dateFrom: DateTime, arn: String, serviceName: String): Future[Seq[String]] = {
    val query = BSONDocument(
      "agentParty.id" -> arn,
      "subscription.service.name" -> serviceName.toLowerCase,
      "currentStatus.timestamp" -> BSONDocument("$gt" -> dateFrom.getMillis),
      "currentStatus.status" -> Status.Cancelled.toString,
      "$where"-> "this.createdBy.credId != this.currentStatus.updatedBy"
    )

    val timerContext = metrics.startTimer(MetricsEnum.RepositoryClientCancelledMandates)
    val result = Try(collection.find(query).cursor[Mandate]().collect[Seq](maxDocs = -1, Cursor.FailOnError()))

    result match {
      case Success(s) =>
        s.map { x =>
          x.map { _.clientDisplayName }
        }
      case Failure(f) =>
        Logger.warn(s"[MandateRepository][getClientCancelledMandates] failed: ${f.getMessage}")
        Future.successful(Nil)
    }
  }

  def removeMandate(mandateId: String): Future[MandateRemove] = {
    val query = BSONDocument("id" -> mandateId)

    collection.remove(query).map { writeResult =>
      if (writeResult.ok) {
        MandateRemoved
      } else {
        MandateRemoveError
      }
    }.recover {
      case e => Logger.warn("Failed to delete mandate", e)
        MandateRemoveError
    }
  }

}
