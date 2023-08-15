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

package uk.gov.hmrc.agentclientmandate.models

import org.joda.time.DateTime
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json

class MandateStatusSpec extends AnyWordSpecLike {
  "MandateStatus reads" must {
    "read a json of MandateStatus" when {
      "there is a valid millisecond timestamp" in {
        val status = Status.New
        val timestamp = 1560854220091L
        val updatedBy = "Person"

        val json =
          s"""{
            |"status" : "$status",
            |"timestamp" : $timestamp,
            |"updatedBy": "$updatedBy"
            |}
          """.stripMargin

        Json.parse(json).as[MandateStatus] shouldBe MandateStatus(status, new DateTime(timestamp), updatedBy)
      }
    }

    "fail to read a json of MandateStatus" when {
      "there no valid millisecond timestamp" in {
        val status = Status.New
        val updatedBy = "Person"

        val json =
          s"""{
            |"status" : "$status",
            |"updatedBy": "$updatedBy"
            |}
          """.stripMargin

        intercept[RuntimeException](Json.parse(json).as[MandateStatus])
      }
    }
  }

  "MandateStatus writes" must {
    "write a json of MandateStatus" when {
      "there is a valid millisecond timestamp" in {
        val status = Status.New
        val timestamp = 1560854220091L
        val updatedBy = "Person"

        val caseClass = MandateStatus(status, new DateTime(timestamp), updatedBy)

        Json.toJson(caseClass) shouldBe Json.parse(
          s"""{
           |"status" : "$status",
           |"timestamp" : $timestamp,
           |"updatedBy": "$updatedBy"
           |}
          """.stripMargin
        )
      }
    }

    "fail to write a json of MandateStatus" when {
      "there is no valid millisecond timestamp" in {
        val status = Status.New
        val updatedBy = "Person"

        val caseClass = MandateStatus(status, null, updatedBy)

        intercept[RuntimeException](Json.toJson(caseClass))
      }
    }
  }
}
