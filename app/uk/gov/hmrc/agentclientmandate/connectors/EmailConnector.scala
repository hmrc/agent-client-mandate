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
import uk.gov.hmrc.agentclientmandate.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

import scala.concurrent.Future

trait EmailConnector extends ServicesConfig{

  def validateEmailUri: String

  def serviceUrl: String

  def http: HttpGet with HttpPost with HttpPut

  def validateEmailId(emailString: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val getUrl = s"""$serviceUrl/$validateEmailUri?email=$emailString"""
    Logger.info(s"[EmailConnector][validateEmailId] - GET ££££££££££££££££££££££ $getUrl")
    http.GET[HttpResponse](getUrl)
  }

}

object EmailConnector extends EmailConnector {
  val validateEmailUri: String = "validate-email-address"
  val serviceUrl = baseUrl("email")
  val http: HttpGet with HttpPost with HttpPut = WSHttp
}