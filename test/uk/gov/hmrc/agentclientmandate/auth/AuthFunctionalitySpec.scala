/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientmandate.auth

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers.{convertToAnyShouldWrapper, the}
import org.scalatest.wordspec.AnyWordSpecLike
import org.mockito.MockitoSugar
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentclientmandate.utils.Generators.agentBusinessUtrGen
import uk.gov.hmrc.auth.core.retrieve.{AgentInformation, Credentials, ~}
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments, PlayAuthConnector}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class AuthFunctionalitySpec extends AnyWordSpecLike with MockitoSugar {

  lazy implicit val fakeRequest: FakeRequest[AnyContent] = FakeRequest()
  val mockAuthConnector: PlayAuthConnector = mock[PlayAuthConnector]
  implicit lazy val hc: HeaderCarrier = HeaderCarrier()


  protected abstract class Setup extends AuthFunctionality {
    override def authConnector: PlayAuthConnector = mockAuthConnector
  }

  val atedEnrolmentIdentifier = EnrolmentIdentifier(key = "ATEDRefNumber", value = "ated-ref-num")
  val agentEnrolmentIdentifier = EnrolmentIdentifier(key = "AgentRefNumber", value = "agent-ref-num")

  val optionalAtedEnrolment: Option[Enrolment] = Option(Enrolment(
    key = "HMRC-ATED-ORG",
    identifiers = Seq(EnrolmentIdentifier(key = "ATEDRefNumber", value = "ated-ref-num")),
    state = "active"
  ))

  val optionalAgentEnrolment: Option[Enrolment] = Option(Enrolment(
    key = "HMRC-AGENT-AGENT",
    identifiers = Seq(EnrolmentIdentifier(key = "AgentRefNumber", value = "agent-ref-num")),
    state = "active"
  ))

  val authRetrievalResponse: Enrolments ~ AgentInformation ~ Some[Credentials] = new ~ (new ~(
    Enrolments(Set(
      Enrolment(
        key = "HMRC-AGENT-AGENT",
        identifiers = Seq(EnrolmentIdentifier(key = "AgentRefNumber", value = agentBusinessUtrGen.sample.get)),
        state = "active"
      ),
      Enrolment(
        key = "HMRC-ATED-ORG",
        identifiers = Seq(EnrolmentIdentifier(key = "ATEDRefNumber", value = "ated-ref-num")),
        state = "active"
      )
    )),
    AgentInformation(None, None, None)),
    Some(Credentials(providerId = "cred-id-113244018119", providerType = "GovernmentGateway")))


  val testAuthRetrieval: AuthRetrieval = AuthRetrieval(
    enrolments = Set(
      Enrolment(
        key = "HMRC-AGENT-AGENT",
        identifiers = Seq(EnrolmentIdentifier(key = "AgentRefNumber", value = "agent-ref-num")),
        state = "active"
      ),
      Enrolment(
        key = "HMRC-ATED-ORG",
        identifiers = Seq(EnrolmentIdentifier(key = "ATEDRefNumber", value = "ated-ref-num")),
        state = "active"
      )
    ),
    agentInformation = AgentInformation(None, None, None),
    Option(Credentials(providerId = "cred-id-113244018119", providerType = "GovernmentGateway"))
  )

  "govGatewayId" must {
    "return an Government Gateway Id" when {
      "there is one present" in {
        testAuthRetrieval.govGatewayId shouldBe "cred-id-113244018119"
      }
    }

    "throw an exception" when {
      "there is no Government Gateway ID" in {
        val error = the[RuntimeException] thrownBy testAuthRetrieval.copy(credentials = None).govGatewayId
        error.getMessage shouldBe "[AuthRetrieval] No GGCredId found."
      }
    }
  }

  "atedUtr" must {
    "return an ated reference number" when {
      "there is one present" in {
        testAuthRetrieval.atedUtr shouldBe atedEnrolmentIdentifier
      }
    }

    "throw an exception" when {
      "there is no ATED enrolment present" in {
        val error = the[RuntimeException] thrownBy testAuthRetrieval.copy(enrolments = Set.empty).atedUtr
        error.getMessage shouldBe "[AuthRetrieval] No enrolment id found for ATEDRefNumber"
      }
    }
  }

  "agentBusinessUtr" must {
    "return an agent reference number" when {
      "there is one present" in {
        testAuthRetrieval.agentBusinessUtr shouldBe agentEnrolmentIdentifier
      }
    }

    "throw an exception" when {
      "there is no AGENT enrolment present" in {
        val error = the[RuntimeException] thrownBy testAuthRetrieval.copy(enrolments = Set.empty).agentBusinessUtr
        error.getMessage shouldBe "[AuthRetrieval] No enrolment id found for AgentRefNumber"
      }
    }
  }

  "agentBusinessEnrolment" must {
    "return an agent enrolment" when {
      "there is one present" in {
        testAuthRetrieval.agentBusinessEnrolment shouldBe optionalAgentEnrolment.get
      }
    }

    "throw an exception" when {
      "there is no AGENT enrolment present" in {
        val error = the[RuntimeException] thrownBy testAuthRetrieval.copy(enrolments = Set.empty).agentBusinessEnrolment
        error.getMessage shouldBe "[AuthRetrieval] No Agent enrolment found"
      }
    }
  }

  "getEnrolmentId" must {
    "return an enrolment identifier" when {
      "given an Ated enrolment and id" in {
        testAuthRetrieval.getEnrolmentId(optionalAtedEnrolment, "ATEDRefNumber") shouldBe atedEnrolmentIdentifier
      }

      "given an Agent enrolment and id" in {
        testAuthRetrieval.getEnrolmentId(optionalAgentEnrolment, "AgentRefNumber") shouldBe agentEnrolmentIdentifier
      }
    }

    "throw an exception" when {
      "there is no ATED enrolment present" in {
        val error = the[RuntimeException] thrownBy testAuthRetrieval.getEnrolmentId(Option(optionalAtedEnrolment.get.copy(identifiers = Seq())), "ATEDRefNumber")
        error.getMessage shouldBe "[AuthRetrieval] No enrolment id found for ATEDRefNumber"
      }

      "there is no AGENT enrolment present" in {
        val error = the[RuntimeException] thrownBy testAuthRetrieval.getEnrolmentId(Option(optionalAgentEnrolment.get.copy(identifiers = Seq())), "AgentRefNum")
        error.getMessage shouldBe "[AuthRetrieval] No enrolment id found for AgentRefNum"
      }
    }
  }

  "authRetrieval" must {
    "return an Ok(200)" when {
      "no exceptions are thrown" in new Setup {
        when(mockAuthConnector.authorise[Enrolments ~ AgentInformation ~ Some[Credentials]](any(), any())(any(), any()))
          .thenReturn(Future.successful(authRetrievalResponse))

        val response: AuthRetrieval => Future[Result] = _ => Future.successful(Ok)
        val result: Result = await(authRetrieval(response))
        result.header.status shouldBe 200
      }
    }
  }

  "return an Unauthorised(401)" when {
    "an exception is thrown from auth" in new Setup {
      when(mockAuthConnector.authorise[AnyContent](any(), any())(any(), any())).thenReturn(Future.failed(new RuntimeException("some error")))

      val response: AuthRetrieval => Future[Result] = _ => Future.successful(Ok)
      val result: Result = await(authRetrieval(response))
      result.header.status shouldBe 401
    }
  }
}

