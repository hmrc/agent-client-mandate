/*
 * Copyright 2017 HM Revenue & Customs
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
import play.api.Logger
import uk.gov.hmrc.agentclientmandate.models.GGRelationshipDto
import uk.gov.hmrc.agentclientmandate.services.MandateCreateService

import scala.concurrent.ExecutionContext.Implicits.global

class ImportExistingRelationshipsActor extends Actor with ActorUtils {

  self: ImportExistingRelationshipsActorComponent =>

  override def receive: Receive = {
    case request: GGRelationshipDto =>

      val origSender = sender
      // $COVERAGE-OFF$
      createService.createMandateForExistingRelationships(request).map { result =>

        origSender ! result // this result is only used in testing
        // $COVERAGE-ON$
      } recover {
        case e =>
          // $COVERAGE-OFF$
          Logger.warn(s"[ImportExistingRelationshipsActor] - Importing existing relationship failed with error :$e")
          origSender ! akka.actor.Status.Failure(e)
        // $COVERAGE-ON$
      }
    // $COVERAGE-OFF$
    case STOP =>
      sender ! STOP
    case e =>
      Logger.warn(s"[ImportExistingRelationshipsActor] Invalid Message : { message : $e}")
      sender ! akka.actor.Status.Failure(new RuntimeException(s"invalid message: $e"))
  }
  // $COVERAGE-ON$

}

trait ImportExistingRelationshipsActorComponent {
  def createService: MandateCreateService
}

class DefaultImportExistingRelationshipsActor extends ImportExistingRelationshipsActor with DefaultImportExistingRelationshipsActorComponent

trait DefaultImportExistingRelationshipsActorComponent extends ImportExistingRelationshipsActorComponent {
  // $COVERAGE-OFF$
  override val createService = MandateCreateService
  // $COVERAGE-ON$
}

object ImportExistingRelationshipsActor {
  // $COVERAGE-OFF$
  def props: Props = Props(classOf[DefaultImportExistingRelationshipsActor])

  // $COVERAGE-ON$
}
