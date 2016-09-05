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

package uk.gov.hmrc.agentclientmandate.controllers


import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientmandate.services.NotificationEmailService



class NotificationEmailControllerSpec extends PlaySpec with OneAppPerSuite with MockitoSugar with BeforeAndAfterEach {

  "NotificationEmailController" should {

    "return a 400 status" when {

      "invalid json is passed" in {

        val fakeBody = Json.obj(
          "invalidJson" -> "Invalid Json"
        )

        val request = TestNotificationEmailController.x().apply(FakeRequest().withBody(fakeBody))
        status(request) must be(BAD_REQUEST)

      }
    }


  }


  val mockNotificationService = mock[NotificationEmailService]


  object TestNotificationEmailController extends NotificationEmailController {
    override val notificationEmailService = mockNotificationService
  }

  override def beforeEach(): Unit = {
     reset(mockNotificationService)
  }

}


 /* implicit override lazy val app: FakeApplication = new FakeApplication(
    additionalConfiguration = Map("auditing.enabled" -> "false")
  )*/


