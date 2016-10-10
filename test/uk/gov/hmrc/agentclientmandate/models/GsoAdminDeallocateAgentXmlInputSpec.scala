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

import org.scalatestplus.play.PlaySpec

class GsoAdminDeallocateAgentXmlInputSpec extends PlaySpec {

  "GsoAdminDeallocateAgentXmlInput" must {

    "convert case class to xml" in {

      val ident1 = Identifier("TYPE1", "value1")
      val ident2 = Identifier("TYPE2", "value2")

      val idents = GsoAdminDeallocateAgentXmlInput(List(ident1, ident2), "ABCDEFGHIJKL", "ATED")

      val identsXml = idents.toXml

      (identsXml \\ "GsoAdminDeallocateAgentXmlInput" \ "ServiceName").text.toString must be("ATED")
      (identsXml \\ "GsoAdminDeallocateAgentXmlInput" \ "AgentCode").text.toString must be("ABCDEFGHIJKL")
      (identsXml \\ "GsoAdminDeallocateAgentXmlInput" \ "Identifiers" \ "Identifier").toList.size must be(2)
      (identsXml \\ "GsoAdminDeallocateAgentXmlInput" \ "Identifiers" \ "Identifier").toList.head.text.toString must be("value1")
      (identsXml \\ "GsoAdminDeallocateAgentXmlInput" \ "Identifiers" \ "Identifier").toList.tail.text.toString must be("value2")
    }

  }

}
