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
import reactivemongo.api.indexes.{IndexType, Index}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.agentclientmandate.models.ClientMandate
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class ClientMandateCreated(clientMandate: ClientMandate)

sealed trait ClientMandateFetchStatus

case class ClientMandateFetched(clientMandate: ClientMandate) extends ClientMandateFetchStatus

case object ClientMandateNotFound extends ClientMandateFetchStatus

trait ClientMandateRepository extends Repository[ClientMandate, BSONObjectID] {

  def insertMandate(clientMandate: ClientMandate): Future[ClientMandateCreated]

  def fetchMandate(mandateId: String): Future[ClientMandateFetchStatus]

  def getAllMandatesByServiceName(arn: String, serviceName: String): Future[List[ClientMandate]]

}

object ClientMandateRepository extends MongoDbConnection {

  private lazy val clientMandateRepository = new ClientMandateMongoRepository

  def apply(): ClientMandateRepository = clientMandateRepository

}

class ClientMandateMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[ClientMandate, BSONObjectID]("clientMandates", mongo, ClientMandate.formats, ReactiveMongoFormats.objectIdFormats)
    with ClientMandateRepository {

  override def indexes: Seq[Index] = {
    Seq(
      Index(Seq("id" -> IndexType.Ascending), name = Some("idIndex"), unique = true, sparse = true),
      Index(Seq("id" -> IndexType.Ascending, "service.name" -> IndexType.Ascending), name = Some("compoundIdServiceIndex"), unique = true, sparse = true)
    )
  }

  def insertMandate(clientMandate: ClientMandate): Future[ClientMandateCreated] = {
    collection.insert[ClientMandate](clientMandate).map {
      wr =>
        ClientMandateCreated(clientMandate)
    }
  }

  def fetchMandate(mandateId: String): Future[ClientMandateFetchStatus] = {
    val query = BSONDocument(
      "id" -> mandateId
    )
    collection.find(query).one[ClientMandate] map {
      case Some(clientMandate) => ClientMandateFetched(clientMandate)
      case _ => ClientMandateNotFound
    }
  }

  def getAllMandatesByServiceName(arn: String, serviceName: String): Future[List[ClientMandate]] = {
    val query = BSONDocument(
      "party.id" -> arn,
      "service.name" -> serviceName
    )
    collection.find(query).cursor[ClientMandate].collect[List]().map {
      case mandateList: List[ClientMandate] => mandateList
      case Nil => Nil
    }

  }

}
