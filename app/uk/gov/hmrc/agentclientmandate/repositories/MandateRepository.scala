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

import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DB
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.agentclientmandate.models.Mandate
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class MandateCreated(mandate: Mandate)

sealed trait MandateUpdate

case class MandateUpdated(mandate: Mandate) extends MandateUpdate

case object MandateUpdateError extends MandateUpdate

sealed trait MandateFetchStatus

case class MandateFetched(mandate: Mandate) extends MandateFetchStatus

case object MandateNotFound extends MandateFetchStatus

trait MandateRepository extends Repository[Mandate, BSONObjectID] {

  def insertMandate(mandate: Mandate): Future[MandateCreated]

  def updateMandate(mandate: Mandate): Future[MandateUpdate]

  def fetchMandate(mandateId: String): Future[MandateFetchStatus]

  def getAllMandatesByServiceName(arn: String, serviceName: String): Future[Seq[Mandate]]

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
      Index(Seq("id" -> IndexType.Ascending, "service.name" -> IndexType.Ascending), name = Some("compoundIdServiceIndex"), unique = true, sparse = true)
    )
  }

  def insertMandate(mandate: Mandate): Future[MandateCreated] = {
    collection.insert[Mandate](mandate).map(writeResult => MandateCreated(mandate))
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
      "subscription.service.name" -> serviceName
    )
    collection.find(query).cursor[Mandate].collect[Seq]()
  }

}
