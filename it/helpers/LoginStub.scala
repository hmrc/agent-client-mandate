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

package helpers

import java.util.UUID

import play.api.Application
import play.api.mvc.{DefaultCookieHeaderEncoding, DefaultSessionCookieBaker}
import uk.gov.hmrc.auth.core.retrieve.{LegacyCredentials, SimpleRetrieval}
import uk.gov.hmrc.http.SessionKeys

trait LoginStub {

  val app: Application
  lazy val signerSession: DefaultSessionCookieBaker = app.injector.instanceOf[DefaultSessionCookieBaker]
  lazy val cookieHeader: DefaultCookieHeaderEncoding = app.injector.instanceOf[DefaultCookieHeaderEncoding]

  val SessionId = s"stubbed-${UUID.randomUUID}"

  private def cookieData(additionalData: Map[String, String], timeStampRollback: Long): Map[String, String] = {
    val timeStamp = new java.util.Date().getTime
    val rollbackTimestamp = (timeStamp - timeStampRollback).toString

    Map(
      SessionKeys.sessionId -> SessionId,
      SimpleRetrieval("authProviderId", LegacyCredentials.reads).toString -> "GGW",
      SessionKeys.lastRequestTimestamp -> rollbackTimestamp
    ) ++ additionalData
  }

  def getSessionCookie(additionalData: Map[String, String] = Map(), timeStampRollback: Long = 0): String = {
    val cookie = signerSession.encodeAsCookie(signerSession.deserialize(cookieData(additionalData, timeStampRollback)))
    val encodedCookie = cookieHeader.encodeSetCookieHeader(Seq(cookie))

    encodedCookie

  }
}
