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

package uk.gov.hmrc.tasks

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._
import scala.language.postfixOps

class RetryPolicySpec extends AnyWordSpec with Matchers {

  "RetryUptoCount without exp backoff" must {
    "retry till the max count is reached irrespective of time" in {

      val rCount = RetryUptoCount(5, false)
      rCount.evalRetry(1000, RetryState(1000, 1, 1000)) mustBe RetryNow
      rCount.evalRetry(1000, RetryState(1000, 5, 1000)) mustBe RetryNow
      rCount.evalRetry(1000, RetryState(1000, 6, 1000)) mustBe StopRetrying
    }
  }

  "RetryUptoCount with exp backoff" must {
    "retry at specific time intervals till the max count is reached" in {

      val rCount = RetryUptoCount(4, true)
      val expected = Map (
        1 -> Map( //at 4 secs
          1000 -> DontRetryNow,
          3500 -> DontRetryNow,
          4001 -> RetryNow,
          4500 -> RetryNow
        ),
        2 -> Map( //at 8 secs
          7500 -> DontRetryNow,
          8001 -> RetryNow,
          8500 -> RetryNow
        ),
        3 -> Map( //at 16 secs
          15900 -> DontRetryNow,
          16000 -> RetryNow,
          17000 -> RetryNow
        ),
        4 -> Map( //at 32 secs
          31900 -> DontRetryNow,
          32000 -> RetryNow,
          32100 -> RetryNow
        ),
        5 -> Map(
          100000 -> StopRetrying
        )
      )

      for{
        retryCnt <- expected.keys
        profile = expected (retryCnt).asInstanceOf[Map[Int, RetryEvalResult]]
        timeNow <- profile.keys
        result = profile(timeNow)
      }  rCount.evalRetry(timeNow, RetryState(0, retryCnt, 0)) mustBe result

    }
  }

  "RetryForDuration without exp backoff" must {
    "retry till max period is reached irrespective of retry count" in {

      val rCount = RetryForDuration(10 seconds, false)
      rCount.evalRetry(1000, RetryState(0, 1, 0)) mustBe RetryNow
      rCount.evalRetry(5000, RetryState(0, 20, 1000)) mustBe RetryNow
      rCount.evalRetry(10000, RetryState(0, 100, 5000)) mustBe RetryNow
      rCount.evalRetry(10001, RetryState(0, 1, 10000)) mustBe StopRetrying
      rCount.evalRetry(11000, RetryState(0, 1, 10000)) mustBe StopRetrying
    }

    "retry till max period is reached irrespective of retry count with exp backoff on" in {

      val rCount = RetryForDuration(10 seconds, true)
      rCount.evalRetry(1000, RetryState(0, 1, 0)) mustBe DontRetryNow
      rCount.evalRetry(5000, RetryState(0, 20, 1000)) mustBe DontRetryNow
      rCount.evalRetry(10000, RetryState(0, 100, 5000)) mustBe RetryNow
      rCount.evalRetry(10001, RetryState(0, 1, 10000)) mustBe StopRetrying
      rCount.evalRetry(11000, RetryState(0, 1, 10000)) mustBe StopRetrying
    }
  }

}
