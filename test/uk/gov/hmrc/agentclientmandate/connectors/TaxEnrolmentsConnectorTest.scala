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

package uk.gov.hmrc.agentclientmandate.connectors

import java.util.UUID

import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.metrics.Metrics
import uk.gov.hmrc.agentclientmandate.utils.TestAudit
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.play.config.{AppName, RunMode}
import uk.gov.hmrc.play.microservice.config.LoadAuditingConfig

import scala.concurrent.Future

class TaxEnrolmentsConnectorSpec extends PlaySpec with OneServerPerSuite with MockitoSugar with BeforeAndAfterEach {

  object TestAuditConnector extends AuditConnector with AppName with RunMode {
    override lazy val auditingConfig = LoadAuditingConfig(s"$env.auditing")
  }

  trait MockedVerbs extends CorePut
  val mockWSHttp: CorePut = mock[MockedVerbs]

  object TestTaxEnrolmentsConnector extends TaxEnrolmentsConnector {
    override val serviceUrl = ""
    override val ggaBaseUrl = ""
    override val http: CorePut = mockWSHttp
    override val audit: Audit = new TestAudit
    override val appName: String = "Test"
    override def metrics = Metrics
  }

  override def beforeEach = {
    reset(mockWSHttp)
  }

  "TaxEnrolmentsConnector" must {

    "use correct metrics" in {
      TaxEnrolmentsConnector.metrics must be(Metrics)
    }

    val successfulJson = Json.parse( """{"rowModified":"1"}""")
    val failureJson = Json.parse( """{"error":"Constraint error"}""")

    "for successful set of known facts, return response" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(mockWSHttp.PUT[JsValue, HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.successful(HttpResponse(OK, responseJson = Some(successfulJson))))

      val knownFacts = Json.toJson("")
      val result = TestTaxEnrolmentsConnector.addKnownFacts("ATED", knownFacts, "JARN123456")
      await(result).status must be(OK)
    }

    "for unsuccessful call of known facts, return response" in {
      implicit val hc = new HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID}")))
      when(mockWSHttp.PUT[JsValue, HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())).
        thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, responseJson = Some(failureJson))))

      val knownFacts = Json.toJson("")
      val result = TestTaxEnrolmentsConnector.addKnownFacts("ATED", knownFacts, "JARN123456")
      await(result).status must be(INTERNAL_SERVER_ERROR)
    }

  }

}