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

package uk.gov.hmrc.agentclientmandate.controllers.auth

import org.mockito.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import uk.gov.hmrc.agentclientmandate.services.RelationshipService
import uk.gov.hmrc.domain.{AgentCode, AtedUtr, Generator}
import uk.gov.hmrc.play.http.HeaderCarrier
import org.mockito.Mockito._
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.concurrent.Future

class AgentDelegationForAtedControllerSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  "AgentDelegationForAtedController" must {

    "return OK" when {
      "agent is authorised to act on behalf of ated customers" in {
        when(mockRelationshipService.isAuthorisedForAted(Matchers.eq(atedUtr))(Matchers.any())).thenReturn(Future.successful(true))
        val result = TestAgentDelegationForAtedController.isAuthorisedForAted(agentCode, atedUtr).apply(FakeRequest())
        status(result) must be(OK)
      }
    }

    "return UnAuthorised" when {
      "agent is not authorised to act on behalf of ated customers" in {
        when(mockRelationshipService.isAuthorisedForAted(Matchers.eq(atedUtr))(Matchers.any())).thenReturn(Future.successful(false))
        val result = TestAgentDelegationForAtedController.isAuthorisedForAted(agentCode, atedUtr).apply(FakeRequest())
        status(result) must be(UNAUTHORIZED)
      }
    }

  }

  val agentCode: AgentCode = AgentCode("XYZ")
  val atedUtr: AtedUtr = new Generator().nextAtedUtr
  implicit val hc = HeaderCarrier()

  val mockRelationshipService = mock[RelationshipService]

  object TestAgentDelegationForAtedController extends AgentDelegationForAtedController {
    override val relationshipService: RelationshipService = mockRelationshipService
  }

}
