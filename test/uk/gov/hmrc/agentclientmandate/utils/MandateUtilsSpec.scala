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

package uk.gov.hmrc.agentclientmandate.utils

import org.joda.time.DateTime
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.http.HttpResponse

class MandateUtilsSpec extends PlaySpec {

  "MandateUtils" should {

    "return true" when {
      "status = APPROVED is NOT FOUND in status History" in {
        val mandate1 = Mandate("AS12345678",
          User("credid", "Joe Bloggs", None),
          agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("", Some(""))),
          clientParty = Some(Party("safe-id", "client-name", PartyType.Organisation, ContactDetails("client@mail.com"))),
          currentStatus = MandateStatus(Status.Active, new DateTime(), "credid"),
          statusHistory = Seq(MandateStatus(Status.PendingActivation, new DateTime(), "credid")),
          subscription = Subscription(Some("ated-ref-no"), Service("ated", "ATED")),
          clientDisplayName = "client display name"
        )
        MandateUtils.whetherSelfAuthorised(mandate1) must be (true)
      }
    }

    "return false" when {
      "status = APPROVED is FOUND in status History" in {
        val mandate1 =
          Mandate(
            id = "123",
            createdBy = User("credid", "name", None),
            agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("test@test.com", Some("0123456789"))),
            clientParty = Some(Party("ABCD1234", "Client Name", PartyType.Organisation, ContactDetails("somewhere@someplace.com", Some("98765433210")))),
            currentStatus = MandateStatus(Status.PendingActivation, new DateTime(), "credid"),
            statusHistory = Seq(MandateStatus(Status.Approved, new DateTime(), "credid"), MandateStatus(Status.New, new DateTime(), "credid2")),
            subscription = Subscription(Some("1111111111"), Service("ebc", "ABC")),
            clientDisplayName = "client display name"
          )
        MandateUtils.whetherSelfAuthorised(mandate1) must be (false)
      }
    }

    "return ErrorNumber from httpresponse" when {
      "response from gg" in {
        val httpResponse = HttpResponse(INTERNAL_SERVER_ERROR, responseString = Some("""<soap:Body xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/03/addressing" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Fault><faultcode>soap:Client</faultcode><faultstring>Business Rule Error</faultstring><faultactor>http://www.gateway.gov.uk/soap/2007/02/admin</faultactor><detail><GatewayDetails xmlns="urn:GSO-System-Services:external:SoapException"><ErrorNumber>9005</ErrorNumber><Message>The enrolment is not allocated to the Agent Group</Message><RequestID>25DAE8CDF10B4B7CB469AF662643C917</RequestID></GatewayDetails></detail></soap:Fault></soap:Body>"""))
        val result = MandateUtils.parseErrorResp(httpResponse)
        result must be ("9005")
      }
    }
  }
}
