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

package uk.gov.hmrc.agentclientmandate.actors

import akka.actor.{ActorSystem, Props}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import uk.gov.hmrc.agentclientmandate.services.MandateCreateService
import uk.gov.hmrc.play.test.UnitSpec
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import org.mockito.Matchers
import org.mockito.Mockito._
import uk.gov.hmrc.agentclientmandate.models.ExistingMandateDto

import scala.concurrent.Future
import scala.concurrent.duration._


class ImportExistingMandateActorMock(val createService: MandateCreateService) extends ImportExistingMandateActor with ImportExistingMandateActorComponent

class ImportExistingMandateActorSpec extends TestKit(ActorSystem("TestCalculationActorSystem")) with UnitSpec with MockitoSugar
  with BeforeAndAfterAll with DefaultTimeout with ImplicitSender with BeforeAndAfter {

  val createServiceMock = mock[MandateCreateService]

  val testTimeout = 10 seconds

  before {
    reset(createServiceMock)
  }

  override def afterAll: Unit = {
    shutdown()
  }

  object ImportExistingMandateActorMock {
    def props(createService: MandateCreateService) = Props(classOf[ImportExistingMandateActorMock], createService)
  }

  "ImportExistingMandateActor" must {
    "successfully import existing relationship" in {
      when(createServiceMock.createMandateForExistingRelationships(Matchers.any())).thenReturn(Future.successful(true))

      val actorRef = system.actorOf(ImportExistingMandateActorMock.props(createServiceMock))

      within(testTimeout) {

        actorRef ! ExistingMandateDto("", "", "", "", "")
        expectMsg(true)
      }

    }
  }

}
