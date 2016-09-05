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

package uk.gov.hmrc.agentclientmandate.services

import uk.gov.hmrc.agentclientmandate.connectors.EmailConnector
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import play.api.http.Status._
import uk.gov.hmrc.agentclientmandate.models.ValidEmail

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future


  trait NotificationEmailService {

    def emailConnector: EmailConnector

    def validateEmail(emailString: String)(implicit hc: HeaderCarrier): Future[Option[Boolean]] = {
      emailConnector.validateEmailId(emailString) map {
        response => response.status match {
          case OK =>
            val validEmail = response.json.as[ValidEmail]
            Some(validEmail.valid)
          case status => None
        }
      }
    }
  }

  object NotificationEmailService extends NotificationEmailService {
    val emailConnector = EmailConnector
  }
