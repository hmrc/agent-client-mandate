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

package uk.gov.hmrc.agentclientmandate.models


import play.api.libs.functional.syntax.functionalCanBuildApplicative
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json._

case class SendEmailRequest(to: List[String], templateId: String, parameters: Map[String, String], force: Boolean)

object SendEmailRequest {
  implicit val format = new Format[SendEmailRequest] {
    // $COVERAGE-OFF$
    def reads(json: JsValue): JsResult[SendEmailRequest] = (
      (__ \ "to").read[List[String]] and
        (__ \ "templateId").read[String] and
        (__ \ "parameters").read[Map[String, String]] and
        (__ \ "force").readNullable[Boolean].map(_.getOrElse(false)))(SendEmailRequest.apply _).reads(json)
    // $COVERAGE-ON$
    def writes(o: SendEmailRequest): JsValue = Json.writes[SendEmailRequest].writes(o)
  }
}
