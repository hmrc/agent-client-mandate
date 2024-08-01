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

package uk.gov.hmrc.agentclientmandate.connectors

import org.mockito.ArgumentMatchers
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.api.libs.json._
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.HeaderCarrier
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when

import java.net.URL
import uk.gov.hmrc.http.HttpReads

import scala.concurrent.ExecutionContext

trait ConnectorTest extends FutureAwaits with DefaultAwaitTimeout with MockitoSugar {
  val mockHttpClient: HttpClientV2 = mock[HttpClientV2]

  def requestBuilderExecute[A] = {
    val requestBuilder: RequestBuilder = mock[RequestBuilder]
    when(mockHttpClient.post(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)
    requestBuilder.execute[A](any[HttpReads[A]], any[ExecutionContext])
  }

  def executeGet[A] = {
    val mockGetRequestBuilder: RequestBuilder = mock[RequestBuilder]
    when(mockGetRequestBuilder.setHeader(any[(String, String)])).thenReturn(mockGetRequestBuilder)
    when(mockHttpClient.get(any[URL])(any[HeaderCarrier])).thenReturn(mockGetRequestBuilder)
    mockGetRequestBuilder.execute[A](any[HttpReads[A]], any[ExecutionContext])
  }

  def executeDelete[A] = {
    val mockDeleteRequestBuilder: RequestBuilder = mock[RequestBuilder]
    when(mockHttpClient.delete(any[URL])(any[HeaderCarrier])).thenReturn(mockDeleteRequestBuilder)
    mockDeleteRequestBuilder.execute[A](any[HttpReads[A]], any[ExecutionContext])
  }

  def executePost[A](body: JsValue) = {
    val mockPostRequestBuilder: RequestBuilder = mock[RequestBuilder]
    when(mockPostRequestBuilder.setHeader(any[(String, String)])).thenReturn(mockPostRequestBuilder)
    when(mockHttpClient.post(any[URL])(any[HeaderCarrier])).thenReturn(mockPostRequestBuilder)
    when(mockPostRequestBuilder.withBody(ArgumentMatchers.eq(body))(any(), any(), any())).thenReturn(mockPostRequestBuilder)
    mockPostRequestBuilder.execute[A](any[HttpReads[A]], any[ExecutionContext])
  }

}

