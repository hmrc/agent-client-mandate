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

import org.mockito.Mockito._
import org.mockito.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.agentclientmandate.ClientMandateFetched
import uk.gov.hmrc.agentclientmandate.services._

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

    }

    "return CREATED" when {

      "valid json is sent" in {

        when(clientMandateServiceMock.createMandate(Matchers.any())) thenReturn Future.successful("123")
        println(s"####  ${requestJson}")

        val request = TestClientMandateController.create().apply(FakeRequest().withBody(requestJson))
        status(request) must be(CREATED)
        (contentAsJson(request) \ "id").as[String] must be("123")

      }

    }
    // create API tests ---- END


    // get API tests ---- START

    "not return a 404" when {

      "GET /agent-client-mandate/mandate/:mandateId exists" in {
        val request = route(FakeRequest(GET, "/agent-client-mandate/mandate/12345678")).get
        status(request) mustNot be(NOT_FOUND)
      }

    }

    "return a success response" when {

      "mandate id is found" in {

        when(mockFetchClientMandateService.fetchClientMandate(Matchers.eq(manadateId))) thenReturn Future.successful(clientMandateFetched)

        val reponse = TestClientMandateController.fetch(manadateId).apply(FakeRequest())
        status(reponse) must be(OK)

      }

    }

    // get API tests ---- START

  }

  override def beforeEach(): Unit = {
    reset(clientMandateServiceMock)
    reset(mockFetchClientMandateService)
  }

  val manadateId = "12345678"
  val clientMandate = ClientMandate("123", "credid", Party("JARN123456", "Joe Bloggs", "Organisation"), ContactDetails("test@test.com", "0123456789"))

  val clientMandateFetched = ClientMandateFetched(clientMandate)

  val requestJson = Json.toJson(ClientMandateDto(PartyDto("ARN123456", "Joe Bloggs", "Organisation"), ContactDetailsDto("test@test.com", "0123456789")))

  val clientMandateServiceMock = mock[ClientMandateService]
  val mockFetchClientMandateService = mock[FetchClientMandateService]

  object TestClientMandateController extends ClientMandateController {
    override val clientMandateService = clientMandateServiceMock
    override val fetchClientMandateService = mockFetchClientMandateService
  }

}
