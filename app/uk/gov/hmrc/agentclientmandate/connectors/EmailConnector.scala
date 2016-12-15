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

package uk.gov.hmrc.agentclientmandate.connectors


import play.api.Logger
import play.api.http.Status.ACCEPTED
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientmandate.config.WSHttp
import uk.gov.hmrc.agentclientmandate.models.SendEmailRequest
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait EmailStatus
case object EmailSent extends EmailStatus
case object EmailNotSent extends EmailStatus

trait EmailConnector extends ServicesConfig with RawResponseReads {

  def sendEmailUri: String

  def serviceUrl: String

  def http: HttpGet with HttpPost with HttpPut

  def sendTemplatedEmail(emailString: String, templateName: String)(implicit hc: HeaderCarrier): Future[EmailStatus] = {

    val params = Map("emailAddress" -> emailString)

    val sendEmailReq = SendEmailRequest(List(emailString), templateName, params, force = true)

    val postUrl = s"$serviceUrl/$sendEmailUri"
    val jsonData = Json.toJson(sendEmailReq)

    http.POST(postUrl, jsonData).map { response =>
      response.status match {
        case ACCEPTED => EmailSent
        case status =>
          Logger.warn(s"[EmailConnector][sendTemplatedEmail] - status: $status Error ${response.body}")
          EmailNotSent
      }
    }
  }


}

object EmailConnector extends EmailConnector {
  // $COVERAGE-OFF$
  val sendEmailUri: String = "send-templated-email"
  val serviceUrl = baseUrl("email")
  val http: HttpGet with HttpPost with HttpPut = WSHttp
  // $COVERAGE-OFF$
}
