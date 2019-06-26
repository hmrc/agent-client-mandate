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

package uk.gov.hmrc.agentclientmandate.models
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

import org.joda.time.LocalDate
import play.api.libs.json.Json

case class Individual(firstName: String,
                      middleName: Option[String] = None,
                      lastName: String,
                      dateOfBirth: LocalDate)

object Individual {
  implicit val formats = Json.format[Individual]
}


case class Organisation(organisationName: String,
                        isAGroup: Option[Boolean] = None,
                        organisationType: Option[String] = None)

object Organisation {
  implicit val formats = Json.format[Organisation]
}


case class EtmpContactDetails(phoneNumber: Option[String] = None,
                          mobileNumber: Option[String] = None,
                          faxNumber: Option[String] = None,
                          emailAddress: Option[String] = None)
object EtmpContactDetails {
  implicit val formats = Json.format[EtmpContactDetails]
}

case class Identification(idNumber: String, issuingInstitution: String, issuingCountryCode: String)

object Identification {
  implicit val formats = Json.format[Identification]
}

case class RegisteredAddressDetails(addressLine1: String,
                                    addressLine2: String,
                                    addressLine3: Option[String]=None,
                                    addressLine4: Option[String]=None,
                                    postalCode: Option[String]=None,
                                    countryCode: String)
object RegisteredAddressDetails {
  implicit val formats = Json.format[RegisteredAddressDetails]
}

case class AgentDetails(safeId: String,
                        isAnIndividual: Boolean,
                        individual: Option[Individual],
                        organisation: Option[Organisation],
                        addressDetails: RegisteredAddressDetails,
                        contactDetails: EtmpContactDetails,
                        identification: Option[Identification])

object AgentDetails {
  implicit val formats = Json.format[AgentDetails]
}
