/*
 * Copyright 2019 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.services.AgentDetailsService
import uk.gov.hmrc.domain.{AgentCode, AtedUtr, Generator}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.Future

class AgentDelegationForAtedControllerSpec extends PlaySpec with GuiceOneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

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

  }

  val agentCode: AgentCode = AgentCode("XYZ")
  val atedUtr: AtedUtr = new Generator().nextAtedUtr
  implicit val hc = HeaderCarrier()

  val mockRelationshipService = mock[AgentDetailsService]

  val cc: ControllerComponents = app.injector.instanceOf[ControllerComponents]

  object TestAgentDelegationForAtedController extends BackendController(cc) with AgentDelegationForAtedController {
    override val agentDetailsService: AgentDetailsService = mockRelationshipService
  }

}
