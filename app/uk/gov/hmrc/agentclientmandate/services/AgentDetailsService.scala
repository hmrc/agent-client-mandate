/*
 * Copyright 2018 HM Revenue & Customs
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

import org.joda.time.LocalDate
import play.api.libs.json.JsValue
import uk.gov.hmrc.agentclientmandate.connectors.{AuthConnector, EtmpConnector}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.domain.AtedUtr

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier


trait AgentDetailsService {

  def etmpConnector: EtmpConnector

  def authConnector: AuthConnector

  def mandateFetchService: MandateFetchService


  def getAgentDetails(agentCode: String)(implicit hc: HeaderCarrier): Future[AgentDetails] = {
    authConnector.getAuthority().flatMap { authority =>

      val agentPartyId = (authority \ "accounts" \ "agent" \ "agentBusinessUtr").as[String]

      etmpConnector.getRegistrationDetails(agentPartyId, "arn").map { etmpDetails =>
        val isAnIndividual = (etmpDetails \ "isAnIndividual").as[Boolean]
        val safeId = (etmpDetails \ "safeId").as[String]
        val addressLine1 = (etmpDetails \ "addressDetails" \ "addressLine1").as[String]
        val addressLine2 = (etmpDetails \ "addressDetails" \ "addressLine2").as[String]
        val addressLine3 = (etmpDetails \ "addressDetails" \ "addressLine3").asOpt[String]
        val addressLine4 = (etmpDetails \ "addressDetails" \ "addressLine4").asOpt[String]
        val postalCode = (etmpDetails \ "addressDetails" \ "postalCode").asOpt[String]
        val countryCode = (etmpDetails \ "addressDetails" \ "countryCode").as[String]

        val phoneNumber = (etmpDetails \ "contactDetails" \ "phoneNumber").asOpt[String]
        val mobileNumber = (etmpDetails \ "contactDetails" \ "mobileNumber").asOpt[String]
        val faxNumber = (etmpDetails \ "contactDetails" \ "faxNumber").asOpt[String]
        val emailAddress = (etmpDetails \ "contactDetails" \ "emailAddress").asOpt[String]


        val nonUKId = (etmpDetails \ "nonUKIdentification").asOpt[Identification]

        if (isAnIndividual) {
          val fname = (etmpDetails \ "individual" \ "firstName").as[String]
          val lname = (etmpDetails \ "individual" \ "lastName").as[String]
          val dob = (etmpDetails \ "individual" \ "dateOfBirth").as[LocalDate]

          AgentDetails(safeId,
            true,
            individual = Some(Individual(fname, None, lname, dob)),
            None,
            addressDetails = RegisteredAddressDetails(addressLine1, addressLine2, addressLine3, addressLine4, postalCode, countryCode),
            contactDetails = EtmpContactDetails(phoneNumber, mobileNumber, faxNumber, emailAddress),
            identification = nonUKId)

        } else {
          val orgName = (etmpDetails \ "organisation" \ "organisationName").as[String]
          val isAGroup = (etmpDetails \ "organisation" \ "isAGroup").asOpt[Boolean]
          val orgType = (etmpDetails \ "organisation" \ "organisationName").asOpt[String]

          AgentDetails(safeId,
            false,
            None,
            organisation = Some(Organisation(orgName, isAGroup, orgType)),
            addressDetails = RegisteredAddressDetails(addressLine1, addressLine2, addressLine3, addressLine4, postalCode, countryCode),
            contactDetails = EtmpContactDetails(phoneNumber, mobileNumber, faxNumber, emailAddress),
            identification = nonUKId)
        }

      }
    }
  }


  def isAuthorisedForAted(ated: AtedUtr)(implicit hc: HeaderCarrier): Future[Boolean] = {
    authConnector.getAuthority().flatMap { authority =>
      val agentRefNumberOpt = (authority \ "accounts" \ "agent" \ "agentBusinessUtr").asOpt[String]
      agentRefNumberOpt match {
        case Some(arn) =>
          mandateFetchService.getAllMandates(arn, "ated", None, None).map(_.find(_.subscription.referenceNumber.fold(false)(a => a == ated.utr)).fold(false)(a => true))
        case None => Future.successful(false)
      }
    }
  }
}

object AgentDetailsService extends AgentDetailsService {
  // $COVERAGE-OFF$
  val authConnector = AuthConnector
  val etmpConnector = EtmpConnector
  val mandateFetchService: MandateFetchService = MandateFetchService
  // $COVERAGE-ON$
}
