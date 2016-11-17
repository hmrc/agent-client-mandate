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

package uk.gov.hmrc.agentclientmandate.controllers.testOnly

import play.api.Logger
import play.api.mvc.Action
import uk.gov.hmrc.agentclientmandate.models.Mandate
import uk.gov.hmrc.agentclientmandate.repositories.{MandateCreateError, MandateCreated, MandateRepository}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

//scalastyle:off public.methods.have.type
trait PerformanceTestSupportController extends BaseController {

  def mandateRepository: MandateRepository

  def createMandate() = Action.async(parse.json) { implicit request =>
    withJsonBody[Mandate] { x =>
      Logger.debug("inserting test mandate")
      mandateRepository.insertMandate(x).map { mandateStatus =>
        mandateStatus match {
          case MandateCreated(mandate) =>
            Logger.debug("inserted test mandate")
            Created
          case MandateCreateError =>
            BadRequest
        }
      }
    }
  }
}


object PerformanceTestSupportController extends PerformanceTestSupportController {
  // $COVERAGE-OFF$
  val mandateRepository = MandateRepository()
  // $COVERAGE-ON$
}
