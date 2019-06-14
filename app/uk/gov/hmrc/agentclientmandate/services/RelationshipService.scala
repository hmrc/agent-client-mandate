/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientmandate.services

import com.typesafe.config.{Config, ConfigFactory}
import javax.inject.Inject
import play.api.Logger
import uk.gov.hmrc.agentclientmandate.metrics.ServiceMetrics
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.tasks.{ActivationTaskExecutor, ActivationTaskService, DeActivationTaskService, DeactivationTaskExecutor}
import uk.gov.hmrc.agentclientmandate.utils.MandateConstants._
import uk.gov.hmrc.agentclientmandate.utils.MandateUtils
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.auth.core.{AuthorisedFunctions, PlayAuthConnector}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.tasks._

import scala.concurrent.{ExecutionContext, Future}

class DefaultRelationshipService @Inject()(val serviceMetrics: ServiceMetrics,
                                           val authConnector: PlayAuthConnector,
                                           val activationTaskService: ActivationTaskService,
                                           val deactivationTaskService: DeActivationTaskService) extends RelationshipService {
  TaskController.setupExecutor(TaskConfig("create", classOf[ActivationTaskExecutor], 5, RetryUptoCount(10, exponentialBackoff = true)))
  TaskController.setupExecutor(TaskConfig("break", classOf[DeactivationTaskExecutor], 5, RetryUptoCount(10, exponentialBackoff = true)))

  val identifiers: Config = ConfigFactory.load("identifiers.properties")
}

trait RelationshipService extends AuthorisedFunctions {
  val serviceMetrics: ServiceMetrics
  val identifiers: Config
  val activationTaskService: ActivationTaskService
  val deactivationTaskService: DeActivationTaskService

  def createAgentClientRelationship(mandate: Mandate, agentCode: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    if (mandate.subscription.service.name.toUpperCase == AtedService) {
      val serviceId = mandate.subscription.service.id
      val identifier = identifiers.getString(s"${serviceId.toLowerCase()}.identifier")
      val clientId = mandate.subscription.referenceNumber.getOrElse("")

      for {
        (groupId, credId) <- getUserAuthDetails
      } yield {
        val task = Task("create", Map("clientId" -> clientId,
          "agentPartyId" -> mandate.agentParty.id,
          "serviceIdentifier" -> identifier,
          "agentCode" -> agentCode,
          "mandateId" -> mandate.id,
          "credId" -> credId,
          "groupId" -> groupId,
          "token" -> hc.token.get.value,
          "authorization" -> hc.authorization.get.value
        ), ActivationTaskMessage(activationTaskService, serviceMetrics))
        TaskController.execute(task)
      }
    } else {
      throw new BadRequestException("This is only defined for ATED")
    }
  }

  def breakAgentClientRelationship(mandate: Mandate, agentCode: String, userType: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    if (mandate.subscription.service.name.toUpperCase == AtedService) {
      val serviceId = mandate.subscription.service.id
      val identifier = identifiers.getString(s"${serviceId.toLowerCase()}.identifier")
      val clientId = mandate.subscription.referenceNumber.getOrElse("")

      for {
        (groupId, credId) <- getUserAuthDetails
      } yield {
        val task = Task("break", Map("clientId" -> clientId,
          "agentPartyId" -> mandate.agentParty.id,
          "serviceIdentifier" -> identifier,
          "agentCode" -> agentCode,
          "mandateId" -> mandate.id,
          "credId" -> credId,
          "groupId" -> groupId,
          "token" -> hc.token.get.value,
          "authorization" -> hc.authorization.get.value,
          "userType" -> userType
        ), DeActivationTaskMessage(deactivationTaskService, serviceMetrics))
        TaskController.execute(task)
      }
    } else {
      throw new BadRequestException("This is only defined for ATED")
    }
  }

  private def getUserAuthDetails(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[(String, String)] = {
    authorised().retrieve(credentials and groupIdentifier) {
      case Credentials(ggCredId, _) ~ Some(groupId) => Future.successful(MandateUtils.validateGroupId(groupId), ggCredId)
      case _ =>
        Logger.warn("No details found for agent")
        throw new RuntimeException("No details found for the agent!")
    }
  }
}