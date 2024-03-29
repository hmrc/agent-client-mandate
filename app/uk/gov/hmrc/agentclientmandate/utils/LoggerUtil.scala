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

import play.api.Logging

// $COVERAGE-OFF$

object LoggerUtil  extends Logging {

def logInfo(content: String): Unit = logger.info(content)
def logInfo(content: String, throwable: Throwable): Unit = logger.info(content, throwable)
def logDebug(content: String): Unit = logger.debug(content)
def logDebug(content: String, throwable: Throwable): Unit = logger.debug(content, throwable)
def logWarn(content: String): Unit = logger.warn(content)
def logWarn(content: String, throwable: Throwable): Unit = logger.warn(content, throwable)
def logError(content: String): Unit = logger.error(content)
def logError(content: String, throwable: Throwable): Unit = logger.error(content, throwable)
}

// $COVERAGE-ON$

