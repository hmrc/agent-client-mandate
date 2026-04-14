/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientmandate.utils

import play.api.Logger
import play.api.libs.json.{JsLookupResult, JsObject, JsString, JsValue, Json}

import scala.util.Try

object HipUtilities {

  lazy val logger: Logger = Logger(this.getClass)

  val Success = "success"
  val AcknowledgementReference = "acknowledgementReference"
  val AcknowledgmentReference = "acknowledgmentReference"
  val ErrorsNode: String = "errors"
  val CodeNode: String = "code"
  val TextNode: String = "text"

  def extractHipErrorCode(jsonErrorMessageWithCode: String): Option[(String, String)] = {
    for {
      errorMessageBody <- Try(Json.parse(jsonErrorMessageWithCode)).toOption
      errorCodeNode    <- (errorMessageBody \ ErrorsNode \ CodeNode).toOption
      errorTextNode <- (errorMessageBody \ ErrorsNode \ TextNode).toOption
    } yield {
      (errorCodeNode.asInstanceOf[JsString].value, errorTextNode.asInstanceOf[JsString].value)
    }
  }

  def removeAcknowledgementReferenceField(hipRequestPayload: JsValue): JsObject = {
    if ((hipRequestPayload \ AcknowledgementReference).isDefined || (hipRequestPayload \ AcknowledgmentReference).isDefined ) {
      hipRequestPayload.as[JsObject] - AcknowledgementReference - AcknowledgmentReference
    } else {
      logger.warn(s"Request does not contain a '$AcknowledgementReference node.")
      hipRequestPayload.as[JsObject]
    }
  }

  def stripSuccessWrapper(hipResponsePayload: JsValue): JsObject = {
    val successNodeLookup: JsLookupResult = hipResponsePayload \ Success
    if (successNodeLookup.isDefined) {
      successNodeLookup.as[JsObject]
    } else {
      logger.warn(s"Received response does not contain a '$Success' node.")
      hipResponsePayload.as[JsObject]
    }
  }

}

