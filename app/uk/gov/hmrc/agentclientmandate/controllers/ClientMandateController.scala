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
import play.api.mvc._
import uk.gov.hmrc.agentclientmandate.models.{CreateMandateResponse, MandateDto, MandateUpdatedDto}
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetched, MandateNotFound, MandateUpdated}
import uk.gov.hmrc.agentclientmandate.services._
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future


trait ClientMandateController extends BaseController {

  def createService: MandateCreateService

  def fetchService: MandateFetchService

  def updateService: MandateUpdateService

  def create = Action.async(parse.json) { implicit request =>
    request.body.asOpt[MandateDto] match {
      case Some(x) =>
        createService.createMandate(x).map { mandateId =>
          Created(Json.toJson(CreateMandateResponse(mandateId)))
        }
      case None => Future.successful(BadRequest)
    }
  }

  def fetch(mandateId: String) = Action.async { implicit request =>
    fetchService.fetchClientMandate(mandateId).map {
      case MandateFetched(x) => Ok(Json.toJson(x))
      case MandateNotFound => NotFound
    }
  }

  def fetchAll(arn: String, serviceName: String) = Action.async { implicit request =>
    fetchService.getAllMandates(arn, serviceName).map {
      case Nil => NotFound
      case mandateList => Ok(Json.toJson(mandateList))
    }
  }

  def update = Action.async(parse.json) { implicit request =>
    request.body.asOpt[MandateUpdatedDto] match {
      case Some(newMandate) =>
        fetchService.fetchClientMandate(newMandate.mandateId).flatMap {
          case MandateFetched(mandate) => updateService.updateMandate(mandate, newMandate) map {
            case MandateUpdated(y) => Ok(Json.toJson(y))
          }
          case MandateNotFound => Future.successful(NotFound)
        }
      case None => Future.successful(BadRequest)
    }
  }
}


object ClientMandateController extends ClientMandateController {
  val createService = MandateCreateService
  val fetchService = MandateFetchService
  val updateService = MandateUpdateService
}
