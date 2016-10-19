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

import org.joda.time.DateTime
import org.mockito.Matchers
import org.scalatest.BeforeAndAfterEach
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.iteratee.Enumerator
import play.api.test.Helpers._
import reactivemongo.api.{Cursor, DB}
import reactivemongo.api.commands.{MultiBulkWriteResult, UpdateWriteResult}
import reactivemongo.json.collection.{JSONCollection, JSONQueryBuilder}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.mongo.MongoSpecSupport
import reactivemongo.api.indexes.CollectionIndexesManager
import reactivemongo.bson.BSONDocument

import scala.collection.generic.CanBuildFrom
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class MandateRepositorySpec extends PlaySpec with MongoSpecSupport with OneServerPerSuite with BeforeAndAfterEach with MockitoSugar {

  "MandateRepository" should {

    "save a client mandate to the repo" when {

      "a new client mandate object is passed" in {
        await(testMandateRepository.insertMandate(mandate))

        await(testMandateRepository.findAll()).head must be(mandate)
        await(testMandateRepository.count) must be(1)
      }

      "insert results in error" in {
        when(mockCollection.indexesManager.create(Matchers.any())).thenReturn(Future.successful(UpdateWriteResult(true,0,0,Nil,Nil,None,None,None)))
        when(mockCollection.insert(Matchers.any(),Matchers.any())(Matchers.any(),Matchers.any())).thenReturn(Future.successful(UpdateWriteResult(false,0,0,Nil,Nil,None,None,None)))
        val testRepository = new TestMandateRepository
        val result = await(testRepository.insertMandate(mandate))

        result mustBe MandateCreateError
      }
    }

    "update a client mandate in the repo" when {

      "a client mandate to update is passed" in {
        await(testMandateRepository.insertMandate(mandate))

        await(testMandateRepository.updateMandate(updatedMandate)) must be(MandateUpdated(updatedMandate))
        await(testMandateRepository.findAll()).head must be(updatedMandate)
        await(testMandateRepository.count) must be(1)

      }

    }

    "get a client mandate from the repo" when {

      "the correct mandate id is passed" in {
        await(testMandateRepository.insertMandate(mandate))

        await(testMandateRepository.findAll()).head must be(mandate)
        await(testMandateRepository.count) must be(1)
        await(testMandateRepository.fetchMandate(mandate.id)) must be(MandateFetched(mandate))
      }

    }

    "get a list of client mandates from the repo" when {

      "the arn and service name are correct" in {
        await(testMandateRepository.insertMandate(mandate))

        await(testMandateRepository.findAll()).head must be(mandate)
        await(testMandateRepository.count) must be(1)

        await(testMandateRepository.getAllMandatesByServiceName("JARN123456", "ated")) must be(List(mandate))
      }

    }

    "return an empty list" when {

      "the arn and service does not match" in {
        await(testMandateRepository.insertMandate(mandate))

        await(testMandateRepository.findAll()).head must be(mandate)
        await(testMandateRepository.count) must be(1)

        await(testMandateRepository.getAllMandatesByServiceName("JARN123456", "ATED")) must be(List(mandate))
      }
    }

    "insert existing relationships" must {
      "insert successfully" in {
        val existingRelationships = List(GGRelationshipDto("aaa", "bbb", "ccc", "ddd", "eee"), GGRelationshipDto("zzz", "yyy", "xxx", "www", "vvv"))

        await(testMandateRepository.insertExistingRelationships(existingRelationships)) must be(ExistingRelationshipsInserted)
      }

      "agent already exists" in {
        val existingRelationships = List(GGRelationshipDto("aaa", "bbb", "ccc", "ddd", "eee"), GGRelationshipDto("zzz", "yyy", "xxx", "www", "vvv"))
        await(testMandateRepository.insertExistingRelationships(existingRelationships))

        val existingRelationshipsAgain = List(GGRelationshipDto("aaa", "bbb", "ccc", "ddd", "eee"))
        await(testMandateRepository.insertExistingRelationships(existingRelationshipsAgain)) must be (ExistingRelationshipsAlreadyExist)

      }

      "return error when insert fails" in {
        val existingRelationships = List(GGRelationshipDto("aaa", "bbb", "ccc", "ddd", "eee"), GGRelationshipDto("zzz", "yyy", "xxx", "www", "vvv"))

        setupFindMock
        when(mockCollection.indexesManager.create(Matchers.any())).thenReturn(Future.successful(UpdateWriteResult(true,0,0,Nil,Nil,None,None,None)))
        when(mockCollection.ImplicitlyDocumentProducer).thenThrow(new scala.RuntimeException)
        when(mockCollection.bulkInsert(Matchers.any())(Matchers.any(), Matchers.any())).thenReturn(Future.successful(MultiBulkWriteResult(false, 0, 0, Nil, Nil, None, None, None, 0)))
        val testRepository = new TestMandateRepository
        val result = await(testRepository.insertExistingRelationships(existingRelationships))

        result mustBe ExistingRelationshipsInsertError
      }
    }

    "check for existing agent already inserted" must {
      "agent already exists" in {
        val existingRelationships = List(GGRelationshipDto("aaa", "bbb", "ccc", "ddd", "eee"), GGRelationshipDto("zzz", "yyy", "xxx", "www", "vvv"))

        await(testMandateRepository.insertExistingRelationships(existingRelationships)) must be(ExistingRelationshipsInserted)

        await(testMandateRepository.agentAlreadyInserted("bbb")) must be (ExistingAgentFound)
      }

      "agent does not exist" in {
        val existingRelationships = List(GGRelationshipDto("aaa", "bbb", "ccc", "ddd", "eee"), GGRelationshipDto("zzz", "yyy", "xxx", "www", "vvv"))

        await(testMandateRepository.insertExistingRelationships(existingRelationships)) must be(ExistingRelationshipsInserted)

        await(testMandateRepository.agentAlreadyInserted("ttt")) must be (ExistingAgentNotFound)
      }
    }

    "mark existing relationship processed" must {
      "update relationship successfully with processed true" in {
        val relationship = GGRelationshipDto("aaa", "bbb", "ccc", "ddd", "eee")
        val existingRelationships = List(relationship)

        await(testMandateRepository.insertExistingRelationships(existingRelationships)) must be(ExistingRelationshipsInserted)

        await(testMandateRepository.existingRelationshipProcessed(relationship)) must be(ExistingRelationshipProcessed)
      }

      "failure in updating relationship must be handled" in {
        val relationship = GGRelationshipDto("aaa", "bbb", "ccc", "ddd", "eee")
        val existingRelationships = List(relationship)

        when(mockCollection.indexesManager.create(Matchers.any())).thenReturn(Future.successful(UpdateWriteResult(true,0,0,Nil,Nil,None,None,None)))
        when(mockCollection.update(Matchers.any(),Matchers.any(),Matchers.any(),Matchers.any(),Matchers.any())(Matchers.any(),Matchers.any(),Matchers.any())).thenThrow(new RuntimeException(""))
        val testRepository = new TestMandateRepository
        val result = await(testRepository.existingRelationshipProcessed(relationship))

        result mustBe ExistingRelationshipProcessError
      }
    }

  }

  def testMandateRepository(implicit mongo: () => DB) = new MandateMongoRepository

  def mandate: Mandate =
    Mandate("AS12345678", createdBy = User("credid", "name", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, contactDetails = ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = None,
      currentStatus = MandateStatus(Status.New, new DateTime(1472631804869L), "credidupdate"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ated"))
    )

  def updatedMandate: Mandate =
    Mandate("AS12345678", createdBy = User("credid", "name", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, contactDetails = ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = Some(Party("XBAT00000123456", "Joe Ated", PartyType.Organisation, contactDetails = ContactDetails("", None))),
      currentStatus = MandateStatus(Status.Active, new DateTime(1472631805678L), "credidclientupdate"),
      statusHistory = Seq(MandateStatus(Status.New, new DateTime(1472631804869L), "credidupdate")),
      subscription = Subscription(Some("XBAT00000123456"), Service("ated", "ATED"))
    )

  def mandate1: Mandate =
    Mandate("AS12345678", createdBy = User("credid", "name", None),
      agentParty = Party("JARN123457", "John Snow", PartyType.Organisation, contactDetails = ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = None,
      currentStatus = MandateStatus(Status.New, new DateTime(1472631804869L), "credidupdate"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ated", "ATED"))
    )

  val mockCollection = mock[JSONCollection]

  private def setupIndexesManager: CollectionIndexesManager = {
    val mockIndexesManager = mock[CollectionIndexesManager]
    when(mockCollection.indexesManager).thenReturn(mockIndexesManager)
    when(mockIndexesManager.dropAll) thenReturn Future.successful(0)
    mockIndexesManager
  }

  override def beforeEach(): Unit = {
    await(testMandateRepository.drop)
    reset(mockCollection)
    setupIndexesManager
  }

  class TestMandateRepository extends MandateMongoRepository {
    override lazy val collection = mockCollection
  }

  private def setupFindMock = {
    val queryBuilder = mock[JSONQueryBuilder]
    when(mockCollection.find(Matchers.any())(Matchers.any())) thenReturn queryBuilder

    when(queryBuilder.one[GGRelationshipDto](Matchers.any(), Matchers.any()))thenReturn(Future.successful(None))
  }
}
