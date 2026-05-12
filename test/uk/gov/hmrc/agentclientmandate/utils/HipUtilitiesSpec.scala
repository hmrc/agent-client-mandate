/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}

class HipUtilitiesSpec extends PlaySpec {

  "extractHipErrorCode" must {

    val unprocessableResponse =
      s"""{
         |  "errors": {
         |    "processingDate": "2025-12-09T12:34:46.672Z",
         |    "code": "303",
         |    "text": "Error message"
         |  }
         |}
         |""".stripMargin

    "extract error code and message" in {
      HipUtilities.extractHipErrorCode(unprocessableResponse) shouldEqual Some(("303", "Error message"))
    }
  }

  "stripSuccessWrapper" must {

    val successfulSubscribeJson = Json.parse(
      """
        |{
        |  "processingDate": "2001-12-17T09:30:47Z",
        |  "atedRefNumber": "ABCDEabcde12345",
        |  "formBundleNumber": "123456789012345"
        |}
      """.stripMargin
    )

    val wrappedSuccessfulSubscribeJson = Json.obj("success" -> successfulSubscribeJson)

    "remove success wrapper from successful response" in {
      HipUtilities.stripSuccessWrapper(wrappedSuccessfulSubscribeJson) shouldEqual successfulSubscribeJson
    }

    "return unchanged Json in success node not present" in {
      HipUtilities.stripSuccessWrapper(successfulSubscribeJson) shouldEqual successfulSubscribeJson
    }
  }

  "removeAcknowledgementReferenceField" must {

    val inputJsonCorrectSpelling: JsValue = Json.parse(
      """
        |{
        |"acknowledgementReference":"Tp0x8ql6GldqGyGh6u36149378018603",
        |"safeId":"XE0001234567890",
        |"emailConsent":false,
        |"address":[
        | {
        |   "name1":"Paul",
        |    "name2":"Carrielies",
        |    "addressDetails": {
        |      "addressLine1": "100 SuttonStreet",
        |      "addressLine2": "Wokingham",
        |      "postalCode": "AB12CD",
        |      "countryCode": "GB"
        |    },
        |    "contactDetails": {
        |      "telephone": "01332752856",
        |      "mobile": "07782565326",
        |      "fax": "01332754256",
        |      "email": "aa@aa.com"
        |    }
        | }],
        | "utr":"12345",
        | "businessType":"Corporate Body",
        | "isNonUKClientRegisteredByAgent": false,
        | "knownFactPostcode": "NE1 1EN"}
        |
    """.stripMargin
    )

    val inputJsonIncorrectSpelling: JsValue = Json.parse(
      """
        |{
        |"acknowledgmentReference":"Tp0x8ql6GldqGyGh6u36149378018603",
        |"safeId":"XE0001234567890",
        |"emailConsent":false,
        |"address":[
        | {
        |   "name1":"Paul",
        |    "name2":"Carrielies",
        |    "addressDetails": {
        |      "addressLine1": "100 SuttonStreet",
        |      "addressLine2": "Wokingham",
        |      "postalCode": "AB12CD",
        |      "countryCode": "GB"
        |    },
        |    "contactDetails": {
        |      "telephone": "01332752856",
        |      "mobile": "07782565326",
        |      "fax": "01332754256",
        |      "email": "aa@aa.com"
        |    }
        | }],
        | "utr":"12345",
        | "businessType":"Corporate Body",
        | "isNonUKClientRegisteredByAgent": false,
        | "knownFactPostcode": "NE1 1EN"}
        |
    """.stripMargin
    )

    val inputJsonWithAcknowlegementReferenceRemoved: JsValue = Json.parse(
      """{
        |"safeId":"XE0001234567890",
        |"emailConsent":false,
        |"address":[
        | {
        |   "name1":"Paul",
        |    "name2":"Carrielies",
        |    "addressDetails": {
        |      "addressLine1": "100 SuttonStreet",
        |      "addressLine2": "Wokingham",
        |      "postalCode": "AB12CD",
        |      "countryCode": "GB"
        |    },
        |    "contactDetails": {
        |      "telephone": "01332752856",
        |      "mobile": "07782565326",
        |      "fax": "01332754256",
        |      "email": "aa@aa.com"
        |    }
        | }],
        | "utr":"12345",
        | "businessType":"Corporate Body",
        | "isNonUKClientRegisteredByAgent": false,
        | "knownFactPostcode": "NE1 1EN"}
        |
    """.stripMargin
    )

    "remove acknowledgementReference from input Json" in {
      HipUtilities.removeAcknowledgementReferenceField(inputJsonCorrectSpelling) shouldEqual inputJsonWithAcknowlegementReferenceRemoved
    }

    "remove acknowledgmentReference from input Json" in {
      HipUtilities.removeAcknowledgementReferenceField(inputJsonIncorrectSpelling) shouldEqual inputJsonWithAcknowlegementReferenceRemoved
    }

    "return unchanged Json if acknowledgementReference field is absent" in {
      HipUtilities.removeAcknowledgementReferenceField(inputJsonWithAcknowlegementReferenceRemoved) shouldEqual inputJsonWithAcknowlegementReferenceRemoved
    }
  }
}
