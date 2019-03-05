/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientmandate.connectors


import play.api.Mode.Mode
import play.api.{Configuration, Logger, Play}
import play.api.http.Status.ACCEPTED
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientmandate.Auditable
import uk.gov.hmrc.agentclientmandate.config.WSHttp
import uk.gov.hmrc.agentclientmandate.models.SendEmailRequest
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait EmailStatus
case object EmailSent extends EmailStatus
case object EmailNotSent extends EmailStatus

trait EmailConnector extends ServicesConfig with RawResponseReads with Auditable {

  override protected def mode: Mode = Play.current.mode

  override protected def runModeConfiguration: Configuration = Play.current.configuration

  override protected def appNameConfiguration: Configuration = Play.current.configuration

  def sendEmailUri: String

  def serviceUrl: String

  def http: CorePost

  def sendTemplatedEmail(emailString: String, templateName: String, serviceString: String)(implicit hc: HeaderCarrier): Future[EmailStatus] = {
    val params = Map("emailAddress" -> emailString,
                     "service" -> serviceString)

    val sendEmailReq = SendEmailRequest(List(emailString), templateName, params, force = true)

    val postUrl = s"$serviceUrl/$sendEmailUri"
    val jsonData = Json.toJson(sendEmailReq)

    http.POST(postUrl, jsonData).map { response =>
      response.status match {
        case ACCEPTED => {
          EmailSent
        }
        case status => {
          Logger.warn("email failed")
          doFailedAudit("emailFailed", jsonData.toString, response.body)
          EmailNotSent
        }
      }
    }
  }


}

object EmailConnector extends EmailConnector {
  // $COVERAGE-OFF$
  val sendEmailUri: String = "hmrc/email"
  val serviceUrl = baseUrl("email")
  val http: CorePost = WSHttp
  // $COVERAGE-OFF$
}
