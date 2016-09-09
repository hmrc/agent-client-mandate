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

package uk.gov.hmrc.agentclientmandate.controllers

import org.joda.time.DateTime
import org.mockito.Mockito._
import org.mockito.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.repositories.{ClientMandateUpdateError, ClientMandateUpdated, ClientMandateNotFound, ClientMandateFetched}
import uk.gov.hmrc.agentclientmandate.services._
import play.api.test.{FakeApplication, FakeRequest}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.services.ClientMandateCreateService
import play.api.http.HttpVerbs.PATCH

import scala.concurrent.Future


class ClientMandateControllerSpec extends PlaySpec with OneAppPerSuite with MockitoSugar with BeforeAndAfterEach {

  "ClientMandateController" should {

    // create API tests ---- START
    "not return a 404" when {

      "POST /agent-client-mandate/mandate exists" in {
        val request = route(FakeRequest(POST, "/agent-client-mandate/mandate")).get
        status(request) mustNot be(NOT_FOUND)
      }

    }

    "return a 400" when {

      "party is missing from the request json" in {
        val json = Json.obj()
        val request = TestClientMandateController.create().apply(FakeRequest().withBody(json))
        status(request) must be(BAD_REQUEST)
      }

      "contact details is missing from the request json" in {
        val json = Json.obj("party" -> Json.obj("id" -> "123", "name" -> "Joe Bloggs", "type" -> "Organisation"))
        val request = TestClientMandateController.create().apply(FakeRequest().withBody(json))
        status(request) must be(BAD_REQUEST)
      }

      "createdBy is missing from the request json" in {
        val json = Json.obj("party" -> Json.obj("id" -> "123", "name" -> "Joe Bloggs", "type" -> "Organisation"))
        val request = TestClientMandateController.create().apply(FakeRequest().withBody(json))
        status(request) must be(BAD_REQUEST)
      }

      "service object is missing" in {
        val json = Json.obj("party" -> Json.obj("id" -> "123", "name" -> "Joe Bloggs", "type" -> "Organisation"))
        val request = TestClientMandateController.create().apply(FakeRequest().withBody(json))
        status(request) must be(BAD_REQUEST)
      }

    }

    "return CREATED" when {

      "valid json is sent" in {

        when(clientMandateServiceMock.createMandate(Matchers.any())(Matchers.any())) thenReturn Future.successful("123")

        val request = TestClientMandateController.create().apply(FakeRequest().withBody(requestJson))
        status(request) must be(CREATED)
        (contentAsJson(request) \ "mandateId").as[String] must be("123")

      }

    }
    // create API tests ---- END

    // update API ---- START

    "not return a 404" when {

      "PATCH /agent-client-mandate/mandate exists" in {

        val request = route(FakeRequest(PATCH, "/agent-client-mandate/mandate")).get
        status(request) mustNot be(NOT_FOUND)

      }

    }


    "return BAD_REQUEST" when {

      "invalid json is sent" in {

        val json = Json.toJson(PartyDto("XVAT00000123456", "Joe Bloggs", "Organisation"))
        val request = TestClientMandateController.update().apply(FakeRequest().withBody(json))
        status(request) must be(BAD_REQUEST)

      }

    }

    "return ok" when {

      "valid updated json is sent and an existing mandate is found" in {

        when(mockClientMandateUpdateService.updateMandate(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn {
          Future.successful(ClientMandateUpdated(updatedClientMandate(DateTime.now)))
        }

        val json = Json.toJson(ClientMandateUpdatedDto("AS12345678", Some(PartyDto("XVAT00000123456", "Joe Bloggs", "Organisation")), None, None))

        val response = TestClientMandateController.update().apply(FakeRequest().withBody(json))
        status(response) must be(OK)
      }

    }

    "return NOT_FOUND" when {

      "valid json is passed but no existing mandate is found" in {

        when(mockClientMandateUpdateService.updateMandate(Matchers.any(), Matchers.any())(Matchers.any())) thenReturn {
          Future.successful(ClientMandateUpdateError)
        }

        val json = Json.toJson(ClientMandateUpdatedDto("AS12345678", Some(PartyDto("XVAT00000123456", "Joe Bloggs", "Organisation")), None, None))

        val response = TestClientMandateController.update().apply(FakeRequest().withBody(json))
        status(response) must be(NOT_FOUND)

      }

    }


    // update API ---- END


    // get by Id API tests ---- START

    "return a success response" when {

      "mandate id is found" in {

        when(mockFetchClientMandateService.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(ClientMandateFetched(clientMandate))

        val response = TestClientMandateController.fetch(mandateId).apply(FakeRequest())
        status(response) must be(OK)

      }

    }

    "return a not found" when {

      "mandate is not found" in {

        when(mockFetchClientMandateService.fetchClientMandate(Matchers.eq(mandateId))) thenReturn Future.successful(ClientMandateNotFound)

        val response = TestClientMandateController.fetch(mandateId).apply(FakeRequest())
        status(response) must be(NOT_FOUND)
      }

    }

    // get by Id API tests ---- START

    //get by service API tests ---- START

    "not return a 404" when {

      "POST /agent-client-mandate/mandate/service exists" in {
        val request = route(FakeRequest(GET, s"/agent-client-mandate/mandate/service/$arn/$serviceName")).get
        status(request) mustNot be(NOT_FOUND)
      }

      "return a success response" when {

        "service id is vaild" in {

          when(mockFetchClientMandateService.getAllMandates(Matchers.eq(arn), Matchers.eq(serviceName))) thenReturn Future.successful(List(clientMandate))

          val response = TestClientMandateController.fetchAll(arn, serviceName).apply(FakeRequest())

          status(response) must be(OK)

        }

        "service id is invaild" in {

          when(mockFetchClientMandateService.getAllMandates(Matchers.eq(arn), Matchers.any())) thenReturn Future.successful(Nil)

          val response = TestClientMandateController.fetchAll(arn, invalidServiceName).apply(FakeRequest())

          status(response) must be(NOT_FOUND)


        }
      }

    }

    //get by service API tests ---- END



  }

  val mandateId = "123"

  val serviceName = "345"
  val invalidServiceName = "349"

  val arn = "ARN123456"

  val clientMandateServiceMock = mock[ClientMandateCreateService]

  val mockFetchClientMandateService = mock[ClientMandateFetchService]

  val mockClientMandateUpdateService = mock[ClientMandateUpdateService]

  val requestJson = Json.toJson(
    ClientMandateDto(
      PartyDto("ARN123456", "Joe Bloggs", "Organisation"),
      ContactDetailsDto("test@test.com", "0123456789"),
      ServiceDto(None, "ATED")
    )
  )

  val clientMandate =
    ClientMandate(
      id = "123",
      createdBy = "credid",
      agentParty = Party("JARN123456", "Joe Bloggs", "Organisation", ContactDetails("test@test.com", "0123456789")),
      clientParty = None,
      currentStatus = MandateStatus(Status.Pending, new DateTime(), "credid"),
      statusHistory = None,
      subscription = Subscription(None, Service("ated", "ATED"))
      //service = Service(None, "ATED")
    )

  def updatedClientMandate(time: DateTime): ClientMandate =
    ClientMandate("AS12345678", createdBy = "credid",
      agentParty = Party("JARN123456", "Joe Bloggs", "Organisation", contactDetails = ContactDetails("test@test.com", "0123456789")),
      clientParty = Some(Party("XBAT00000123456", "Joe Ated", "Organisation", contactDetails = ContactDetails("", ""))),
      currentStatus = MandateStatus(Status.Active, time, "credid"),
      statusHistory = Some(Seq(MandateStatus(Status.Pending, time, "credid"))),
      subscription = Subscription(Some("XBAT00000123456"), Service("ated", "ATED"))
      //service = Service(Some("XBAT00000123456"), "ATED")
    )

  object TestClientMandateController extends ClientMandateController {
    override val clientMandateCreateService = clientMandateServiceMock
    override val fetchClientMandateService = mockFetchClientMandateService
    override val clientMandateUpdateService = mockClientMandateUpdateService
  }

  override def beforeEach(): Unit = {
    reset(clientMandateServiceMock)
    reset(mockFetchClientMandateService)
  }

  implicit override lazy val app: FakeApplication = new FakeApplication(
    additionalConfiguration = Map("auditing.enabled" -> "false")
  )

}
