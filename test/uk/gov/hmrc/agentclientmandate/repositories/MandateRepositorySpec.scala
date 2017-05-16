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

import org.joda.time.DateTime
import org.mockito.Matchers
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.Logger
import play.api.libs.iteratee.Enumerator
import play.api.test.Helpers._
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.api.indexes.CollectionIndexesManager
import reactivemongo.api.{Cursor, DB}
import reactivemongo.bson.BSONDocument
import reactivemongo.json.collection.{JSONCollection, JSONQueryBuilder}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.duration._
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
        setupFindMockTemp
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

        await(testMandateRepository.getAllMandatesByServiceName("JARN123456", "ated", None, None, None)) must be(List(mandate))
      }

    }

    "return an empty list" when {

      "the arn and service does not match" in {
        await(testMandateRepository.insertMandate(mandate))

        await(testMandateRepository.findAll()).head must be(mandate)
        await(testMandateRepository.count) must be(1)

        await(testMandateRepository.getAllMandatesByServiceName("JARN123456", "ATED", None, None, None)) must be(List(mandate))
      }
    }

    "apply filtering to the list" when {
      "user selects to view only those they have created" in {
        await(testMandateRepository.insertMandate(activeMandate))
        val user = User("credid2", "Joe Bloggs", None)
        await(testMandateRepository.insertMandate(activeMandate.copy(id = "AS12345679", createdBy = user)))

        await(testMandateRepository.getAllMandatesByServiceName("JARN123456", "ATED", Some("credid"), Some("othercredid"), None)) must be(List(activeMandate))
      }

      "user adds a filter on the display name" in {
        await(testMandateRepository.insertMandate(activeMandate))

        await(testMandateRepository.getAllMandatesByServiceName("JARN123456", "ATED", None, None, Some("bob"))) must be(List())
      }
    }

    "fetch a mandate from the repo by client" when {

      "find the mandate when the correct client id and service is passed in" in {
        await(testMandateRepository.insertMandate(updatedMandate))

        await(testMandateRepository.count) must be(1)
        await(testMandateRepository.fetchMandateByClient(updatedMandate.clientParty.get.id, updatedMandate.subscription.service.id)) must be(MandateFetched(updatedMandate))
      }

      "cannot find the mandate with incorrect client id passed in" in {
        await(testMandateRepository.insertMandate(updatedMandate2))

        await(testMandateRepository.count) must be(1)
        await(testMandateRepository.fetchMandateByClient("XYZ", updatedMandate2.subscription.service.id)) must be(MandateNotFound)
      }

      "cannot find the mandate with correct client but not active" in {
        await(testMandateRepository.insertMandate(updatedMandate3))

        await(testMandateRepository.count) must be(1)
        await(testMandateRepository.fetchMandateByClient(updatedMandate3.clientParty.get.id, updatedMandate3.subscription.service.id)) must be(MandateNotFound)
      }

      "find the mandate with same client but different service" in {
        await(testMandateRepository.insertMandate(updatedMandate4))
        await(testMandateRepository.insertMandate(updatedMandate2))

        await(testMandateRepository.count) must be(2)
        await(testMandateRepository.fetchMandateByClient(updatedMandate4.clientParty.get.id, updatedMandate4.subscription.service.id)) must be(MandateFetched(updatedMandate4))
        await(testMandateRepository.fetchMandateByClient(updatedMandate2.clientParty.get.id, updatedMandate2.subscription.service.id)) must be(MandateFetched(updatedMandate2))
      }

      "find the newest mandate" in {
        await(testMandateRepository.insertMandate(updatedMandate5))
        await(testMandateRepository.insertMandate(updatedMandate4))

        await(testMandateRepository.count) must be(2)
        await(testMandateRepository.fetchMandateByClient(updatedMandate4.clientParty.get.id, updatedMandate4.subscription.service.id)) must be(MandateFetched(updatedMandate4))
      }
    }

    "doesAgentHaveEmail" must {
      "return true if all mandates have an agent email address" in {
        await(testMandateRepository.insertMandate(updatedMandate3))
        await(testMandateRepository.insertMandate(updatedMandate2))

        await(testMandateRepository.count) must be(2)
        await(testMandateRepository.findMandatesMissingAgentEmail("JARN123456", "ATED")) must be(Nil)
      }

      "return false if all mandates have an agent email address" in {
        await(testMandateRepository.insertMandate(updatedMandate2))
        await(testMandateRepository.insertMandate(updatedMandate4))

        await(testMandateRepository.count) must be(2)
        await(testMandateRepository.findMandatesMissingAgentEmail("JARN123456", "ATED")) must be(Seq(updatedMandate4.id))
      }

      "fail when trying to find agents email in mandates" in {
        setupFindMockTemp
        when(mockCollection.indexesManager.create(Matchers.any())).thenReturn(Future.successful(UpdateWriteResult(true,0,0,Nil,Nil,None,None,None)))
        when(mockCollection.find(Matchers.eq(BSONDocument(
          "agentParty.contactDetails.email" -> "",
          "agentParty.id" -> "JARN123456",
          "subscription.service.id" -> "ATED")))(Matchers.any())).thenThrow(new RuntimeException)
        val testRepository = new TestMandateRepository
        val result = await(testRepository.findMandatesMissingAgentEmail("JARN123456", "ATED"))

        result mustBe Nil
      }
    }

    "updateAgentEmail" must {
      "update the agents email" in {
        await(testMandateRepository.insertMandate(updatedMandate4))
        await(testMandateRepository.insertMandate(updatedMandate2))

        await(testMandateRepository.updateAgentEmail(List(updatedMandate2.id, updatedMandate4.id), "test@mail.com")) must be(MandateUpdatedEmail)
        await(testMandateRepository.fetchMandate(updatedMandate4.id).map {
          case MandateFetched(x) => x.agentParty.contactDetails.email
        }) must be("test@mail.com")
        await(testMandateRepository.fetchMandate(updatedMandate2.id).map {
          case MandateFetched(x) => x.agentParty.contactDetails.email
        }) must be("test@mail.com")
      }
    }

    "updateClientEmail" must {
      "update the clients email" in {
        await(testMandateRepository.insertMandate(updatedMandate2))

        await(testMandateRepository.updateClientEmail(updatedMandate2.id, "test@mail.com")) must be(MandateUpdatedEmail)
        await(testMandateRepository.fetchMandate(updatedMandate2.id).map {
          case MandateFetched(x) => x.clientParty.get.contactDetails.email
        }) must be("test@mail.com")
      }
    }

    "updateAgentCredId" must {
      "update the agents cred id" in {
        await(testMandateRepository.insertMandate(activeMandate))
        await(testMandateRepository.insertMandate(updatedMandate))

        await(testMandateRepository.updateAgentCredId("credid", "newCredId")) must be(MandateUpdatedCredId)

        await(testMandateRepository.fetchMandate(activeMandate.id).map {
          case MandateFetched(x) => x.createdBy.credId
        }) must be("newCredId")
        await(testMandateRepository.fetchMandate(updatedMandate.id).map {
          case MandateFetched(x) => x.createdBy.credId
        }) must be("newCredId")
      }
    }

  }

  def testMandateRepository(implicit mongo: () => DB) = new MandateMongoRepository

  def mandate: Mandate =
    Mandate("AS12345678", createdBy = User("credid", "Joe Bloggs", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, contactDetails = ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = None,
      currentStatus = MandateStatus(Status.New, new DateTime(1472631804869L), "credidupdate"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ATED", "ated")),
      clientDisplayName = "client display name"
    )

  def activeMandate: Mandate =
    Mandate("AS22345678", createdBy = User("credid", "Joe Bloggs", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, contactDetails = ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = None,
      currentStatus = MandateStatus(Status.Active, new DateTime(1472631804869L), "credidupdate"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ATED", "ated")),
      clientDisplayName = "client display name"
    )

  def updatedMandate: Mandate =
    Mandate("AS12345678", createdBy = User("credid", "Joe Bloggs", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, contactDetails = ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = Some(Party("XBAT00000123456", "Joe Ated", PartyType.Organisation, contactDetails = ContactDetails("", None))),
      currentStatus = MandateStatus(Status.Active, new DateTime(1472631805678L), "credidclientupdate"),
      statusHistory = Seq(MandateStatus(Status.New, new DateTime(1472631804869L), "credidupdate")),
      subscription = Subscription(Some("XBAT00000123456"), Service("ATED", "ated")),
      clientDisplayName = "client display name"
    )

  def mandate1: Mandate =
    Mandate("AS12345678", createdBy = User("credid", "Joe Bloggs", None),
      agentParty = Party("JARN123457", "John Snow", PartyType.Organisation, contactDetails = ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = None,
      currentStatus = MandateStatus(Status.New, new DateTime(1472631804869L), "credidupdate"),
      statusHistory = Nil,
      subscription = Subscription(None, Service("ATED", "ated")),
      clientDisplayName = "client display name"
    )

  def updatedMandate2: Mandate =
    Mandate("AS12345678", createdBy = User("credid", "Joe Bloggs", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, contactDetails = ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = Some(Party("XBAT00000123457", "Susie", PartyType.Organisation, contactDetails = ContactDetails("", None))),
      currentStatus = MandateStatus(Status.Active, new DateTime(1472631805678L), "credidclientupdate"),
      statusHistory = Seq(MandateStatus(Status.New, new DateTime(1472631804869L), "credidupdate")),
      subscription = Subscription(Some("XBAT00000123456"), Service("AWRS", "awrs")),
      clientDisplayName = "client display name"
    )

  def updatedMandate3: Mandate =
    Mandate("AS12345679", createdBy = User("credid", "Joe Bloggs", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, contactDetails = ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = Some(Party("XBAT00000123457", "Susie", PartyType.Organisation, contactDetails = ContactDetails("", None))),
      currentStatus = MandateStatus(Status.New, new DateTime(1472631805678L), "credidclientupdate"),
      statusHistory = Nil,
      subscription = Subscription(Some("XBAT00000123456"), Service("ATED", "ated")),
      clientDisplayName = "client display name"
    )

  def updatedMandate4: Mandate =
    Mandate("AS12345679", createdBy = User("credid", "Joe Bloggs", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, contactDetails = ContactDetails("", Some("0123456789"))),
      clientParty = Some(Party("XBAT00000123457", "Susie", PartyType.Organisation, contactDetails = ContactDetails("", None))),
      currentStatus = MandateStatus(Status.Active, new DateTime(1472631805678L), "credidclientupdate"),
      statusHistory = Seq(MandateStatus(Status.New, new DateTime(1472631804869L), "credidupdate")),
      subscription = Subscription(Some("XBAT00000123456"), Service("ATED", "ated")),
      clientDisplayName = "client display name"
    )

  def updatedMandate5: Mandate =
    Mandate("AS12345670", createdBy = User("credid", "Joe Bloggs", None),
      agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, contactDetails = ContactDetails("test@test.com", Some("0123456789"))),
      clientParty = Some(Party("XBAT00000123457", "Susie", PartyType.Organisation, contactDetails = ContactDetails("", None))),
      currentStatus = MandateStatus(Status.Cancelled, new DateTime(1472631805678L), "credidclientupdate"),
      statusHistory = Seq(MandateStatus(Status.New, new DateTime(1472631804869L), "credidupdate")),
      subscription = Subscription(Some("XBAT00000123456"), Service("ATED", "ated")),
      clientDisplayName = "client display name"
    )
  def updatedMandate6: Mandate =
    Mandate("AS12325679", createdBy = User("credid", "Joe Bloggs", None),
      agentParty = Party("JARN123457", "Joe Bloggs", PartyType.Organisation, contactDetails = ContactDetails("", Some("0123456789"))),
      clientParty = Some(Party("XBAT00000123457", "Susie", PartyType.Organisation, contactDetails = ContactDetails("", None))),
      currentStatus = MandateStatus(Status.Active, new DateTime(1472631805678L), "credidclientupdate"),
      statusHistory = Seq(MandateStatus(Status.New, new DateTime(1472631804869L), "credidupdate")),
      subscription = Subscription(Some("XBAT00000123456"), Service("ATED", "ated")),
      clientDisplayName = "client display name"
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
    when(mockCollection.find(Matchers.eq(BSONDocument(
      "agentPartyId" -> "bbb"
    )))(Matchers.any())) thenReturn queryBuilder

    when(queryBuilder.one[GGRelationshipDto](Matchers.any(), Matchers.any()))thenReturn(Future.successful(None))
  }

  private def setupFindMockTemp = {
    val queryBuilder = mock[JSONQueryBuilder]
    when(mockCollection.find(Matchers.any())(Matchers.any())) thenReturn queryBuilder
    val mockCursor = mock[Cursor[BSONDocument]]

    when(queryBuilder.cursor[BSONDocument](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any())) thenAnswer new Answer[Cursor[BSONDocument]] {
      def answer(i: InvocationOnMock) = mockCursor
    }

    when(queryBuilder.one(Matchers.any(), Matchers.any())) thenReturn Future.successful(None)

    when(
      mockCursor.collect[Traversable](Matchers.anyInt(), Matchers.anyBoolean())(Matchers.any[CanBuildFrom[Traversable[_], BSONDocument, Traversable[BSONDocument]]], Matchers.any[ExecutionContext])
    ) thenReturn Future.successful(List())

    when(
      mockCursor.enumerate()
    ) thenReturn Enumerator[BSONDocument]()
  }
}
