/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientmandate.controllers.auth

import org.mockito.ArgumentMatchers._
import org.mockito.{ArgumentMatchers, MockitoSugar}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.agentclientmandate.auth.AuthRetrieval
import uk.gov.hmrc.agentclientmandate.services.AgentDetailsService
import uk.gov.hmrc.agentclientmandate.utils.Generators.agentBusinessUtrGen
import uk.gov.hmrc.auth.core.retrieve.AgentInformation
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.domain.{AgentCode, AtedUtr, Generator}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class AgentDelegationForAtedControllerSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach {

  "AgentDelegationForAtedController" must {

    "return OK" when {
      "agent is authorised to act on behalf of ated customers" in {
        when(mockRelationshipService.isAuthorisedForAted(ArgumentMatchers.eq(atedUtr))(any())).thenReturn(Future.successful(true))
        val result = TestAgentDelegationForAtedController.isAuthorisedForAted(agentCode, atedUtr).apply(FakeRequest())
        status(result) must be(OK)
      }
    }

    "return UnAuthorised" when {
      "agent is not authorised to act on behalf of ated customers" in {
        when(mockRelationshipService.isAuthorisedForAted(ArgumentMatchers.eq(atedUtr))(any())).thenReturn(Future.successful(false))
        val result = TestAgentDelegationForAtedController.isAuthorisedForAted(agentCode, atedUtr).apply(FakeRequest())
        status(result) must be(UNAUTHORIZED)
      }
    }

    "return not found" when {
      "Runtime Exception is thrown" in {
        when(mockRelationshipService.isAuthorisedForAted(ArgumentMatchers.eq(atedUtr))(any())).thenReturn(Future.failed(new RuntimeException("some error")))
        val result = TestAgentDelegationForAtedController.isAuthorisedForAted(agentCode, atedUtr).apply(FakeRequest())
        status(result) must be(NOT_FOUND)
      }
    }
  }

  val agentCode: AgentCode = AgentCode("XYZ")
  val atedUtr: AtedUtr = new Generator().nextAtedUtr
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockRelationshipService: AgentDetailsService = mock[AgentDetailsService]
  val mockAuthConnector: DefaultAuthConnector = mock[DefaultAuthConnector]

  val cc: ControllerComponents = Helpers.stubControllerComponents()
  val ar: AuthRetrieval = AuthRetrieval(enrolments = Set(
    Enrolment(
      key = "HMRC-AGENT-AGENT",
      identifiers = Seq(EnrolmentIdentifier(key = "AgentRefNumber", value = agentBusinessUtrGen.sample.get)),
      state = "active"
    )
  ),
    agentInformation = AgentInformation(None, None, None),
    None
  )

  object TestAgentDelegationForAtedController extends BackendController(cc) with AgentDelegationForAtedController {
    override val authConnector: DefaultAuthConnector = mockAuthConnector
    override val agentDetailsService: AgentDetailsService = mockRelationshipService
    override def authRetrieval(body: AuthRetrieval => Future[Result])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] = body(ar)
  }

}
