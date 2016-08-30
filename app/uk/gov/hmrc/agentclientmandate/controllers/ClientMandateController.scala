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

import play.api.libs.json.{JsSuccess, Json}
import uk.gov.hmrc.agentclientmandate.services.{ClientMandateService, FetchClientMandateService}
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import play.api.mvc._

import scala.concurrent.Future

case class PartyDto(id: String, name: String, `type`: String)

object PartyDto {
  implicit val formats = Json.format[PartyDto]
}

case class ContactDetailsDto(email: String, phone: String)

object ContactDetailsDto {
  implicit val formats = Json.format[ContactDetailsDto]
}

case class ClientMandateDto(party: PartyDto, contactDetails: ContactDetailsDto)

object ClientMandateDto {
  implicit val formats = Json.format[ClientMandateDto]
}


object ClientMandateController extends ClientMandateController {
  val clientMandateService = ClientMandateService
  val fetchClientMandateService = FetchClientMandateService
}

case class Resp(id: String)

object Resp {
  implicit val formats = Json.format[Resp]
}

trait ClientMandateController extends BaseController {

  def clientMandateService: ClientMandateService
  def fetchClientMandateService: FetchClientMandateService

  def create = Action.async(parse.json) { implicit request =>
    request.body.asOpt[ClientMandateDto] match {
      case Some(x) =>
        clientMandateService.createMandate(x).map {
          mandateId => Created(Json.toJson(Resp(mandateId)))
        }
      case None => Future.successful(BadRequest)
    }
  }

  def fetch(mandateId: String) = Action.async {
    implicit request =>
      fetchClientMandateService.fetchClientMandate(mandateId).map { clientMandate => Ok(Json.toJson(clientMandate))}
  }

}
