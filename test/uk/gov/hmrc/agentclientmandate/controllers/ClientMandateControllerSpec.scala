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
import uk.gov.hmrc.agentclientmandate.repositories.{ClientMandateNotFound, ClientMandateFetched}
import uk.gov.hmrc.agentclientmandate.services._
import play.api.test.{FakeApplication, FakeRequest}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.services.ClientMandateCreateService

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


    // get API tests ---- START

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

    // get API tests ---- START

  }

  val mandateId = "123"

  val clientMandateServiceMock = mock[ClientMandateCreateService]

  val mockFetchClientMandateService = mock[ClientMandateFetchService]

  val requestJson = Json.toJson(
    ClientMandateDto("credid",
      PartyDto("ARN123456", "Joe Bloggs", "Organisation"),
      ContactDetailsDto("test@test.com", "0123456789"),
      ServiceDto("ATED")
    )
  )

  val clientMandate =
    ClientMandate(
      id = "123",
      createdBy = "credid",
      party = Party("JARN123456", "Joe Bloggs", "Organisation", ContactDetails("test@test.com", "0123456789")),
      currentStatus = MandateStatus(Status.Pending, new DateTime(), "credid"),
      statusHistory = None,
      service = Service(None, "ATED")
    )

  object TestClientMandateController extends ClientMandateController {
    override val clientMandateService = clientMandateServiceMock
    override val fetchClientMandateService = mockFetchClientMandateService
  }

  override def beforeEach(): Unit = {
    reset(clientMandateServiceMock)
    reset(mockFetchClientMandateService)
  }

  implicit override lazy val app: FakeApplication = new FakeApplication(
    additionalConfiguration = Map("auditing.enabled" -> "false")
  )

}
