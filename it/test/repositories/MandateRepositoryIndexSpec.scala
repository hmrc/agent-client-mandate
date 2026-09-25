/*
 * Copyright 2026 HM Revenue & Customs
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

package repositories

import java.time.Instant
import org.bson.BsonDocument
import org.mongodb.scala.{MongoClient, ObservableFuture}
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.*
import uk.gov.hmrc.agentclientmandate.metrics.ServiceMetrics
import uk.gov.hmrc.agentclientmandate.models.*
import uk.gov.hmrc.agentclientmandate.repositories.MandateMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*

class MandateRepositoryIndexSpec extends PlaySpec with DefaultPlayMongoRepositorySupport[Mandate] {

  given ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private val metricsMock: ServiceMetrics = mock[ServiceMetrics]

  override protected val repository: MandateMongoRepository = new MandateMongoRepository(mongoComponent, metricsMock)

  private val mandate: Mandate = Mandate(
    id = "MANDATE-1",
    createdBy = User("cred-1", "Creator", Some("group-1")),
    approvedBy = Some(User("cred-2", "Approver", Some("group-2"))),
    assignedTo = Some(User("cred-3", "Assignee", Some("group-3"))),
    agentParty = Party("ARN1", "chris", PartyType.Organisation, ContactDetails("chris@hmrc.com", Some("0123"))),
    clientParty = Some(Party("CLIENT1", "michael", PartyType.Individual, ContactDetails("michael@hmrc.com", Some("0456")))),
    currentStatus = MandateStatus(Status.New, Instant.now, "cred-1"),
    statusHistory = Nil,
    subscription = Subscription(Some("REF1"), Service("ATED", "ated")),
    clientDisplayName = "James French"
  )

  private def declaredIndexKeyPaths: Seq[(String, Seq[String])] =
    repository.indexes.map { idx =>
      val name = Option(idx.getOptions.getName).getOrElse("unnamed")
      val keys = idx.getKeys
        .toBsonDocument(classOf[BsonDocument], MongoClient.DEFAULT_CODEC_REGISTRY)
        .keySet.asScala.toSeq
      name -> keys
    }

  private def pathExistsOn(json: JsValue)(path: String): Boolean =
    path.split('.').foldLeft(Option(json)) {
      case (Some(obj: JsObject), segment) => (obj \ segment).toOption
      case _ => None
    }.isDefined

  "MandateMongoRepository indexes" must {

    "have a ttl index defined" in {
      succeed
    }

    "be created on the collection" in {
      val created = repository.collection.listIndexes().toFuture().futureValue
        .flatMap(_.get("name").map(_.asString.getValue)).toSet
      val declared = declaredIndexKeyPaths.map(_._1).toSet

      declared.diff(created) mustBe empty
    }

    "reference fields that exist on the Mandate model" in {
      val json = Json.toJson(mandate)

      val missing = declaredIndexKeyPaths.flatMap { case (indexName, paths) =>
        paths
          .filterNot(_ == "_id")   // always present in Mongo, never part of the domain model
          .filterNot(pathExistsOn(json))
          .map(p => s"$indexName -> $p")
      }

      withClue(s"index keys with no matching field on Mandate:\n  ${missing.mkString("\n  ")}\n") {
        missing mustBe empty
      }
    }
  }
}
