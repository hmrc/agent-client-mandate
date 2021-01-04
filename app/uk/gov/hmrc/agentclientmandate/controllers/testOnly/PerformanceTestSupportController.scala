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

package uk.gov.hmrc.agentclientmandate.controllers.testOnly

import javax.inject.Inject
import play.api.libs.json.JsValue
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.agentclientmandate.models.Mandate
import uk.gov.hmrc.agentclientmandate.repositories._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global

class DefaultPerformanceTestSupportController @Inject()(
                                                         val mandateRepo: MandateRepo,
                                                         val cc: ControllerComponents
                                                       ) extends BackendController(cc) with PerformanceTestSupportController {
  val mandateRepository: MandateRepository = mandateRepo.repository
}

trait PerformanceTestSupportController extends BackendController {
  def mandateRepository: MandateRepository

  def createMandate(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[Mandate] { x =>
      mandateRepository.insertMandate(x).map {
        case MandateCreated(_) => Created
        case MandateCreateError => BadRequest
      }
    }
  }

  def deleteMandate(mandateId: String): Action[AnyContent] = Action.async { _ =>
    mandateRepository.removeMandate(mandateId).map {
      case MandateRemoved => Created
      case MandateRemoveError => BadRequest
    }
  }
}
