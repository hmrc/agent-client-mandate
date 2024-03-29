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

package uk.gov.hmrc.agentclientmandate.utils

import java.time.Instant
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.utils.Generators._
import uk.gov.hmrc.http.HttpResponse


class MandateUtilsSpec extends PlaySpec {

  "MandateUtils" should {

    "return true" when {
      "status = APPROVED is NOT FOUND in status History" in {
        val mandate1 = Mandate(mandateReferenceGen.sample.get,
          User("credid", nameGen.sample.get, None),
          agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails("", Some(""))),
          clientParty = Some(Party("safe-id", "client-name", PartyType.Organisation, ContactDetails(emailGen.sample.get))),
          currentStatus = MandateStatus(Status.Active, Instant.now(), "credid"),
          statusHistory = Seq(MandateStatus(Status.PendingActivation, Instant.now(), "credid")),
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
            id = mandateReferenceGen.sample.get,
            createdBy = User("credid", "name", None),
            agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample)),
            clientParty = Some(Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, telephoneNumberGen.sample))),
            currentStatus = MandateStatus(Status.PendingActivation, Instant.now(), "credid"),
            statusHistory = Seq(MandateStatus(Status.Approved, Instant.now(), "credid"), MandateStatus(Status.New, Instant.now(), "credid2")),
            subscription = Subscription(Some(subscriptionReferenceGen.sample.get), Service("ebc", "ABC")),
            clientDisplayName = "client display name"
          )
        MandateUtils.whetherSelfAuthorised(mandate1) must be (false)
      }
    }

    "return ErrorNumber from httpresponse" when {
      "response from gg" in {
        val httpResponse = HttpResponse(INTERNAL_SERVER_ERROR, """<soap:Body xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/03/addressing" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Fault><faultcode>soap:Client</faultcode><faultstring>Business Rule Error</faultstring><faultactor>http://www.gateway.gov.uk/soap/2007/02/admin</faultactor><detail><GatewayDetails xmlns="urn:GSO-System-Services:external:SoapException"><ErrorNumber>9005</ErrorNumber><Message>The enrolment is not allocated to the Agent Group</Message><RequestID>25DAE8CDF10B4B7CB469AF662643C917</RequestID></GatewayDetails></detail></soap:Fault></soap:Body>""")
        val result = MandateUtils.parseErrorResp(httpResponse)
        result must be ("9005")
      }
    }

    "validate groupid" when {
      "remove testGroup string " in {
        val testGroupId = "testGroupId-42424200-0000-0000-0000-000000000000"
        MandateUtils.validateGroupId(testGroupId) must be ("42424200-0000-0000-0000-000000000000")
      }

      "trim a long string" in {
        val paddedGroupId = "42424200-0000-0000-0000-000000000000   "
        MandateUtils.validateGroupId(paddedGroupId) must be ("42424200-0000-0000-0000-000000000000")
      }

      "Throws an exception with an invalid string" in {
        val invalidGroupId = "42424200-0000-0000-0000"
        intercept[RuntimeException] {MandateUtils.validateGroupId(invalidGroupId)}
      }

    }
  }
}
