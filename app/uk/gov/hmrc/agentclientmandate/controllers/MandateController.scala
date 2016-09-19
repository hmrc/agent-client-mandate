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

import play.api.mvc.Action
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetched, MandateNotFound}
import uk.gov.hmrc.agentclientmandate.services.{AllocateAgentService, MandateFetchService}
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import scala.concurrent.Future

trait MandateController extends BaseController {

  def fetchService: MandateFetchService
  def allocateAgentService: AllocateAgentService

  def allocate(agentCode: String, mandateId: String) = Action.async { implicit request =>
    fetchService.fetchClientMandate(mandateId).flatMap {
      case MandateFetched(mandate) => {
        allocateAgentService.allocateAgent(mandate, agentCode).map { response =>
          response.status match {
            case OK => Ok
            case BAD_REQUEST => BadRequest
            case INTERNAL_SERVER_ERROR | _ => InternalServerError
          }
        }
      }
      case MandateNotFound => Future.successful(NotFound)
    }
  }
}

object MandateController extends MandateController {
  // $COVERAGE-OFF$
  val fetchService = MandateFetchService
  val allocateAgentService = AllocateAgentService
  // $COVERAGE-ON$
}