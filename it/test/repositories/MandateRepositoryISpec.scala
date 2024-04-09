/*
 * Copyright 2024 HM Revenue & Customs
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

package repositories

import helpers.IntegrationSpec
import java.time.{LocalDateTime, ZoneOffset, Instant}
import org.scalatest.Assertion
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import uk.gov.hmrc.agentclientmandate.models
import uk.gov.hmrc.agentclientmandate.models.Status
import uk.gov.hmrc.agentclientmandate.models.Status._
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


class MandateRepositoryISpec extends IntegrationSpec {

  val repo = mandateRepo.repository
  val when = LocalDateTime.of(2023, 8, 24, 10, 55).toInstant(ZoneOffset.UTC)
  val laterThanWhen = LocalDateTime.of(2023, 8, 24, 19, 55).toInstant(ZoneOffset.UTC)
  val serviceName: String = "ated"
  val serviceId: String = "ATED"
  val subscription = Subscription(None, Service(serviceId, serviceName))
  val agentEmail: String = "agent@notrealemail.fake"
  val clientEmail: String = "client@notrealemail.fake"
  val agentIds: List[String] = Range(0,20).map(id => s"FAKE-UTR$id").toList
  val clientIds: List[String] = Range(0,20).map(id => s"Fake_id$id").toList
  val mandateIds: List[String] = Range(0,20).map(id => s"Mandate_id$id").toList
  
  override protected def afterAll(): Unit = {
    super.afterAll()
    await(mandateRepo.repository.collection.drop().toFuture())
    stopWmServer()    
  }

  def createMandate(id: String, 
                    userId: String, 
                    servName: String, 
                    agentId: String, 
                    clientId: String,
                    ts: Instant, 
                    status: Status.Status, 
                    agentEmail: String = "agent@notrealemail.fake",
                    clientEmail: String = "client@notrealemail.fake"): Mandate =
    Mandate(
      id, 
      models.User(userId, "First Last", Some("FAKE-AB123456")), 
      None, 
      None,
      Party(agentId, "First Last", PartyType.Individual, ContactDetails(agentEmail)),
      Some(Party(clientId, "First Last", PartyType.Individual, ContactDetails(clientEmail, Some("No")))),
      MandateStatus(status, ts, "cred-id-113244018119"), 
      List(), 
      Subscription(None, Service(serviceId, servName)), 
      "display-name"
    )

  def createMandates(mandates: List[Mandate]): Future[Int] =
    Future.sequence(
      mandates.map{ mandate =>
        mandateRepo.repository.insertMandate(mandate).map{
          case MandateCreated(_) => 1
          case _ => 0
        }.recover{case x => 0}
      }
    ).map(_.sum)

  def createMandatesAndWait(mandates: List[Mandate])(fn: => Assertion): Assertion =
    await(createMandates(mandates)) match {
      case count if count == mandates.length => fn
      case _ => fail()
    }

  "MandateRepository" should {

    "Insert and retrieve a mandate" in {
      val mandate = createMandate(mandateIds(0), "cred-id-113244018119", serviceName, agentIds(1), clientIds(0), when, New)
      await(mandateRepo.repository.insertMandate(mandate)) match {
        case mandateCreate: MandateCreated =>
          await(mandateRepo.repository.fetchMandate(mandate.id)) match {
            case MandateFetched(fetched) if fetched ==mandate => succeed
            case err => fail(s"ERRR: $err")
          }
        case err => fail(err.toString)
      }
    }

    "Insert, Update and retrieve a mandate" in {
      val mandate = createMandate(mandateIds(0), "cred-id-113244018119", serviceName, agentIds(1), clientIds(0), when, New)
      await(mandateRepo.repository.insertMandate(mandate)) match {
        case mandateCreate: MandateCreated =>
          val updatedMandate = mandate.copy(currentStatus = mandate.currentStatus.copy(timestamp = laterThanWhen))
          await(mandateRepo.repository.updateMandate(updatedMandate)) match {
            case MandateUpdated(update) => await(mandateRepo.repository.fetchMandate(update.id)) match {
              case MandateFetched(fetched) if fetched == update => succeed
              case _ => fail()
            }
            case _ => fail()
          }
          
        case _ => fail()
      }
    }
 
    "Multiple Inserts and fetchMandateByClient" in {
      val mandates = Range(0,12).map(idx => createMandate(mandateIds(idx), "cred-id-113244018119", serviceName, agentIds(1), clientIds(idx), when, Active)).toList

      createMandatesAndWait(mandates){
        await(mandateRepo.repository.fetchMandateByClient(clientIds(3), serviceName)) match {
          case MandateFetched(fetched) if fetched.clientParty.fold(false)(cp => cp.id == clientIds(3)) => succeed
          case err => fail(err.toString())
        }        
      }
    }

    "Multiple Inserts and getAllMandatesByServiceName" in {
      val mandates = 
        createMandate(mandateIds(0), "cred-id-113244018120", "other", agentIds(1), clientIds(0), when, Active) ::
       (createMandate(mandateIds(1), "cred-id-113244018121", "other", agentIds(1), clientIds(1), when, Active) ::
       (createMandate(mandateIds(2), "cred-id-113244018122", "other", agentIds(1), clientIds(2), when, Active) :: 
        Range(3,5).map(idx => createMandate(mandateIds(idx), "cred-id-113244018119", "other", agentIds(1), clientIds(idx), when, Active)).toList)) ++
        // mandates 5 to 9 will have a Subscription to the "ated" service"
        Range(5,10).map(idx => createMandate(mandateIds(idx), "cred-id-113244018119", serviceName, agentIds(1), clientIds(idx), when, Active)).toList
                     
      createMandatesAndWait(mandates){
        await(mandateRepo.repository.getAllMandatesByServiceName(agentIds(1), "other", None, None, None)) match {
          case fetched if fetched.length == 5  => 
            await(mandateRepo.repository.getAllMandatesByServiceName(agentIds(1), "other", Some("cred-id-113244018120"), Some("cred-id-113244018121"), None)) match {
              case fetched if fetched.length == 2  => succeed            
              case fetched  => fail(s"ERROR: returned ${fetched.length} mandates")
            }
          case fetched  => fail(s"ERROR: returned ${fetched.length} mandates")
        }
      }
    }

    "Multiple Insert and findMandatesMissingAgentEmail" in {
      val mandates = 
        createMandate("missimgemailmandate", "cred-id-113244018133", serviceName, agentIds(1), "clientId99", when, Active, "") ::
        Range(0,12).map(idx => createMandate(mandateIds(idx), "cred-id-113244018119", serviceName, agentIds(1), clientIds(idx), when, Active)).toList

      createMandatesAndWait(mandates){
        await(mandateRepo.repository.findMandatesMissingAgentEmail(agentIds(1), serviceName)) match {
          case fetched if fetched.length == 1  => succeed
          case fetched  => fail(s"ERROR: returned ${fetched.length} mandates")
        }
      }
    }

    "Insert and updateAgentEmail" in {
      val mandates = List(
        createMandate(mandateIds(0), "cred-id-113244018120", "ated", agentIds(1), clientIds(0), when, Active, "old@domeain.com"),
        createMandate(mandateIds(1), "cred-id-113244018121", "ated", agentIds(1), clientIds(1), when, Active, "old@domeain.com"),
        createMandate(mandateIds(2), "cred-id-113244018122", "ated", agentIds(1), clientIds(2), when, Active, "old@domeain.com")
      )
      val agentMandates = Seq(mandateIds(0), mandateIds(1), mandateIds(2))
      createMandatesAndWait(mandates){
        await(mandateRepo.repository.updateAgentEmail(agentMandates, "new@domeain.com")) match {
          case MandateUpdatedEmail => 
            await(mandateRepo.repository
                    .collection
                    .find(in("id", agentMandates:_*))
                    .collect()
                    .toFutureOption()
                    .map {
                      case None => Nil
                      case Some(mandates) => mandates
                    }) match {
              case Nil => fail()
              case mandates if mandates.forall(m => m.agentParty.contactDetails.email == "new@domeain.com") => succeed
              case _ => fail()
          }
          case fetched  => fail(s"ERROR: failed to update agent email address")
        }
      }
    }

    "Insert and updateClientEmail" in {
      val mandates = List(
        createMandate(mandateIds(0), "cred-id-113244018120", "ated", agentIds(1), clientIds(0), when, Active, "old@domeain.com", "old@domeain.com"),
        createMandate(mandateIds(1), "cred-id-113244018121", "ated", agentIds(1), clientIds(1), when, Active, "old@domeain.com", "old@domeain.com"),
        createMandate(mandateIds(2), "cred-id-113244018122", "ated", agentIds(1), clientIds(2), when, Active, "old@domeain.com", "old@domeain.com")
      )
      createMandatesAndWait(mandates){
        await(mandateRepo.repository.updateClientEmail(mandateIds(0), "new@domeain.com")) match {
          case MandateUpdatedEmail => 
            await(mandateRepo.repository
                    .collection
                    .find(org.mongodb.scala.model.Filters.equal("id", mandateIds(0)))
                    .headOption()
                    .map {
                      case None => Nil
                      case Some(mandate) => mandate
                    }) match {
              case Nil => fail()
              case mandate: Mandate if mandate.clientParty.fold(false)(_.contactDetails.email == "new@domeain.com") => succeed
              case _ => fail()
          }
          case fetched  => fail(s"ERROR: failed to update agent email address")
        }
      }
    }

    "Insert and updateAgentCredId" in {
      val mandate = createMandate(mandateIds(0), "cred-id-113244018119", serviceName, agentIds(1), clientIds(0), when, New)
      await(mandateRepo.repository.insertMandate(mandate)) match {
        case mandateCreate: MandateCreated =>
          await(mandateRepo.repository.updateAgentCredId("cred-id-113244018119", "cred-id-113244018144")) match {
            case MandateUpdatedCredId => await(mandateRepo.repository.fetchMandate(mandateIds(0))) match {
              case MandateFetched(fetched) if fetched.createdBy.credId == "cred-id-113244018144" => succeed
              case err => fail(err.toString())
            }
            case err => fail(err.toString())
          }
          
        case err => fail(err.toString())
      }
    }

    "Multiple Inserts and findOldMandates" in {
      val mandates = 
        createMandate(mandateIds(0), "cred-id-113244018120", "ated", agentIds(1), clientIds(0), when, Approved) ::
       (createMandate(mandateIds(1), "cred-id-113244018121", "ated", agentIds(1), clientIds(1), when, PendingCancellation) ::
       (createMandate(mandateIds(2), "cred-id-113244018122", "ated", agentIds(1), clientIds(2), when, PendingActivation) :: 
        Range(3,5).map(idx => createMandate(mandateIds(idx), "cred-id-113244018119", "ated", agentIds(1), clientIds(idx), when, Active)).toList)) ++
        Range(5,10).map(idx => createMandate(mandateIds(idx), "cred-id-113244018119", serviceName, agentIds(1), clientIds(idx), when, Approved)).toList
                     
      createMandatesAndWait(mandates){
        await(mandateRepo.repository.findOldMandates(laterThanWhen)) match {
          case fetched if fetched.length == 8  => succeed
          case fetched  => fail(s"ERROR: returned ${fetched.length} mandates")
        }
      }
    }

    "Muiple Inserts and getClientCancelledMandates" in {
      val mandates = 
        createMandate(mandateIds(0), "cred-id-113244018120", "ated", agentIds(1), clientIds(0), laterThanWhen, Cancelled) ::
       (createMandate(mandateIds(1), "cred-id-113244018121", "ated", agentIds(1), clientIds(1), laterThanWhen, PendingCancellation) ::
       (createMandate(mandateIds(2), "cred-id-113244018122", "ated", agentIds(1), clientIds(2), laterThanWhen, Cancelled) :: 
        Range(3,5).map(idx => createMandate(mandateIds(idx), "cred-id-113244018119", "ated", agentIds(1), clientIds(idx), when, Active)).toList)) ++
        Range(5,10).map(idx => createMandate(mandateIds(idx), "cred-id-113244018119", serviceName, agentIds(1), clientIds(idx), when, Approved)).toList
                     
      createMandatesAndWait(mandates){
        await(mandateRepo.repository.getClientCancelledMandates(when, agentIds(1), "ated")) match {
          case fetched if fetched.length == 2  => succeed
          case fetched  => fail(s"ERROR: returned ${fetched.length} mandates")
        }
      }
    }

    "Insert and removeMandate" in {
      val mandate = createMandate(mandateIds(0), "cred-id-113244018119", serviceName, agentIds(1), clientIds(0), when, New)
      await(mandateRepo.repository.insertMandate(mandate)) match {
        case mandateCreate: MandateCreated =>
          await(mandateRepo.repository.removeMandate(mandateIds(0))) match {
            case MandateRemoved => await(mandateRepo.repository.fetchMandate(mandateIds(0))) match {
              case MandateFetched(fetched) => fail()
              case _ => succeed
            }
            case _ => fail()
          }          
        case _ => fail()
      }
    }

  }

}

