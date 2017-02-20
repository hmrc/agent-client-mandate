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

package uk.gov.hmrc.agentclientmandate.tasks

import akka.actor.{ActorSystem, Props}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestActorRef, TestKit}
import org.joda.time.DateTime
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.connectors.{AuthConnector, EmailSent, EtmpConnector, GovernmentGatewayProxyConnector}
import uk.gov.hmrc.agentclientmandate.metrics.Metrics
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.services.{MandateFetchService, NotificationEmailService}
import uk.gov.hmrc.domain.{AtedUtr, Generator}
import uk.gov.hmrc.play.http.{BadRequestException, HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.tasks._

import scala.concurrent.Future
import scala.util.Success


class ActivationTaskExecutorMock(override val etmpConnector: EtmpConnector, override val ggProxyConnector: GovernmentGatewayProxyConnector,
                                 override val fetchService: MandateFetchService, override val mandateRepository: MandateRepository, override val emailNotificationService: NotificationEmailService) extends ActivationTaskExecutor

class ActivationTaskExecutorSpec extends TestKit(ActorSystem("activation-task")) with UnitSpec
  with BeforeAndAfterAll with DefaultTimeout with ImplicitSender with MockitoSugar with BeforeAndAfterEach with OneServerPerSuite {

  //lazy val activationTaskExec = TestActorRef[ActivationTaskExecutor]
  //lazy val executorActor = activationTaskExec.underlyingActor
  lazy val phaseCommit = Phase.Commit
  lazy val phaseRollback = Phase.Rollback

  val ggProxyMock = mock[GovernmentGatewayProxyConnector]
  val etmpMock = mock[EtmpConnector]
  val mockMandateFetchService = mock[MandateFetchService]
  val mockMandateRepository = mock[MandateRepository]
  val mockEmailNotificationService = mock[NotificationEmailService]


  object ActivationTaskExecutorMock {
    def props(etmpConnector: EtmpConnector, ggConnector: GovernmentGatewayProxyConnector, mandateFetchService: MandateFetchService, mandateRepository: MandateRepository, emailNotificationService: NotificationEmailService) =
      Props(classOf[ActivationTaskExecutorMock], etmpConnector, ggConnector, mandateFetchService, mandateRepository, emailNotificationService)
  }

  lazy val startSignal = Start(Map("clientId" -> "clientId", "agentPartyId" -> "agentPartyId"))
  lazy val startSignal1 = Start(Map("clientId" -> "clientId", "agentPartyId" -> "agentPartyId", "mandateId" -> "mandateId","credId" -> "credId"))
  lazy val nextSignal = Next("gg-proxy-activation", Map("serviceIdentifier" -> "serviceIdentifier", "clientId" -> "clientId", "agentCode" -> "agentCode", "clientId" -> "clientId", "agentPartyId" -> "agentPartyId"))
  lazy val finalizeSignal = Next("finalize-activation", Map("serviceIdentifier" -> "serviceIdentifier", "clientId" -> "clientId", "agentCode" -> "agentCode", "mandateId" -> "mandateId", "credId" -> "credId"))

  val timeToUse = DateTime.now()
  val mandate = Mandate("AS12345678",
    User("credid", "Joe Bloggs", None),
    agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("", Some(""))),
    currentStatus = MandateStatus(Status.New, timeToUse, "credid"),
    subscription = Subscription(None, Service("ated", "ATED")),
    clientDisplayName = "client display name"
  )
  val updatedMandate = Mandate("AS12345678",
    User("credid", "Joe Bloggs", None),
    agentParty = Party("JARN123456", "Joe Bloggs", PartyType.Organisation, ContactDetails("", Some(""))),
    clientParty = Some(Party("safe-id", "client-name", PartyType.Organisation, ContactDetails("client@mail.com"))),
    currentStatus = MandateStatus(Status.Approved, timeToUse, "credid"),
    subscription = Subscription(Some("ated-ref-no"), Service("ated", "ATED")),
    clientDisplayName = "client display name"
  )


  override def beforeEach(): Unit = {
    reset(ggProxyMock)
    reset(etmpMock)
    reset(mockMandateFetchService)
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  implicit val hc = HeaderCarrier()

  "ActivationTaskExecutor" should {

    "execute and move to GG-PROXY allocation step" when {

      "signal is START" in {
        when(etmpMock.maintainAtedRelationship(Matchers.any())) thenReturn Future.successful(HttpResponse(OK, None))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props(etmpMock, ggProxyMock, mockMandateFetchService, mockMandateRepository, mockEmailNotificationService))

        actorRef ! TaskCommand(New(startSignal))
        expectMsg(TaskCommand(StageComplete(Next("gg-proxy-activation", Map("clientId" -> "clientId", "agentPartyId" -> "agentPartyId")), phaseCommit)))
      }
    }

    "execute and move to 'finalize' step" when {

      "signal is Next('gg-proxy-activation', args)" in {
        when(ggProxyMock.allocateAgent(Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(OK, None))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props(etmpMock, ggProxyMock, mockMandateFetchService, mockMandateRepository, mockEmailNotificationService))

        actorRef ! TaskCommand(StageComplete(nextSignal, phaseCommit))
        expectMsg(TaskCommand(StageComplete(Next("finalize-activation", Map("serviceIdentifier" -> "serviceIdentifier", "clientId" -> "clientId", "agentCode" -> "agentCode", "agentPartyId" -> "agentPartyId")), phaseCommit)))
      }
    }


    "execute and FINISH" when {

      "signal is Next('finalize-activation', args)" in {
        when(mockMandateFetchService.fetchClientMandate(Matchers.any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockMandateRepository.updateMandate(Matchers.any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))
        when(mockEmailNotificationService.sendMail(Matchers.eq(updatedMandate.id), Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(EmailSent))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props(etmpMock, ggProxyMock, mockMandateFetchService, mockMandateRepository, mockEmailNotificationService))

        actorRef ! TaskCommand(StageComplete(finalizeSignal, phaseCommit))
        expectMsg(TaskCommand(Complete(Map("credId" -> "credId", "clientId" -> "clientId", "agentCode" -> "agentCode", "mandateId" -> "mandateId", "serviceIdentifier" -> "serviceIdentifier"), phaseCommit)))
      }
    }

    "fail to execute" when {

      "signal is START but the ETMP fails" in {
        when(etmpMock.maintainAtedRelationship(Matchers.any())) thenReturn Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, None))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props(etmpMock, ggProxyMock, mockMandateFetchService, mockMandateRepository, mockEmailNotificationService))

        actorRef ! TaskCommand(New(startSignal))
        expectMsgType[TaskCommand]
      }

      "signal is Next('gg-proxy', args) but the GG fails" in {
        when(ggProxyMock.allocateAgent(Matchers.any())(Matchers.any())) thenReturn Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, None))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props(etmpMock, ggProxyMock, mockMandateFetchService, mockMandateRepository, mockEmailNotificationService))

        actorRef ! TaskCommand(StageComplete(nextSignal, phaseCommit))
        expectMsgType[TaskCommand]
      }

      "signal is Next('finalize', args) but no mandate is returned" in {
        when(mockMandateFetchService.fetchClientMandate(Matchers.any())).thenReturn(Future.successful(MandateNotFound))
        when(mockMandateRepository.updateMandate(Matchers.any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))
        when(mockEmailNotificationService.sendMail(Matchers.eq(updatedMandate.id), Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(EmailSent))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props(etmpMock, ggProxyMock, mockMandateFetchService, mockMandateRepository, mockEmailNotificationService))

        actorRef ! TaskCommand(StageComplete(finalizeSignal, phaseCommit))
        expectMsgType[TaskCommand]
      }

      "signal is Next('finalize', args) but mandate update fails" in {
        when(mockMandateFetchService.fetchClientMandate(Matchers.any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockMandateRepository.updateMandate(Matchers.any())).thenReturn(Future.successful(MandateUpdateError))
        when(mockEmailNotificationService.sendMail(Matchers.eq(updatedMandate.id), Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(Future.successful(EmailSent))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props(etmpMock, ggProxyMock, mockMandateFetchService, mockMandateRepository, mockEmailNotificationService))

        actorRef ! TaskCommand(StageComplete(finalizeSignal, phaseCommit))
        expectMsgType[TaskCommand]
      }
    }

    "rollback" when {

      "the Signal is START and move to Finish" in {
        when(mockMandateFetchService.fetchClientMandate(Matchers.any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockMandateRepository.updateMandate(Matchers.any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props(etmpMock, ggProxyMock, mockMandateFetchService, mockMandateRepository, mockEmailNotificationService))

        actorRef ! TaskCommand(StageComplete(startSignal1, phaseRollback))
        expectMsg(TaskCommand(Complete(Map("clientId" -> "clientId", "agentPartyId" -> "agentPartyId", "mandateId" -> "mandateId","credId" -> "credId"), phaseRollback)))

      }

      "the signal is Next('gg-proxy-activation', args) and move to START signal" in {
        when(etmpMock.maintainAtedRelationship(Matchers.any())) thenReturn Future.successful(HttpResponse(OK, None))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props(etmpMock, ggProxyMock, mockMandateFetchService, mockMandateRepository, mockEmailNotificationService))

        actorRef ! TaskCommand(StageComplete(nextSignal, phaseRollback))
        expectMsg(TaskCommand(StageComplete(Start(Map("serviceIdentifier" -> "serviceIdentifier", "clientId" -> "clientId", "agentCode" -> "agentCode", "agentPartyId" -> "agentPartyId")), phaseRollback)))

      }

      "the signal is Next('finalize', args) and move to Next('gg-proxy-activation', args) signal" in {
        when(etmpMock.maintainAtedRelationship(Matchers.any())) thenReturn Future.successful(HttpResponse(OK, None))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props(etmpMock, ggProxyMock, mockMandateFetchService, mockMandateRepository, mockEmailNotificationService))

        actorRef ! TaskCommand(StageComplete(finalizeSignal, phaseRollback))
        expectMsg(TaskCommand(StageComplete(Next("gg-proxy-activation",Map("credId" -> "credId", "clientId" -> "clientId", "agentCode" -> "agentCode", "mandateId" -> "mandateId", "serviceIdentifier" -> "serviceIdentifier")), phaseRollback)))

      }
    }


    "handle rollback failure" when {

      "rollback fails at START signal" in {

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props(etmpMock, ggProxyMock, mockMandateFetchService, mockMandateRepository, mockEmailNotificationService))

        actorRef ! TaskCommand(Failed(startSignal, phaseRollback))
        expectMsg(TaskCommand(RollbackFailureHandled(Map("clientId" -> "clientId", "agentPartyId" -> "agentPartyId"))))
      }
    }


    "rollback the activity in Next('gg-proxy-activation', args)" when {

      "rollback fails at Next('gg-proxy-activation', args signal" in {

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props(etmpMock, ggProxyMock, mockMandateFetchService, mockMandateRepository, mockEmailNotificationService))

        actorRef ! TaskCommand(Failed(nextSignal, phaseRollback))

        expectMsg(TaskCommand(RollbackFailureHandled(Map("serviceIdentifier" -> "serviceIdentifier", "clientId" -> "clientId", "agentCode" -> "agentCode", "agentPartyId" -> "agentPartyId"))))
      }
    }

  }

}
