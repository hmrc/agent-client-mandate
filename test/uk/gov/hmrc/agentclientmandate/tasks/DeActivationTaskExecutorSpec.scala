/*
 * Copyright 2024 HM Revenue & Customs
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

import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import java.time.Instant
import org.mockito.ArgumentMatchers._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{reset, when}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientmandate.connectors.{EmailSent, EtmpConnector, TaxEnrolmentConnector}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.agentclientmandate.services.{MandateFetchService, MandateUpdateService, NotificationEmailService}
import uk.gov.hmrc.agentclientmandate.utils.Generators._
import uk.gov.hmrc.agentclientmandate.utils.MockMetricsCache
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.tasks.{Phase, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeActivationTaskExecutorSpec extends TestKit(ActorSystem("activation-task")) with AnyWordSpecLike
  with BeforeAndAfterAll with DefaultTimeout with ImplicitSender with MockitoSugar with BeforeAndAfterEach {

  lazy val phaseCommit: Phase.Value = Phase.Commit
  lazy val phaseRollback: Phase.Value = Phase.Rollback
  lazy val startSignal: Start = Start(Map("clientId" -> "clientId", "agentPartyId" -> "agentPartyId", "mandateId" -> "mandateId"))
  lazy val startSignal1: Start = Start(Map("clientId" -> "clientId", "agentPartyId" -> "agentPartyId", "mandateId" -> "mandateId", "credId" -> "credId"))
  lazy val nextSignal: Next = Next(
    "gg-proxy-deactivation", Map(
      "serviceIdentifier" -> "serviceIdentifier",
      "agentCode" -> "agentCode",
      "clientId" -> "clientId",
      "mandateId" -> "mandateId",
      "agentPartyId" -> "agentPartyId"))
  lazy val nextSignalTaxEnrolment: Next = Next(
    "gg-proxy-deactivation", Map(
      "serviceIdentifier" -> "serviceIdentifier",
      "agentCode" -> "agentCode",
      "credId" -> "credId",
      "groupId" -> "groupId",
      "clientId" -> "clientId",
      "mandateId" -> "mandateId",
      "agentPartyId" -> "agentPartyId",
      "userType" -> "agent"))
  lazy val finalizeSignal: Next = Next(
    "finalize-deactivation", Map(
      "serviceIdentifier" -> "serviceIdentifier",
      "clientId" -> "clientId",
      "agentCode" -> "agentCode",
      "mandateId" -> "mandateId",
      "credId" -> "credId",
      "userType" -> "client"))
  lazy val finalizeSignal1: Next = Next(
    "finalize-deactivation", Map(
      "serviceIdentifier" -> "serviceIdentifier",
      "clientId" -> "clientId",
      "agentCode" -> "agentCode",
      "mandateId" -> "mandateId",
      "credId" -> "credId",
      "userType" -> "agent"))
  val taxEnrolmentMock: TaxEnrolmentConnector = mock[TaxEnrolmentConnector]
  val etmpMock: EtmpConnector = mock[EtmpConnector]
  val mockMandateFetchService: MandateFetchService = mock[MandateFetchService]
  val mockMandateRepository: MandateRepository = mock[MandateRepository]
  val mockEmailNotificationService: NotificationEmailService = mock[NotificationEmailService]
  val mockMandateUpdateService: MandateUpdateService = mock[MandateUpdateService]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockMandateRepo: MandateRepo = mock[MandateRepo]

  val timeToUse: Instant = Instant.now()
  val mandate: Mandate = Mandate(mandateReferenceGen.sample.get,
    User("credid", nameGen.sample.get, None),
    agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails("", Some(""))),
    currentStatus = MandateStatus(Status.New, timeToUse, "credid"),
    subscription = Subscription(None, Service("ated", "ATED")),
    clientDisplayName = "client display name"
  )
  val updatedMandate: Mandate = Mandate(mandateReferenceGen.sample.get,
    User("credid", nameGen.sample.get, None),
    agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, Some(""))),
    clientParty = Some(Party("safe-id", "client-name", PartyType.Organisation, ContactDetails(emailGen.sample.get))),
    currentStatus = MandateStatus(Status.Approved, timeToUse, "credid"),
    statusHistory = Seq(MandateStatus(Status.New, Instant.now(), "credid")),
    subscription = Subscription(Some("ated-ref-no"), Service("ated", "ATED")),
    clientDisplayName = "client display name"
  )
  val updatedMandate1: Mandate = Mandate(mandateReferenceGen.sample.get,
    User("credid", nameGen.sample.get, None),
    agentParty = Party(partyIDGen.sample.get, nameGen.sample.get, PartyType.Organisation, ContactDetails(emailGen.sample.get, Some(""))),
    clientParty = Some(Party("safe-id", "client-name", PartyType.Organisation, ContactDetails(emailGen.sample.get))),
    currentStatus = MandateStatus(Status.Cancelled, Instant.now(), "credid"),
    statusHistory = Seq(MandateStatus(Status.PendingCancellation, Instant.now(), "credid"), MandateStatus(Status.Approved, timeToUse, "credid")),
    subscription = Subscription(Some("ated-ref-no"), Service("ated", "ATED")),
    clientDisplayName = "client display name"
  )

  override def beforeEach(): Unit = {
    reset(taxEnrolmentMock)
    reset(etmpMock)
    reset(mockMandateFetchService)
    reset(mockMandateRepository)

    when(MockMetricsCache.mockMetrics.startTimer(any()))
      .thenReturn(null)
    when(mockMandateRepo.repository)
      .thenReturn(mockMandateRepository)
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  object DeActivationTaskExecutorMock {
    def props(): Props = Props(classOf[DeactivationTaskExecutor])
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  lazy val deActivationTaskService: DeActivationTaskService = new DeActivationTaskService(
    etmpMock,
    mockMandateUpdateService,
    taxEnrolmentMock,
    MockMetricsCache.mockMetrics,
    mockEmailNotificationService,
    mockAuditConnector,
    mockMandateFetchService,
    mockMandateRepo
  )

  "DeActivationTaskExecutor" should {
    lazy val message = DeActivationTaskMessage(deActivationTaskService, MockMetricsCache.mockMetrics)

    "execute and FINISH" when {
      "signal is Next('finalize-deactivation', args) and userType is Client" in {
        when(mockMandateFetchService.fetchClientMandate(any())(any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockMandateRepository.updateMandate(any())(any()))
          .thenReturn(Future.successful(MandateUpdated(updatedMandate1)))
        when(mockEmailNotificationService.sendMail(ArgumentMatchers.eq("client@mail.com"), any(),
          ArgumentMatchers.eq(Some("client")), any(), any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(EmailSent))

        val actorRef = system.actorOf(DeActivationTaskExecutorMock.props(
        ))

        actorRef ! TaskCommand(StageComplete(finalizeSignal, phaseCommit), message)
        expectMsg(TaskCommand(Complete(Map(
          "credId" -> "credId",
          "clientId" -> "clientId",
          "agentCode" -> "agentCode",
          "mandateId" -> "mandateId",
          "serviceIdentifier" -> "serviceIdentifier",
          "userType" -> "client",
          "mandateId" -> "mandateId"), phaseCommit), message))
      }

      "signal is Next('finalize-deactivation', args) and userType is Agent, send mail to agent" in {
        when(mockMandateFetchService.fetchClientMandate(any())(any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockMandateRepository.updateMandate(any())(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))
        when(mockEmailNotificationService.sendMail(ArgumentMatchers.eq("agent@mail.com"), any(),
          ArgumentMatchers.eq(Some("agent")), any(), any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(EmailSent))

        val actorRef = system.actorOf(DeActivationTaskExecutorMock.props(
        ))

        actorRef ! TaskCommand(StageComplete(finalizeSignal1, phaseCommit), message)
        expectMsg(TaskCommand(Complete(Map(
          "credId" -> "credId",
          "clientId" -> "clientId",
          "agentCode" -> "agentCode",
          "mandateId" -> "mandateId",
          "serviceIdentifier" -> "serviceIdentifier",
          "userType" -> "agent", "mandateId" -> "mandateId"), phaseCommit), message))
      }

      "signal is Next('finalize-deactivation', args) and userType is Agent, sends mail to client" in {
        when(mockMandateFetchService.fetchClientMandate(any())(any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockMandateRepository.updateMandate(any())(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate1)))
        when(mockEmailNotificationService.sendMail(ArgumentMatchers.eq("client@mail.com"), any(),
          ArgumentMatchers.eq(Some("client")), any(),any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(EmailSent))

        val actorRef = system.actorOf(DeActivationTaskExecutorMock.props(
        ))

        actorRef ! TaskCommand(StageComplete(finalizeSignal1, phaseCommit), message)
        expectMsg(TaskCommand(Complete(Map(
          "credId" -> "credId",
          "clientId" -> "clientId",
          "agentCode" -> "agentCode",
          "mandateId" -> "mandateId",
          "serviceIdentifier" -> "serviceIdentifier",
          "userType" -> "agent"), phaseCommit), message))
      }
    }

    "fail to execute" when {

      "signal is START but the ETMP fails" in {
        when(etmpMock.maintainAtedRelationship(any())) thenReturn Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, ""))

        val actorRef = system.actorOf(DeActivationTaskExecutorMock.props(
        ))

        actorRef ! TaskCommand(New(startSignal), message)
        expectMsgType[TaskCommand]
      }

      "signal is Next('finalize-deactivation', args) but no mandate is returned" in {
        when(mockMandateFetchService.fetchClientMandate(any())(any())).thenReturn(Future.successful(MandateNotFound))
        when(mockMandateRepository.updateMandate(any())(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))
        when(mockEmailNotificationService.sendMail(ArgumentMatchers.eq(updatedMandate.id),
          any(), any(),any(), any(), any(), any(), any())(any())).thenReturn(Future.successful(EmailSent))

        val actorRef = system.actorOf(DeActivationTaskExecutorMock.props(
        ))

        actorRef ! TaskCommand(StageComplete(finalizeSignal, phaseCommit), message)
        expectMsgType[TaskCommand]
      }

      "signal is Next('finalize-deactivation', args) but mandate update fails" in {
        when(mockMandateFetchService.fetchClientMandate(any())(any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockMandateRepository.updateMandate(any())(any())).thenReturn(Future.successful(MandateUpdateError))
        when(mockEmailNotificationService.sendMail(ArgumentMatchers.eq(updatedMandate.id), any(), any(), any(), any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(EmailSent))

        val actorRef = system.actorOf(DeActivationTaskExecutorMock.props(
        ))

        actorRef ! TaskCommand(StageComplete(finalizeSignal, phaseCommit), message)
        expectMsgType[TaskCommand]
      }
    }

    "rollback" when {

      "the Signal is START and move to Finish" in {
        when(mockMandateFetchService.fetchClientMandate(any())(any())).thenReturn(Future.successful(MandateFetched(mandate)))
        when(mockMandateRepository.updateMandate(any())(any())).thenReturn(Future.successful(MandateUpdated(updatedMandate)))

        val actorRef = system.actorOf(DeActivationTaskExecutorMock.props(
        ))

        actorRef ! TaskCommand(StageComplete(startSignal1, phaseRollback), message)
        //executorActor.execSignal must be startArgs
        expectMsg(TaskCommand(Complete(Map(
          "clientId" -> "clientId",
          "agentPartyId" -> "agentPartyId",
          "mandateId" -> "mandateId",
          "credId" -> "credId"), phaseRollback), message))
      }

      "the signal is Next('gg-proxy-deactivation', args) and move to START signal" in {
        when(etmpMock.maintainAtedRelationship(any())) thenReturn Future.successful(HttpResponse(OK, ""))

        val actorRef = system.actorOf(DeActivationTaskExecutorMock.props(
        ))

        actorRef ! TaskCommand(StageComplete(nextSignal, phaseRollback), message)
        //executorActor.execSignal must be startArgs
        expectMsg(TaskCommand(StageComplete(Start(Map(
          "serviceIdentifier" -> "serviceIdentifier",
          "clientId" -> "clientId",
          "agentCode" -> "agentCode",
          "agentPartyId" -> "agentPartyId",
          "mandateId" -> "mandateId")), phaseRollback), message))
      }

      "the signal is Next('finalize-deactivation', args) and move to Next('gg-proxy-deactivation', args) signal" in {
        when(etmpMock.maintainAtedRelationship(any())) thenReturn Future.successful(HttpResponse(OK, ""))

        val actorRef = system.actorOf(DeActivationTaskExecutorMock.props(
        ))

        actorRef ! TaskCommand(StageComplete(finalizeSignal, phaseRollback), message)
        //executorActor.execSignal must be startArgs
        expectMsg(TaskCommand(StageComplete(Next(
          "gg-proxy-deactivation", Map(
            "credId" -> "credId",
            "clientId" -> "clientId",
            "agentCode" -> "agentCode",
            "mandateId" -> "mandateId",
            "serviceIdentifier" -> "serviceIdentifier",
            "userType" -> "client")), phaseRollback), message))
      }
    }

    "handle rollback failure" when {

      "rollback fails at START signal" in {
        val actorRef = system.actorOf(DeActivationTaskExecutorMock.props(
        ))

        actorRef ! TaskCommand(Failed(startSignal, phaseRollback), message)
        expectMsg(TaskCommand(RollbackFailureHandled(Map("clientId" -> "clientId", "agentPartyId" -> "agentPartyId", "mandateId" -> "mandateId")), message))
      }
    }

    "rollback the activity in Next('gg-proxy-deactivation', args)" when {

      "rollback fails at Next('gg-proxy-deactivation', args signal" in {
        val actorRef = system.actorOf(DeActivationTaskExecutorMock.props(
        ))

        actorRef ! TaskCommand(Failed(nextSignal, phaseRollback), message)

        expectMsg(TaskCommand(RollbackFailureHandled(Map(
          "serviceIdentifier" -> "serviceIdentifier",
          "clientId" -> "clientId",
          "agentCode" -> "agentCode",
          "agentPartyId" -> "agentPartyId",
          "mandateId" -> "mandateId")), message))
      }
    }

    "execute and move to 'finalize-deactivation' step tax enrolments" when {
      "signal is Next('gg-proxy-deactivation', args)" in {
        when(taxEnrolmentMock.deAllocateAgent(any(), any(), any(), any())(any())) thenReturn Future.successful(HttpResponse(NO_CONTENT, ""))

        val actorRef = system.actorOf(DeActivationTaskExecutorMock.props(
        ))

        actorRef ! TaskCommand(StageComplete(nextSignalTaxEnrolment, phaseCommit), message)
        expectMsg(TaskCommand(StageComplete(Next(
          "finalize-deactivation", Map(
            "serviceIdentifier" -> "serviceIdentifier",
            "groupId" -> "groupId",
            "credId" -> "credId",
            "clientId" -> "clientId",
            "agentCode" -> "agentCode",
            "agentPartyId" -> "agentPartyId",
            "mandateId" -> "mandateId",
            "userType" -> "agent")), phaseCommit), message))
      }

      "signal is Next('gg-proxy-deactivation', args) not found returned" in {
        when(taxEnrolmentMock.deAllocateAgent(any(), any(), any(), any())(any())) thenReturn Future.successful(HttpResponse(NOT_FOUND, ""))

        val actorRef = system.actorOf(DeActivationTaskExecutorMock.props())

        actorRef ! TaskCommand(StageComplete(nextSignalTaxEnrolment, phaseCommit), message)
        expectMsg(TaskCommand(StageComplete(Next(
          "finalize-deactivation", Map(
            "serviceIdentifier" -> "serviceIdentifier",
            "groupId" -> "groupId",
            "credId" -> "credId",
            "clientId" -> "clientId",
            "agentCode" -> "agentCode",
            "agentPartyId" -> "agentPartyId",
            "mandateId" -> "mandateId",
            "userType" -> "agent")), phaseCommit), message))
      }

      "signal is Next('gg-proxy-deactivation', args) failure code returned" in {
        when(taxEnrolmentMock.deAllocateAgent(any(), any(), any(), any())(any())) thenReturn Future.successful(HttpResponse(INTERNAL_SERVER_ERROR, ""))

        val actorRef = system.actorOf(DeActivationTaskExecutorMock.props(
        ))

        actorRef ! TaskCommand(StageComplete(nextSignalTaxEnrolment, phaseCommit), message)
        assert(expectMsgType[TaskCommand].status.isInstanceOf[StageFailed])
      }

    }
  }
}
