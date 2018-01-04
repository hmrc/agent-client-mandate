/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientmandate.connectors

import org.mockito.Mockito.reset
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import uk.gov.hmrc.agentclientmandate.metrics.Metrics
import uk.gov.hmrc.agentclientmandate.models.NewEnrolment
import uk.gov.hmrc.http.{CoreGet, CorePost}

class TaxEnrolmentsConnectorTest extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  trait MockedVerbs extends  CorePost
  val mockWSHttp: CorePost = mock[MockedVerbs]

  object TestTaxEnrolmentsConnector extends TaxEnrolmentConnector {

    override val http: CorePost = mockWSHttp

    override val enrolmentUrl: String = ""

    override def serviceUrl: String = ""

    override val metrics = Metrics
  }

  override def beforeEach = {
    reset(mockWSHttp)
  }

  "TaxEnrolmentsConnector" must {
    val request = NewEnrolment("0000000021313132")
    "works for an agent" in {

    }
  }

}