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

package uk.gov.hmrc.agentclientmandate.auth

import play.api.mvc.Result
import play.api.mvc.Results.Unauthorized
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.{AgentInformation, Credentials, ~}
import uk.gov.hmrc.auth.core.{AuthorisedFunctions, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.agentclientmandate.utils.LoggerUtil.logError
import play.api.Logging
import scala.concurrent.{ExecutionContext, Future}

case class AuthRetrieval(enrolments: Set[Enrolment],
                         agentInformation: AgentInformation,
                         credentials: Option[Credentials]
                        ) extends Logging {
  def govGatewayId: String = {
    val optionalId = credentials.find(_.providerType == "GovernmentGateway") map { _.providerId }
    optionalId.getOrElse(throw new RuntimeException(s"[AuthRetrieval] No GGCredId found."))
  }
  def userType: String = if(enrolments.exists(_.key == "HMRC-AGENT-AGENT")) "agent" else "client"

  def atedUtr: EnrolmentIdentifier = getEnrolmentId(enrolments.find(_.key == "HMRC-ATED-ORG"), enrolmentId = "ATEDRefNumber")

  def agentBusinessUtr: EnrolmentIdentifier = {
    val enrolment: Option[Enrolment] = enrolments.find(_.key == "HMRC-AGENT-AGENT")
    logger.warn(s"agentBusinessUtr found enrolment $enrolment")
    getEnrolmentId(enrolments.find(_.key == "HMRC-AGENT-AGENT"), enrolmentId = "AgentRefNumber")
  }

  def agentBusinessEnrolment: Enrolment =
    enrolments.find(_.key == "HMRC-AGENT-AGENT").getOrElse(throw new RuntimeException("[AuthRetrieval] No Agent enrolment found"))

  def getEnrolmentId(enrolment: Option[Enrolment], enrolmentId: String): EnrolmentIdentifier = {
    val enrolID = enrolment flatMap (_.identifiers.find(_.key.toLowerCase == enrolmentId.toLowerCase))
    if (enrolmentId == "AgentRefNumber") logger.warn(s" getEnrolmentId: Search for $enrolmentId found $enrolID")

    enrolID.getOrElse(throw new RuntimeException(s"[AuthRetrieval] No enrolment id found for $enrolmentId"))
  }
}


trait AuthFunctionality extends AuthorisedFunctions {

  def authRetrieval(body: AuthRetrieval => Future[Result])
                   (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] = {
    authorised().retrieve(allEnrolments and agentInformation and credentials) {
      case Enrolments(enrolments) ~ agentInfo ~ creds =>
        body(AuthRetrieval(enrolments, agentInfo, creds))
    } recover {
      case er: Exception =>
        logError(s"[authRetrieval] Unexpected auth error - ${er.getMessage} - ${er.getStackTrace.mkString("\n")}")
        Unauthorized
    }
  }
}
