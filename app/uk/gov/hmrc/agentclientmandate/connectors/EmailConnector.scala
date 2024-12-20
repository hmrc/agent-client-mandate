/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.agentclientmandate.utils.LoggerUtil.logWarn
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ExecutionContext, Future}

sealed trait EmailStatus
case object EmailSent extends EmailStatus
case object EmailNotSent extends EmailStatus

class DefaultEmailConnector @Inject()(val auditConnector: AuditConnector,
                                      val servicesConfig: ServicesConfig,
                                      val ec: ExecutionContext,
                                      val http: HttpClientV2) extends EmailConnector {
  val sendEmailUri: String = "hmrc/email"
  val serviceUrl: String = servicesConfig.baseUrl("email")
}

trait EmailConnector extends Auditable {
  implicit val ec: ExecutionContext

  def sendEmailUri: String
  def serviceUrl: String
  def http: HttpClientV2

  def sendTemplatedEmail(emailString: String, templateName: String, serviceString: String,
                         uniqueAuthNo: Option[String], recipientName: String)(implicit hc: HeaderCarrier): Future[EmailStatus] = {

    val defaultParams = Map("emailAddress" -> emailString, "service" -> serviceString, "recipient" -> recipientName)

    val params = templateName match {
        case "agent_removes_mandate" => defaultParams + ("uniqueAuthNo" -> uniqueAuthNo.getOrElse(""))
        case _ => defaultParams
      }

    val sendEmailReq = SendEmailRequest(List(emailString), templateName, params, force = true)
    val postUrl = s"$serviceUrl/$sendEmailUri"
    val jsonData = Json.toJson(sendEmailReq)

    http.post(url"$postUrl").withBody(jsonData).execute[HttpResponse].map{ response =>
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
