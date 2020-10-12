/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientmandate.controllers.auth

import javax.inject.Inject
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.agentclientmandate.auth.AuthFunctionality
import uk.gov.hmrc.agentclientmandate.services.AgentDetailsService
import uk.gov.hmrc.agentclientmandate.utils.LoggerUtil.logError
import uk.gov.hmrc.domain.{AgentCode, AtedUtr}
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global

class DefaultAgentDelegationForAtedController @Inject()(
                                                         val agentDetailsService: AgentDetailsService,
                                                         val cc: ControllerComponents,
                                                         val authConnector: DefaultAuthConnector
                                                       ) extends BackendController(cc) with AgentDelegationForAtedController

trait AgentDelegationForAtedController extends BackendController with AuthFunctionality {
  def agentDetailsService: AgentDetailsService

  def isAuthorisedForAted(ac: AgentCode, ated: AtedUtr): Action[AnyContent] = Action.async { implicit request =>
    authRetrieval{ implicit ar =>
      agentDetailsService.isAuthorisedForAted(ated) map { isAuthorised =>
        if (isAuthorised) Ok
        else Unauthorized
      } recover {
        case e: RuntimeException =>
          logError(s"[AgentDelegationForAtedController] Authorisation Error - $e - ${e.getMessage} - ${e.getStackTrace.mkString("\n")}")
          NotFound
      }
    }
  }

  def isAuthorisedForAtedNew(ated: AtedUtr): Action[AnyContent] = Action.async { implicit request =>
    authRetrieval{ implicit ar =>
      agentDetailsService.isAuthorisedForAted(ated) map { isAuthorised =>
        if (isAuthorised) Ok
        else Unauthorized
      } recover {
        case e: RuntimeException =>
          logError(s"[AgentDelegationForAtedController] Authorisation Error - $e - ${e.getMessage} - ${e.getStackTrace.mkString("\n")}")
          NotFound
      }
    }
  }
}
