/*
 * Copyright 2021 HM Revenue & Customs
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
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, WordSpecLike}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.connectors.{EmailSent, EtmpConnector, TaxEnrolmentConnector}
import uk.gov.hmrc.agentclientmandate.metrics.ServiceMetrics
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.services.{MandateFetchService, MandateUpdateService, NotificationEmailService}
import uk.gov.hmrc.agentclientmandate.utils.Generators._
import uk.gov.hmrc.agentclientmandate.utils.MockMetricsCache
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.tasks._

import scala.concurrent.Future

class ActivationTaskExecutorSpec extends TestKit(ActorSystem("activation-task")) with WordSpecLike
  with BeforeAndAfterAll with DefaultTimeout with ImplicitSender with MockitoSugar with BeforeAndAfterEach {

  lazy val phaseCommit = Phase.Commit
  lazy val phaseRollback = Phase.Rollback
  lazy val startSignal = Start(Map("clientId" -> "clientId", "agentPartyId" -> "agentPartyId", "mandateId" -> "mandateId"))
  lazy val startSignal1 = Start(Map("clientId" -> "clientId", "agentPartyId" -> "agentPartyId", "mandateId" -> "mandateId", "credId" -> "credId"))
  lazy val nextSignal = Next("gg-proxy-activation", Map("serviceIdentifier" -> "serviceIdentifier", "clientId" -> "clientId", "agentCode" -> "agentCode", "clientId" -> "clientId", "agentPartyId" -> "agentPartyId", "mandateId" -> "mandateId", "groupId" -> "groupId", "credId" -> "credId"))
  lazy val finalizeSignal = Next("finalize-activation", Map("serviceIdentifier" -> "serviceIdentifier", "clientId" -> "clientId", "agentCode" -> "agentCode", "mandateId" -> "mandateId", "credId" -> "credId"))
  val etmpMock = mock[EtmpConnector]
  val mockMandateFetchService = mock[MandateFetchService]
  val mockMandateUpdateService = mock[MandateUpdateService]
  val mockMandateRepository = mock[MandateRepository]
  val mockEmailNotificationService = mock[NotificationEmailService]
  val taxEnrolmentMock = mock[TaxEnrolmentConnector]
  val mockServiceMetrics = mock[ServiceMetrics]
  val mockAuditConnector = mock[AuditConnector]
  val mockMandateRepo = mock[MandateRepo]

  val timeToUse = DateTime.now()
  val mandate = Mandate(mandateReferenceGen.sample.get,
    User("credid", nameGen.sample.get, None),
    agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails("", Some(""))),
    clientParty = Some(Party("safe-id", "client-name", PartyType.Organisation, ContactDetails(emailGen.sample.get))),
    currentStatus = MandateStatus(Status.New, timeToUse, "credid"),
    subscription = Subscription(None, Service("ated", "ATED")),
    clientDisplayName = "client display name"
  )
  val updatedMandate = Mandate(mandateReferenceGen.sample.get,
    User("credid", nameGen.sample.get, None),
    agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails("", Some(""))),
    clientParty = Some(Party("safe-id", "client-name", PartyType.Organisation, ContactDetails(emailGen.sample.get))),
    currentStatus = MandateStatus(Status.Approved, timeToUse, "credid"),
    statusHistory = Seq(MandateStatus(Status.New, new DateTime(), "credid")),
    subscription = Subscription(Some("ated-ref-no"), Service("ated", "ATED")),
    clientDisplayName = "client display name"
  )
  val updatedMandate1 = Mandate(mandateReferenceGen.sample.get,
    User("credid", nameGen.sample.get, None),
    agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails("", Some(emailGen.sample.get))),
    clientParty = Some(Party("safe-id", "client-name", PartyType.Organisation, ContactDetails(emailGen.sample.get))),
    currentStatus = MandateStatus(Status.Active, new DateTime(), "credid"),
    statusHistory = Seq(MandateStatus(Status.PendingActivation, new DateTime(), "credid"), MandateStatus(Status.Approved, timeToUse, "credid")),
    subscription = Subscription(Some("ated-ref-no"), Service("ated", "ATED")),
    clientDisplayName = "client display name"
  )

  override def beforeEach(): Unit = {
    reset(etmpMock)
    reset(taxEnrolmentMock)
    reset(mockMandateFetchService)
    reset(mockMandateRepository)

    when(MockMetricsCache.mockMetrics.startTimer(any()))
      .thenReturn(null)
    when(mockMandateRepo.repository)
      .thenReturn(mockMandateRepository)
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  object ActivationTaskExecutorMock {
    def props(): Props = Props(classOf[ActivationTaskExecutor])
  }

  lazy val activationTaskService: ActivationTaskService = new ActivationTaskService(
    etmpMock,
    mockMandateUpdateService,
    taxEnrolmentMock,
    MockMetricsCache.mockMetrics,
    mockEmailNotificationService,
    mockAuditConnector,
    mockMandateFetchService,
    mockMandateRepo
  )


  implicit val hc = HeaderCarrier()

  "ActivationTaskExecutor" should {
    lazy val message = ActivationTaskMessage(activationTaskService, MockMetricsCache.mockMetrics)

    "execute and move to GG-PROXY allocation step" when {

      "signal is START" in {
        when(etmpMock.maintainAtedRelationship(any())) thenReturn Future.successful(HttpResponse(OK, ""))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props())

        actorRef ! TaskCommand(New(startSignal), message)
        expectMsg(TaskCommand(StageComplete(Next("gg-proxy-activation", Map("clientId" -> "clientId", "agentPartyId" -> "agentPartyId", "mandateId" -> "mandateId")), phaseCommit), message))
      }
    }

    "execute and move to 'finalize' step Tax Enrolment" when {
      "signal is Next('gg-proxy-activation', args)" in {


        when(taxEnrolmentMock.allocateAgent(any(), any(), any(), any())(any())) thenReturn Future.successful(HttpResponse(CREATED, ""))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props())

        actorRef ! TaskCommand(StageComplete(nextSignal, phaseCommit), message)
        expectMsg(TaskCommand(StageComplete(Next("finalize-activation", Map("serviceIdentifier" -> "serviceIdentifier", "clientId" -> "clientId",
          "agentCode" -> "agentCode", "agentPartyId" -> "agentPartyId", "mandateId" -> "mandateId", "groupId" -> "groupId", "credId" -> "credId")), phaseCommit), message))
      }

    }

    "execute and FINISH" when {
      "signal is Next('finalize-activation', args), sends mail to client" in {
        when(mockMandateFetchService.fetchClientMandate(any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate1)))
        when(mockEmailNotificationService.sendMail(ArgumentMatchers.eq("client@mail.com"), any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(Future.successful(EmailSent))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props())

        actorRef ! TaskCommand(StageComplete(finalizeSignal, phaseCommit), message)
        expectMsg(TaskCommand(Complete(Map("credId" -> "credId", "clientId" -> "clientId", "agentCode" -> "agentCode", "mandateId" -> "mandateId", "serviceIdentifier" -> "serviceIdentifier"), phaseCommit), message))
      }

      "signal is Next('finalize-activation', args), sends mail to agent" in {
        when(mockMandateFetchService.fetchClientMandate(any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))
        when(mockEmailNotificationService.sendMail(ArgumentMatchers.eq("agent@mail.com"), any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(Future.successful(EmailSent))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props())

        actorRef ! TaskCommand(StageComplete(finalizeSignal, phaseCommit), message)
        expectMsg(TaskCommand(Complete(Map("credId" -> "credId", "clientId" -> "clientId", "agentCode" -> "agentCode", "mandateId" -> "mandateId", "serviceIdentifier" -> "serviceIdentifier"), phaseCommit), message))
      }
    }

    "fail to execute" when {
      "signal is START but the ETMP fails" in {
        when(etmpMock.maintainAtedRelationship(any())) thenReturn Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, ""))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props())

        actorRef ! TaskCommand(New(startSignal), message)
        expectMsgType[TaskCommand]
      }

      "signal is Next('finalize', args) but no mandate is returned" in {
        when(mockMandateFetchService.fetchClientMandate(any())).thenReturn(Future.successful(MandateNotFound))
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))
        when(mockEmailNotificationService.sendMail(ArgumentMatchers.eq("client@mail.com"), any(), any(), any(), any(), any(),any(), any())(any())).thenReturn(Future.successful(EmailSent))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props())

        actorRef ! TaskCommand(StageComplete(finalizeSignal, phaseCommit), message)
        expectMsgType[TaskCommand]
      }

      "signal is Next('finalize', args) but mandate update fails" in {
        when(mockMandateFetchService.fetchClientMandate(any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdateError))
        when(mockEmailNotificationService.sendMail(any(), any(), any(), any(), any(), any(), any(), any())(any())).thenReturn(Future.successful(EmailSent))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props())

        actorRef ! TaskCommand(StageComplete(finalizeSignal, phaseCommit), message)
        expectMsgType[TaskCommand]
      }

      "signal is Next('finalize', args) but exception in email sent" in {

        val exception = new RuntimeException("some exception")

        when(mockMandateFetchService.fetchClientMandate(any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate1)))
        when(mockEmailNotificationService.sendMail(ArgumentMatchers.eq("client@mail.com"), any(), any(), any(), any(),any(), any(), any())(any())) thenThrow exception

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props())

        actorRef ! TaskCommand(StageComplete(finalizeSignal, phaseCommit), message)
        expectMsgType[TaskCommand]
      }
    }

    "rollback" when {

      "the Signal is START and move to Finish" in {
        when(mockMandateFetchService.fetchClientMandate(any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockMandateRepository.updateMandate(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props())

        actorRef ! TaskCommand(StageComplete(startSignal1, phaseRollback), message)
        expectMsg(TaskCommand(Complete(Map("clientId" -> "clientId", "agentPartyId" -> "agentPartyId", "mandateId" -> "mandateId", "credId" -> "credId"), phaseRollback), message))

      }

      "the signal is Next('gg-proxy-activation', args) and move to START signal" in {
        when(etmpMock.maintainAtedRelationship(any())) thenReturn Future.successful(HttpResponse(OK, ""))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props())

        actorRef ! TaskCommand(StageComplete(nextSignal, phaseRollback), message)
        expectMsg(TaskCommand(StageComplete(Start(Map("serviceIdentifier" -> "serviceIdentifier", "clientId" -> "clientId", "agentCode" -> "agentCode", "agentPartyId" -> "agentPartyId", "mandateId" -> "mandateId", "groupId" -> "groupId", "credId" -> "credId")), phaseRollback), message))

      }

      "the signal is Next('finalize', args) and move to Next('gg-proxy-activation', args) signal" in {
        when(etmpMock.maintainAtedRelationship(any())) thenReturn Future.successful(HttpResponse(OK, ""))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props())

        actorRef ! TaskCommand(StageComplete(finalizeSignal, phaseRollback), message)
        expectMsg(TaskCommand(StageComplete(Next("gg-proxy-activation", Map("credId" -> "credId", "clientId" -> "clientId", "agentCode" -> "agentCode", "mandateId" -> "mandateId", "serviceIdentifier" -> "serviceIdentifier")), phaseRollback), message))

      }
    }


    "handle rollback failure" when {

      "rollback fails at START signal" in {

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props())

        actorRef ! TaskCommand(Failed(startSignal, phaseRollback), message)
        expectMsg(TaskCommand(RollbackFailureHandled(Map("clientId" -> "clientId", "agentPartyId" -> "agentPartyId", "mandateId" -> "mandateId")), message))
      }
    }


    "rollback the activity in Next('gg-proxy-activation', args)" when {

      "rollback fails at Next('gg-proxy-activation', args signal" in {

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props())

        actorRef ! TaskCommand(Failed(nextSignal, phaseRollback), message)

        expectMsg(TaskCommand(RollbackFailureHandled(Map("serviceIdentifier" -> "serviceIdentifier", "clientId" -> "clientId", "agentCode" -> "agentCode", "agentPartyId" -> "agentPartyId", "mandateId" -> "mandateId", "groupId" -> "groupId", "credId" -> "credId")), message))
      }
    }

    "Error condition taxenrolments " when {
      "Return StageFailure when tax enrolments returns status other than CREATED" in {

        when(taxEnrolmentMock.allocateAgent(any(), any(), any(), any())(any())) thenReturn Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, ""))

        val actorRef = system.actorOf(ActivationTaskExecutorMock.props())

        actorRef ! TaskCommand(StageComplete(nextSignal, phaseCommit), message)

        assert(expectMsgType[TaskCommand].status.isInstanceOf[StageFailed])
      }
    }
  }

}
