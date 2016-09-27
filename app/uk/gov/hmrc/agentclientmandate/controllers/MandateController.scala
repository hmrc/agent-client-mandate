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
import play.api.mvc.Action
import uk.gov.hmrc.agentclientmandate.models.{CreateMandateDto, Mandate}
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetched, MandateNotFound, MandateUpdateError, MandateUpdated}
import uk.gov.hmrc.agentclientmandate.services.{RelationshipService, MandateCreateService, MandateFetchService, MandateUpdateService}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//scalastyle:off public.methods.have.type
trait MandateController extends BaseController {

  def createService: MandateCreateService

  def relationshipService: RelationshipService

  def fetchService: MandateFetchService

  def updateService: MandateUpdateService

  def create(agentCode: String) = Action.async(parse.json) { implicit request =>
    request.body.asOpt[CreateMandateDto] match {
      case Some(x) =>
        createService.createMandate(agentCode, x).map { mandateId =>
          Created(Json.parse(s"""{"mandateId": "$mandateId"}"""))
        }
      case None => Future.successful(BadRequest)
    }
  }


  def fetch(authId: String, mandateId: String) = Action.async { implicit request =>
    fetchService.fetchClientMandate(mandateId).map {
      case MandateFetched(x) => Ok(Json.toJson(x))
      case MandateNotFound => NotFound
    }
  }

  def fetchAll(agentCode: String, arn: String, serviceName: String) = Action.async { implicit request =>
    fetchService.getAllMandates(arn, serviceName).map {
      case Nil => NotFound
      case mandateList => Ok(Json.toJson(mandateList))
    }
  }

  def approve(org: String) = Action.async(parse.json) { implicit request =>
    request.body.asOpt[Mandate] match {
      case Some(newMandate) =>
        updateService.updateMandate(newMandate) map {
          case MandateUpdated(y) => Ok(Json.toJson(y))
          case MandateUpdateError => InternalServerError
        }
      case None => Future.successful(BadRequest)
    }
  }

  def activate(agentCode: String, mandateId: String) = Action.async { implicit request =>
    fetchService.fetchClientMandate(mandateId).flatMap {
      case MandateFetched(mandate) =>
        relationshipService.maintainRelationship(mandate, agentCode).map { response =>
          response.status match {
            case OK => Ok
            case BAD_REQUEST => BadRequest
            case INTERNAL_SERVER_ERROR | _ => InternalServerError
          }
        }
      case MandateNotFound => Future.successful(NotFound)
    }
  }

}

object MandateAgentController extends MandateController {
  // $COVERAGE-OFF$
  val createService = MandateCreateService
  val fetchService = MandateFetchService
  val updateService = MandateUpdateService
  val relationshipService = RelationshipService
  // $COVERAGE-ON$
}

object MandateClientController extends MandateController {
  // $COVERAGE-OFF$
  val createService = MandateCreateService
  val fetchService = MandateFetchService
  val updateService = MandateUpdateService
  val relationshipService = RelationshipService
  // $COVERAGE-ON$
}
