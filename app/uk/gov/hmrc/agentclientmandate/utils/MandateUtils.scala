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

package uk.gov.hmrc.agentclientmandate.utils

import javax.xml.parsers.SAXParserFactory
import uk.gov.hmrc.agentclientmandate.models.{EtmpAtedAgentClientRelationship, EtmpRelationship, Mandate, Status}
import uk.gov.hmrc.http.HttpResponse

object MandateUtils {

  def createRelationship(clientId: String, agentId: String) = {
    EtmpAtedAgentClientRelationship(SessionUtils.getUniqueAckNo, clientId, agentId, EtmpRelationship(action = "Authorise", isExclusiveAgent = Some(true)))
  }

  def breakRelationship(clientId: String, agentId: String) = {
    EtmpAtedAgentClientRelationship(SessionUtils.getUniqueAckNo, clientId, agentId, EtmpRelationship(action = "De-Authorise", isExclusiveAgent = None))
  }

  def whetherSelfAuthorised(m: Mandate): Boolean = !m.statusHistory.exists(_.status == Status.Approved) //does not have a status approved

  def parseErrorResp(resp: HttpResponse): String = {
    val msgToXml = scala.xml.XML.withSAXParser(secureSAXParser).loadString(resp.body)
    (msgToXml \\ "ErrorNumber").text
  }

  def validateGroupId(str: String) = if(str.trim.length != 36) {
    if(str.contains("testGroupId-")) str.replace("testGroupId-", "")
    else throw new RuntimeException("Invalid groupId from auth")
  } else str.trim

def secureSAXParser = {
    val saxParserFactory = SAXParserFactory.newInstance()
    saxParserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    saxParserFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    saxParserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    saxParserFactory.newSAXParser()
  }
}
