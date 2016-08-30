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

package uk.gov.hmrc.agentclientmandate

import play.modules.reactivemongo.MongoDbConnection
import play.modules.reactivemongo.ReactiveMongoPlugin
import reactivemongo.api.{DB, DefaultDB}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.agentclientmandate.services.{ClientMandate, ContactDetails, Party}
import uk.gov.hmrc.mongo.{DatabaseUpdate, ReactiveRepository, Repository, Saved}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import play.api.Play.current
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientmandate.models.Id
import uk.gov.hmrc.play.http.NotFoundException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class ClientMandateCreated(clientMandate: ClientMandate)

case class ClientMandateFetched(clientMandate: ClientMandate)

object ClientMandateFetched {
  implicit val formats = Json.format[ClientMandateFetched]
}

trait ClientMandateRepository extends Repository[ClientMandate, BSONObjectID] {

  def insertMandate(clientMandate: ClientMandate): Future[ClientMandateCreated]

  def fetchMandate(mandateId: String): Future[ClientMandateFetched]

}

object ClientMandateRepository extends MongoDbConnection {

  private lazy val clientMandateRepository = new ClientMandateMongoRepository

  def apply(): ClientMandateRepository = clientMandateRepository

}

class ClientMandateMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[ClientMandate, BSONObjectID]("clientMandates", mongo, ClientMandate.formats, ReactiveMongoFormats.objectIdFormats)
    with ClientMandateRepository {


  def insertMandate(clientMandate: ClientMandate) = {
    collection.insert[ClientMandate](clientMandate).map {
      wr =>
        ClientMandateCreated(clientMandate)
    }
  }

  def fetchMandate(mandateId: String): Future[ClientMandateFetched] = {
    val query = BSONDocument(
      "id" -> mandateId
    )
    collection.find(query).one[ClientMandate] map {
      case Some(clientMandate) => ClientMandateFetched(clientMandate)
      case _ => throw new NotFoundException(s"Client Mandate not found for id $mandateId")
    }
  }

}
