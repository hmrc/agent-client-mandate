/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.Logger
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Sorts._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo._
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import org.mongodb.scala.result.InsertOneResult
import org.mongodb.scala.result.DeleteResult
import uk.gov.hmrc.agentclientmandate.metrics.{MetricsEnum, ServiceMetrics}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.utils.LoggerUtil.logWarn
import uk.gov.hmrc.play.http.logging.Mdc

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import com.mongodb.client.result.UpdateResult

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

trait MandateRepo {
  val repository: MandateRepository
}

trait MandateRepository extends PlayMongoRepository[Mandate] with MandateRepo {
  def insertMandate(mandate: Mandate)(implicit ec: ExecutionContext): Future[MandateCreate]
  def updateMandate(mandate: Mandate)(implicit ec: ExecutionContext): Future[MandateUpdate]
  def fetchMandate(mandateId: String)(implicit ec: ExecutionContext): Future[MandateFetchStatus]
  def fetchMandateByClient(clientId: String, service: String)(implicit ec: ExecutionContext): Future[MandateFetchStatus]
  def getAllMandatesByServiceName(arn: String,
                                  serviceName: String,
                                  credId: Option[String],
                                  otherCredId: Option[String],
                                  displayName: Option[String])(implicit ec: ExecutionContext): Future[Seq[Mandate]]
  def findMandatesMissingAgentEmail(arn: String, service: String)(implicit ec: ExecutionContext): Future[Seq[String]]
  def updateAgentEmail(mandateIds: Seq[String], email: String)(implicit ec: ExecutionContext): Future[MandateUpdate]
  def updateClientEmail(mandateId: String, email: String)(implicit ec: ExecutionContext): Future[MandateUpdate]
  def updateAgentCredId(oldCredId: String, newCredId: String)(implicit ec: ExecutionContext): Future[MandateUpdate]
  def findOldMandates(dateFrom: DateTime)(implicit ec: ExecutionContext): Future[Seq[Mandate]]
  def getClientCancelledMandates(dateFrom: DateTime, arn: String, serviceName: String)(implicit ec: ExecutionContext): Future[Seq[String]]
  def removeMandate(mandateId: String)(implicit ec: ExecutionContext): Future[MandateRemove]
  def metrics: ServiceMetrics
}

@Singleton
class MandateMongoRepository @Inject() (mongo: MongoComponent, val metrics: ServiceMetrics)(implicit ec: ExecutionContext)
  extends PlayMongoRepository[Mandate](
    collectionName = "mandates",
    mongoComponent = mongo,
    domainFormat = Mandate.formats,
    indexes = Seq(
                IndexModel(ascending("id"), IndexOptions().name("idIndex").unique(true).sparse(true)),
                IndexModel(ascending("id", "service.name"), IndexOptions().name("compoundIdServiceIndex").unique(true).sparse(true)),
                IndexModel(ascending("id","serviceName","agentPartyId","clientSubscriptionId"), IndexOptions().name("existingRelationshipIndex").sparse(true)),
                IndexModel(ascending("id", "service.name", "clientParty.id"), IndexOptions().name("compoundClientFetchIndex").sparse(true)),
                IndexModel(ascending("id", "createdBy.credId"), IndexOptions().name("agentCreatedByCredId")),
              ),
    extraCodecs = Seq(Codecs.playFormatCodec(User.formats),
                      Codecs.playFormatCodec(Party.formats),
                      Codecs.playFormatCodec(Service.formats),
                      Codecs.playFormatCodec(Status.enumFormat),
                      Codecs.playFormatCodec(MandateStatus.formats),
                      Codecs.playFormatCodec(Subscription.formats)),
    replaceIndexes = true)
    with MandateRepository {

  val logger: Logger = Logger(getClass)
  val repository: MandateRepository = this

  def insertMandate(mandate: Mandate)(implicit ec: ExecutionContext): Future[MandateCreate] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryInsertMandate)
    Mdc.preservingMdc {
      collection
        .insertOne(mandate)
        .toFutureOption()
    }.map { result =>
      (result: @unchecked) match {
        case Some(res: InsertOneResult) if res.wasAcknowledged =>
          timerContext.stop()
          MandateCreated(mandate)
        case None =>
          timerContext.stop()
          MandateCreateError
      }
    }.recover {
      case e => logger.warn("Failed to insert mandate", e)
        timerContext.stop()
        MandateCreateError
    }
  }

  def updateMandate(mandate: Mandate)(implicit ec: ExecutionContext): Future[MandateUpdate] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryUpdateMandate)
    Mdc.preservingMdc {
      collection
        .replaceOne(equal("id", mandate.id), mandate, ReplaceOptions().upsert(false))
        .toFutureOption()
    }.map {
      case Some(res: UpdateResult) if res.wasAcknowledged =>
        timerContext.stop()
        MandateUpdated(mandate)
      case _ =>
        timerContext.stop()
        MandateUpdateError
    }.recover {
      case e => logWarn("Failed to update mandate", e)
        timerContext.stop()
        MandateUpdateError
    }

  }

  def fetchMandate(mandateId: String)(implicit ec: ExecutionContext): Future[MandateFetchStatus] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchMandate)
    Mdc.preservingMdc {
      collection
        .find(equal("id", mandateId))
        .headOption()
        .map{
          case Some(mandate) =>
            timerContext.stop()
            MandateFetched(mandate)
          case _ =>
            timerContext.stop()
            MandateNotFound
      }
    }
  }

  def fetchMandateByClient(clientId: String, service: String)(implicit ec: ExecutionContext): Future[MandateFetchStatus] = {
    val query = and(
      equal("clientParty.id", clientId),
      equal("subscription.service.id", service.toUpperCase),
      or(equal("currentStatus.status", Status.Active.toString),
         equal("currentStatus.status", Status.Approved.toString),
         equal("currentStatus.status", Status.Rejected.toString),
         equal("currentStatus.status", Status.Cancelled.toString))
    )

    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchMandateByClient)
    Mdc.preservingMdc {
      collection
        .find(query)
        .sort(orderBy(descending("_id")))
        .headOption()
        .map {
          case Some(mandate) =>
            timerContext.stop()
            MandateFetched(mandate)
          case _ =>
            timerContext.stop()
            MandateNotFound
       }
    }
  }

  def getAllMandatesByServiceName(arn: String,
                                  serviceName: String,
                                  credId: Option[String],
                                  otherCredId: Option[String],
                                  displayName: Option[String])
                                 (implicit ec: ExecutionContext): Future[Seq[Mandate]] = {
    val query = if (credId.isDefined && otherCredId.isDefined) {
      and(
        equal("agentParty.id", arn),
        equal("subscription.service.name", serviceName.toLowerCase),
        or(equal("createdBy.credId", credId.get), equal("createdBy.credId", otherCredId.get))
      )
    } else {
      and(
        equal("agentParty.id", arn),
        equal("subscription.service.name", serviceName.toLowerCase)
      )
    }
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFetchMandatesByService)
    val result = Mdc.preservingMdc {
      collection
        .find(query)
        .sort(orderBy(ascending("clientDisplayName")))
        .collect()
        .toFutureOption()
        .map {
          case None => Nil
          case Some(mandates) if displayName.isDefined =>
            mandates.filter(mandate =>
              mandate.currentStatus.status != Status.Active ||
             (mandate.currentStatus.status == Status.Active && mandate.clientDisplayName.toLowerCase.contains(displayName.get.toLowerCase))
            )
          case Some(mandates) => mandates
      }
    }

    result onComplete {
      _ => timerContext.stop()
    }
    result
  }

  def findMandatesMissingAgentEmail(arn: String, service: String)(implicit ec: ExecutionContext): Future[Seq[String]] = {
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFindAgentEmail)
    val query = and(
      equal("agentParty.contactDetails.email", ""),
      equal("agentParty.id", arn),
      equal("subscription.service.id", service.toUpperCase)
    )

    val result = Try {
      val queryResult = Mdc.preservingMdc {
        collection
          .find(query)
          .collect()
          .toFutureOption()
          .map{
            case None => Nil
            case Some(mandates) => mandates
          }
      }

      queryResult onComplete {
        _ => timerContext.stop()
      }

      queryResult
    }

    result match {
      case Success(s) =>
        s.map { x =>
          x.map {
            _.id
          }
        }
      case Failure(f) =>
        logWarn(s"[MandateRepository][findMandatesMissingAgentEmail] failed: ${f.getMessage}")
        Future.successful(Nil)
    }
  }

  def updateAgentEmail(mandateIds: Seq[String], email: String)(implicit ec: ExecutionContext): Future[MandateUpdate] = {
    val query = in("id", mandateIds)
    val modifier = set("agentParty.contactDetails.email", email)
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryUpdateAgentEmail)

    Mdc.preservingMdc {
      collection
        .updateMany(query, modifier)
        .toFutureOption()
    }.map {
      case Some(result) if result.wasAcknowledged =>
        timerContext.stop()
        MandateUpdatedEmail
      case _ =>
        timerContext.stop()
        MandateUpdateError
    }.recover {
      case e => logWarn("Failed to update agent email", e)
        timerContext.stop()
        MandateUpdateError
    }
  }

  def updateClientEmail(mandateId: String, email: String)(implicit ec: ExecutionContext): Future[MandateUpdate] = {
    val query = equal("id", mandateId)
    val modifier = set("clientParty.contactDetails.email", email)
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryUpdateClientEmail)

    Mdc.preservingMdc {
      collection
        .findOneAndUpdate(query, modifier, FindOneAndUpdateOptions().upsert(false))
        .toFutureOption()
    }.map {
      case Some(_) =>
        timerContext.stop()
        MandateUpdatedEmail
      case _ =>
        timerContext.stop()
        MandateUpdateError
    }.recover {
      case e => logWarn("Failed to update client email", e)
        timerContext.stop()
        MandateUpdateError
    }
  }

  def updateAgentCredId(oldCredId: String, newCredId: String)(implicit ec: ExecutionContext): Future[MandateUpdate] = {
    val query = equal("createdBy.credId", oldCredId)
    val modifier = set("createdBy.credId", newCredId)
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryUpdateAgentCredId)

    Mdc.preservingMdc {
      collection
        .updateMany(query, modifier)
        .toFutureOption()
    }.map {
      case Some(result) if result.wasAcknowledged =>
        timerContext.stop()
        MandateUpdatedCredId
      case _ =>
        timerContext.stop()
        MandateUpdateError
    }.recover {
      case e => logWarn("Failed to update agent cred id", e)
        timerContext.stop()
        MandateUpdateError
    }
  }

  def findOldMandates(dateFrom: DateTime)(implicit ec: ExecutionContext): Future[Seq[Mandate]] = {
    val query = and(
      lt("currentStatus.timestamp", dateFrom.getMillis),
      or(equal("currentStatus.status", Status.New.toString),
         equal("currentStatus.status", Status.Approved.toString),
         equal("currentStatus.status", Status.PendingCancellation.toString),
         equal("currentStatus.status", Status.PendingActivation.toString))
    )
    val timerContext = metrics.startTimer(MetricsEnum.RepositoryFindOldMandates)

    val result = Mdc.preservingMdc {
      collection
        .find(query)
        .collect()
        .toFutureOption()
        .map{
          case None => Seq.empty
          case Some(res) => res
        }
    }

    result onComplete {
      _ => timerContext.stop()
    }
    result
  }

  def getClientCancelledMandates(dateFrom: DateTime, arn: String, serviceName: String)(implicit ec: ExecutionContext): Future[Seq[String]] = {
    val query = and(
      equal("agentParty.id", arn),
      equal("subscription.service.name", serviceName.toLowerCase),
      gt("currentStatus.timestamp", dateFrom.getMillis),
      equal("currentStatus.status", Status.Cancelled.toString),
      where("this.createdBy.credId != this.currentStatus.updatedBy")
    )

    metrics.startTimer(MetricsEnum.RepositoryClientCancelledMandates)
    val result = Try(Mdc.preservingMdc {
      collection
        .find(query)
        .collect()
        .toFutureOption()
        .map{
          case None => Seq.empty
          case Some(res) => res
        }
    })

    result match {
      case Success(s) =>
        s.map { x =>
          x.map {
            _.clientDisplayName
          }
        }
      case Failure(f) =>
        logWarn(s"[MandateRepository][getClientCancelledMandates] failed: ${f.getMessage}")
        Future.successful(Nil)
    }
  }

  // $COVERAGE-OFF$

  def removeMandate(mandateId: String)(implicit ec: ExecutionContext): Future[MandateRemove] = {
    val query = equal("id", mandateId)
    Mdc.preservingMdc {
      collection
      .deleteOne(query)
      .toFutureOption()
    }.map {
      case Some(result: DeleteResult) if result.getDeletedCount > 0 => MandateRemoved
      case _ => MandateRemoveError
    }.recover {
      case e => logWarn("Failed to delete mandate", e)
        MandateRemoveError
    }
  }

  // $COVERAGE-ON$
}
