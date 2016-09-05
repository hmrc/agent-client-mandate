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

import play.api.Logger
import play.api.mvc.Action
import uk.gov.hmrc.agentclientmandate.models.MessageDetails
import uk.gov.hmrc.agentclientmandate.services.NotificationEmailService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

  trait NotificationEmailController extends BaseController {

    def notificationEmailService: NotificationEmailService

    def x = Action.async(parse.json) {
      implicit request =>
        Logger.info("Entering NotificationEmailController.x to send an email .......")
        withJsonBody[MessageDetails] { msgData =>
          notificationEmailService.validateEmail(msgData.emailId) map {
            case Some(true) => Ok("Valid Email Id!") // send email api call
            case Some(false) => NotFound("Invalid Email Id!")
            case None => NotFound("Invalid Email Id!")
          }
        }
    }
  }
  object NotificationEmailController extends NotificationEmailController {
    val notificationEmailService = NotificationEmailService
  }
