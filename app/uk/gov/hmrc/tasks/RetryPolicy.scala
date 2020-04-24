/*
 * Copyright 2020 HM Revenue & Customs
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

import scala.concurrent.duration.FiniteDuration

/**
  * Created by Nilanjan Biswas on 10/12/2016.
  *
  * Represents the retry settings specified by the client
  * Has 2 concrete subtypes -
  *  A) RetryUptoCount - Retries until a maximum count has been reached
  *  B) RetryForDuration - Retries for a specific length of time
  * In either case you can set the 'exponentialBackoff' to true, in
  * which case there is an exponentially growing amount of wait
  * time between each retry, till it reaches 1 hr, from which point it
  * remains constant.
  */
trait RetryPolicy {
  def evalRetry(now:Long, state:RetryState):RetryEvalResult

  /*
  This is the pattern of delays for the given exp backoff formula
   retry 1 -> 4s
   retry 2 -> 8s
   retry 3 -> 16s
   retry 4 -> 32s
   retry 5 -> 1m 4s
   retry 6 -> 2m 8s
   retry 7 -> 4m 16s
   retry 8 -> 8m 32s
   retry 9 -> 17m 4s
   retry 10 -> 34m 8s
   retry 11 -> 1h
   retry 12 onwards -> every 1h
  */

  // Called only when exponential backoff is true.
  // Given the RetryState of a TaskCommand, evaluate if this task command needs
  // to be retried now OR if it shouldn't be retried anymore because retry limit
  // has been exceeded
  protected def evalExpBackoffRetry(now:Long, state:RetryState): RetryEvalResult = {

    val boLengthMillis = Math.pow(2.0, state.retryCount.toDouble + 2.0).toInt * 500
    val delayInMillis = Math.min(boLengthMillis, 3600000) //max retry delay = 1 hr

    // Current time is past when next retry should have happened
    if (now >= state.lastTryAt + delayInMillis) RetryNow
    else DontRetryNow
  }

}

/**
  * Retry policy that allows retrying upto a maximum count, with ot without exponential backoff
  * @param maxCount The maximum number of times to retry
  * @param exponentialBackoff If wait time between retries should grow exponentially
  */
case class RetryUptoCount(maxCount:Integer, exponentialBackoff:Boolean) extends RetryPolicy {

  override def evalRetry(now:Long, state:RetryState): RetryEvalResult = {
    if(state.retryCount > maxCount ) StopRetrying
    else if (exponentialBackoff) evalExpBackoffRetry(now, state)
    else RetryNow
  }
}

/**
  * Retry policy that allows retrying upto a maximum time period, with ot without exponential backoff
  * @param maxDuration The maximum period of time to keep retrying for
  * @param exponentialBackoff If wait time between retries should grow exponentially
  */
case class RetryForDuration(maxDuration:FiniteDuration, exponentialBackoff:Boolean) extends RetryPolicy {
  override def evalRetry(now:Long, state:RetryState): RetryEvalResult = {

    // Current time is past max duration since first try
    if(now > state.firstTryAt + maxDuration.toMillis) StopRetrying
    else if (exponentialBackoff) evalExpBackoffRetry(now, state)
    else RetryNow
  }
}

/**
  * Represents the different results of evaluating a RetryPolicy against a TaskCommand
  */
sealed trait RetryEvalResult
case object RetryNow extends RetryEvalResult  //retry now by calling the Executor
case object DontRetryNow extends RetryEvalResult //don't retry now, evaluate again at the next tick
case object StopRetrying extends RetryEvalResult //max retries exceeded, stop retrying and mark as failed
