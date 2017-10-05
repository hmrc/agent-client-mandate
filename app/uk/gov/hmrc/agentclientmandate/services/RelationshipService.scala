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

package uk.gov.hmrc.agentclientmandate.services

import play.api.http.Status._
import uk.gov.hmrc.agentclientmandate.config.ApplicationConfig._
import uk.gov.hmrc.agentclientmandate.connectors.{AuthConnector, EtmpConnector, GovernmentGatewayProxyConnector}
import uk.gov.hmrc.agentclientmandate.metrics.Metrics
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.utils.MandateConstants._
import uk.gov.hmrc.tasks._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import akka.actor.ActorSystem
import uk.gov.hmrc.agentclientmandate.tasks.{ActivationTaskExecutor, DeActivationTaskExecutor}
import uk.gov.hmrc.http.{ BadRequestException, HeaderCarrier }
// $COVERAGE-OFF$
trait RelationshipService {

  def authConnector: AuthConnector

  def metrics: Metrics

  def createAgentClientRelationship(mandate: Mandate, agentCode: String)(implicit hc: HeaderCarrier): Unit = {

    if (mandate.subscription.service.name.toUpperCase == AtedService) {
      val serviceId = mandate.subscription.service.id
      val identifier = identifiers.getString(s"${serviceId.toLowerCase()}.identifier")
      val clientId = mandate.subscription.referenceNumber.getOrElse("")
      val credId = getCredId()

      for {
        updatedBy <- credId
      } yield {
        //Then do this each time a 'create' needs to be done
        val task = Task("create", Map("clientId" -> clientId,
          "agentPartyId" -> mandate.agentParty.id,
          "serviceIdentifier" -> identifier,
          "agentCode" -> agentCode,
          "mandateId" -> mandate.id,
          "credId" -> updatedBy))
        //execute asynchronously
        TaskController.execute(task)
      }
    } else {
      throw new BadRequestException("This is only defined for ATED")
    }
  }

  def breakAgentClientRelationship(mandate: Mandate, agentCode: String, userType: String)(implicit hc: HeaderCarrier): Unit = {

    if (mandate.subscription.service.name.toUpperCase == AtedService) {
      val serviceId = mandate.subscription.service.id
      val identifier = identifiers.getString(s"${serviceId.toLowerCase()}.identifier")
      val clientId = mandate.subscription.referenceNumber.getOrElse("")
      val credId = getCredId()

      for {
        updatedBy <- credId
      } yield {
        //Then do this each time a 'break' needs to be done
        val task = Task("break", Map("clientId" -> clientId,
          "agentPartyId" -> mandate.agentParty.id,
          "serviceIdentifier" -> identifier,
          "agentCode" -> agentCode,
          "mandateId" -> mandate.id,
          "credId" -> updatedBy,
          "userType" -> userType))
        //execute asynchronously
        TaskController.execute(task)
      }
    } else {
      throw new BadRequestException("This is only defined for ATED")
    }
  }

  private def getCredId()(implicit hc: HeaderCarrier): Future[String] = {
      authConnector.getAuthority() map { authority =>
      (authority \ "credentials" \ "gatewayId").as[String]
    }
  }

}

object RelationshipService extends RelationshipService {
 
  val authConnector: AuthConnector = AuthConnector
  //val taskController: TaskControllerT = TaskController

  val metrics = Metrics

  TaskController.setupExecutor(TaskConfig("create", classOf[ActivationTaskExecutor], 5, RetryUptoCount(10, true)))
  TaskController.setupExecutor(TaskConfig("break", classOf[DeActivationTaskExecutor], 5, RetryUptoCount(10, true)))

}
// $COVERAGE-ON$