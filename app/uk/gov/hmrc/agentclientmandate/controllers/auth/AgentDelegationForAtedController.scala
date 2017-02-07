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

package uk.gov.hmrc.agentclientmandate.controllers.auth

import play.api.mvc.Action
import uk.gov.hmrc.agentclientmandate.services.AgentDetailsService
import uk.gov.hmrc.domain.{AgentCode, AtedUtr}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object AgentDelegationForAtedController extends AgentDelegationForAtedController {
  // $COVERAGE-OFF$
  val agentDetailsService: AgentDetailsService = AgentDetailsService
  // $COVERAGE-ON$
}

//scalastyle:off public.methods.have.type
trait AgentDelegationForAtedController extends BaseController {

  def agentDetailsService: AgentDetailsService

  def isAuthorisedForAted(ac: AgentCode, ated: AtedUtr) = Action.async { implicit request =>
    agentDetailsService.isAuthorisedForAted(ated) map { isAuthorised =>
      if (isAuthorised) Ok
      else Unauthorized
    }
  }

}
