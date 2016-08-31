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

import play.api.libs.json.Json
import uk.gov.hmrc.agentclientmandate.models.ClientMandateDto
import uk.gov.hmrc.agentclientmandate.repositories.{ClientMandateNotFound, ClientMandateFetched}
import uk.gov.hmrc.agentclientmandate.services.{ClientMandateFetchService, ClientMandateCreateService}
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import play.api.mvc._

import scala.concurrent.Future

object ClientMandateController extends ClientMandateController {
  val clientMandateService = ClientMandateCreateService
  val fetchClientMandateService = ClientMandateFetchService
}

case class CreateMandateResponse(mandateId: String)

object CreateMandateResponse {
  implicit val formats = Json.format[CreateMandateResponse]
}

trait ClientMandateController extends BaseController {

  def clientMandateService: ClientMandateCreateService

  def fetchClientMandateService: ClientMandateFetchService

  def create = Action.async(parse.json) { implicit request =>
    request.body.asOpt[ClientMandateDto] match {
      case Some(x) =>
        clientMandateService.createMandate(x).map {
          mandateId => Created(Json.toJson(CreateMandateResponse(mandateId)))
        }
      case None => Future.successful(BadRequest)
    }
  }

  def fetch(mandateId: String) = Action.async { implicit request =>
    fetchClientMandateService.fetchClientMandate(mandateId).map {
      case ClientMandateFetched(x) => Ok(Json.toJson(x))
      case ClientMandateNotFound => NotFound
    }
  }

  def fetchAll(arn: String, serviceName: String) = Action.async { implicit request =>
    fetchClientMandateService.getAllMandates(arn, serviceName).map {
      case mandateList => Ok(Json.toJson(mandateList))
      case _ => NotFound
    }
  }

}
