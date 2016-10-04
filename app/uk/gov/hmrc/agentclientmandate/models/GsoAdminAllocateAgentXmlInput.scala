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

import play.api.libs.json.Json

import scala.xml.Elem


case class Identifier(`type`: String, value: String)

object Identifier {
  implicit val format = Json.format[Identifier]
}

case class GsoAdminAllocateAgentXmlInput(identifiers: List[Identifier], agentCode: String, serviceName: String) {

  val toXml = {
    <GsoAdminAllocateAgentXmlInput xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="urn:GSO-System-Services:external:1.65:GsoAdminAllocateAgentXmlInput">
      <DirectEnrolment>
      <ServiceName>{serviceName}</ServiceName>
      <Identifiers>
        {
          for (identifier <- identifiers) yield getIdentifier(identifier)
        }
      </Identifiers>
      </DirectEnrolment>
      <AgentID>ACME Agency</AgentID>
      <AgentCode>{agentCode}</AgentCode>
    </GsoAdminAllocateAgentXmlInput>
  }

  private def getIdentifier(identifier : Identifier):Elem = {
    <Identifier IdentifierType={identifier.`type`}>{identifier.value}</Identifier>
  }
}
