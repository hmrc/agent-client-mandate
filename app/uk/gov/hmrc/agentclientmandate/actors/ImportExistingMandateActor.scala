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

package uk.gov.hmrc.agentclientmandate.actors

import akka.actor.{Actor, Props}
import uk.gov.hmrc.agentclientmandate.models.ExistingMandateDto
import uk.gov.hmrc.agentclientmandate.services.MandateCreateService
import play.api.Logger
import scala.concurrent.ExecutionContext.Implicits.global

class ImportExistingMandateActor extends Actor {

  self: ImportExistingMandateActorComponent =>

  override def receive: Receive = {
    case request: ExistingMandateDto => {

      val origSender = sender

      Logger.debug("Importing relationship for agent- " + request.agentPartyId + ", client- " + request.clientSubscriptionId)
      createService.createMandateForExistingRelationships(request).map { result =>

        Logger.debug("[ImportExistingMandateActor] - Importing result: " + result)
        origSender ! result // this result is only used in testing

      }.recover {
        case e =>
          // $COVERAGE-OFF$
          Logger.error(s"[ImportExistingMandateActor] - Importing existing relationship failed with error :$e")
          origSender ! akka.actor.Status.Failure(e)
        // $COVERAGE-ON$
      }
    }
  }

}

trait ImportExistingMandateActorComponent {
  def createService: MandateCreateService
}

class DefaultImportExistingMandateActor extends ImportExistingMandateActor with DefaultImportExistingMandateActorComponent

trait DefaultImportExistingMandateActorComponent extends ImportExistingMandateActorComponent {
  // $COVERAGE-OFF$
  override val createService = MandateCreateService
  // $COVERAGE-ON$
}

object ImportExistingMandateActor {
  // $COVERAGE-OFF$
  def props = Props(classOf[DefaultImportExistingMandateActor])
  // $COVERAGE-ON$
}
