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
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientmandate.services.{ContactDetails, Party, ClientMandate}
import uk.gov.hmrc.mongo.{Saved, DatabaseUpdate, Repository, ReactiveRepository}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import play.api.Play.current

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class ClientMandateCreated(clientMandate: ClientMandate)


trait ClientMandateRepository extends Repository[ClientMandate, BSONObjectID] {

  def insertMandate(clientMandate: ClientMandate): Future[ClientMandateCreated]

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

}
