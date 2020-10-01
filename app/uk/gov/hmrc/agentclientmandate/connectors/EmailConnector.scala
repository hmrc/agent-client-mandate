/*
 * Copyright 2020 HM Revenue & Customs
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


import javax.inject.Inject
import play.api.http.Status.ACCEPTED
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientmandate.Auditable
import uk.gov.hmrc.agentclientmandate.models.SendEmailRequest
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.agentclientmandate.utils.LoggerUtil.logWarn

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait EmailStatus
case object EmailSent extends EmailStatus
case object EmailNotSent extends EmailStatus

class DefaultEmailConnector @Inject()(val auditConnector: AuditConnector,
                                      val servicesConfig: ServicesConfig,
                                      val http: HttpClient) extends EmailConnector {
  val sendEmailUri: String = "hmrc/email"
  val serviceUrl: String = servicesConfig.baseUrl("email")
}

trait EmailConnector extends RawResponseReads with Auditable {
  def sendEmailUri: String
  def serviceUrl: String
  def http: CorePost

  def sendTemplatedEmail(emailString: String, templateName: String, serviceString: String)(implicit hc: HeaderCarrier): Future[EmailStatus] = {
    val params = Map(
      "emailAddress" -> emailString,
      "service" -> serviceString
    )

    val sendEmailReq = SendEmailRequest(List(emailString), templateName, params, force = true)
    val postUrl = s"$serviceUrl/$sendEmailUri"
    val jsonData = Json.toJson(sendEmailReq)

    http.POST(postUrl, jsonData).map { response =>
      response.status match {
        case ACCEPTED => EmailSent
        case _        =>
          logWarn("email failed")
          doFailedAudit("emailFailed", jsonData.toString, response.body)
          EmailNotSent
      }
    }
  }
}
