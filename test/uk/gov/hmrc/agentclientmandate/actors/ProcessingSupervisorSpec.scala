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
import akka.contrib.throttle.Throttler.SetTarget
import akka.testkit._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import reactivemongo.api.DefaultDB
import reactivemongo.json.collection.JSONCollection
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Matchers._
import uk.gov.hmrc.agentclientmandate.models.GGRelationshipDto
import uk.gov.hmrc.agentclientmandate.repositories.MandateRepository

import scala.concurrent.duration._
import scala.concurrent.Future

class ProcessingSupervisorSpec extends TestKit(ActorSystem("TestProcessingSystem")) with UnitSpec with MockitoSugar with OneServerPerSuite
  with BeforeAndAfterAll with DefaultTimeout with ImplicitSender with ActorUtils {

  val mockLockRepo = mock[LockRepository]

  override def beforeAll = {
    when(mockLockRepo.lock(anyString, anyString, any[org.joda.time.Duration])) thenReturn true
  }

  override def afterAll: Unit = {
    shutdown()
  }

  "processing supervisor" must {

    "send requests to throttler" in {

      val throttlerProbe = TestProbe()
      val processingActorProbe = TestProbe()
      val mockRepository = mock[MandateRepository]

      val processingSupervisor = TestActorRef(Props(new ProcessingSupervisor {
        override lazy val throttler = throttlerProbe.ref
        override lazy val processingActor = processingActorProbe.ref
        override lazy val repository = mockRepository
        override val lockRepo = mockLockRepo
      }),"process-supervisor")


      val ggRelationship = GGRelationshipDto("","","","")

      when(mockRepository.findGGRelationshipsToProcess()).thenReturn(Future.successful(List(ggRelationship)))

      within(5 seconds) {

        processingSupervisor ! START
        processingSupervisor ! START

        throttlerProbe.expectMsgClass(classOf[SetTarget])
        throttlerProbe.expectMsg(ggRelationship)
        throttlerProbe.expectMsg(STOP)
        processingSupervisor ! STOP // simulate stop

      }
    }

    "send request to start with no requests queued" in {

      val throttlerProbe = TestProbe()
      val calculationActorProbe = TestProbe()
      val mockRepository = mock[MandateRepository]

      val processingSupervisor = TestActorRef(Props(new ProcessingSupervisor {
        override lazy val throttler = throttlerProbe.ref
        override lazy val processingActor = calculationActorProbe.ref
        override lazy val repository = mockRepository
        override val lockRepo = mockLockRepo
      }),"process-supervisor2")

      when(mockRepository.findGGRelationshipsToProcess()).thenReturn(Future.successful(Nil))

      within(5 seconds) {
        processingSupervisor ! START
        throttlerProbe.expectMsgClass(classOf[SetTarget])
        throttlerProbe.expectMsg(STOP)
        processingSupervisor ! STOP // simulate stop
      }
    }


  }


}
