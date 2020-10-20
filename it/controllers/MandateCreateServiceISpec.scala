package controllers

import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.StatusCodes.NoContent
import helpers.IntegrationSpec
import org.joda.time.DateTime
import org.scalatest
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.WSResponse
import play.libs.ws.WSRequest
import uk.gov.hmrc.agentclientmandate.models
import uk.gov.hmrc.agentclientmandate.models.Status.{Cancelled, New, PendingCancellation}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetchStatus, MandateFetched, MandateUpdated, MandateUpdatedCredId}
import utils.Stubs._

class MandateCreateServiceISpec extends IntegrationSpec {

  val mandateDto: CreateMandateDto = CreateMandateDto(
    email = "not-real-email@notrealemail.fake", serviceName = "ated", displayName = "display-name"
  )

  "agent/[agentcode]/mandate" should {
    "create an agent client mandate" in {

      stubGetArn
      stubGetAuth
      stubPostAuth
      stubEmail

      val result: WSResponse = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate")
        .post(Json.toJson(mandateDto)))

      result.status mustBe 201

      val mandateID: String = (Json.parse(result.body) \ "mandateId").as[String]
      val fetchedMandate: MandateFetchStatus = await(mandateRepo.repository.fetchMandate(mandateID))
      fetchedMandate match {
        case MandateFetched(fetched) => fetched.currentStatus.status mustBe Status.New
        case _ => scalatest.Assertions.fail()
      }
    }
  }

  "/org/:org/mandate/:clientId/:service" should {
    "find client by service and client id" in {

      stubGetArn
      stubGetAuth
      stubPostAuth
      stubEmail
      stubGetSubscription

        val result: WSResponse = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate")
          .post(Json.toJson(mandateDto)))

        val mandateID: String = (Json.parse(result.body) \ "mandateId").as[String]
        val mandateForApproval = Mandate(mandateID, models.User("cred-id-113244018119", "First Last", Some("FAKE-AB123456")), None, None,
                Party("FAKE-UTR", "First Last", PartyType.Individual, ContactDetails("not-real-email@notrealemail.fake")),
                Some(Party("Fake-ID", "First Last", PartyType.Individual, ContactDetails("not-real-email@notrealemail.fake", Some("No")))),
                MandateStatus(New, DateTime.now(), "cred-id-113244018119"), Nil, Subscription(None, Service("ATED", "ated")), "display-name")
        await(hitApplicationEndpoint("/org/approvedstatus/mandate/approve")
          .post(Json.toJson(mandateForApproval)))

        stubGetSafeId

        val result2 = await(hitApplicationEndpoint("/org/:org/mandate/Test/ated").get())
        val mandateStatus: String = (Json.parse(result2.body) \ "currentStatus" \ "status").as[String]

          mandateStatus mustBe Status.Approved.toString
      }
    }

  "/agent/:ac/mandate/service/:arn/:service" should {
    "find all mandates by service" in {

      stubGetArn
      stubGetAuth
      stubPostAuth
      stubEmail
      stubGetSubscription

      val result: WSResponse = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate")
        .post(Json.toJson(mandateDto)))
      val mandateID: String = (Json.parse(result.body) \ "mandateId").as[String]

      val result2 = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate/service/FAKE-UTR/ated")
        .get())
      val mandates: List[JsObject] = Json.parse(result2.body).as[List[JsObject]]

      val timeStamp = (mandates.head \\ "timestamp")
      val expectedResult: JsValue = Json.parse(
        s"""
           |[{"id":"$mandateID",
           |"createdBy":{"credId":"cred-id-113244018119","name":"First Last","groupId":"FAKE-AB123456"},
           |"agentParty":{"id":"FAKE-UTR","name":"First Last","type":"Individual","contactDetails":{"email":"not-real-email@notrealemail.fake"}},
           |"currentStatus":{"status":"New","timestamp":${timeStamp.head},"updatedBy":"cred-id-113244018119"},
           |"statusHistory":[],
           |"subscription":{"service":{"id":"ATED","name":"ated"}},
           |"clientDisplayName":"display-name"}]
           |""".stripMargin)
      result2.body mustBe expectedResult.toString
      }
    }



  "/agent/:ac/mandate/isAgentMissingEmail/:arn/:service" should {
    "find mandates missing agent email" in {

      stubGetArn
      stubGetAuth
      stubPostAuth

      val result: WSResponse = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate")
        .post(Json.toJson(mandateDto)))
      val mandateID: String = (Json.parse(result.body) \ "mandateId").as[String]
      val MandateMissingAgentEmail = Mandate(mandateID, models.User("cred-id-113244018119", "First Last", Some("FAKE-AB123456")), None, None,
        Party("FAKE-UTR", "First Last", PartyType.Individual, ContactDetails("")),
        Some(Party("Fake-ID", "First Last", PartyType.Individual, ContactDetails("not-real-email@notrealemail.fake", Some("No")))),
        MandateStatus(New, DateTime.now(), "cred-id-113244018119"), Nil, Subscription(None, Service("ATED", "ated")), "display-name")
      await(hitApplicationEndpoint("/org/approvedstatus/mandate/approve").post(Json.toJson(MandateMissingAgentEmail)))
      val missingEmailResult = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate/isAgentMissingEmail/FAKE-UTR/ated")
        .get())
      missingEmailResult.status mustBe 204
    }
  }

  "/agent/:ac/mandate/updateAgentEmail/:arn/:service" should {
    "update agent email" in {

      stubGetArn
      stubGetAuth
      stubPostAuth

      val result: WSResponse = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate")
        .post(Json.toJson(mandateDto)))
      val mandateID: String = (Json.parse(result.body) \ "mandateId").as[String]
      val MandateMissingAgentEmail = Mandate(mandateID, models.User("cred-id-113244018119", "First Last", Some("FAKE-AB123456")), None, None,
        Party("FAKE-UTR", "First Last", PartyType.Individual, ContactDetails("")),
        Some(Party("Fake-ID", "First Last", PartyType.Individual, ContactDetails("not-real-email@notrealemail.fake", Some("No")))),
        MandateStatus(New, DateTime.now(), "cred-id-113244018119"), Nil, Subscription(None, Service("ATED", "ated")), "display-name")
      await(hitApplicationEndpoint("/org/approvedstatus/mandate/approve").post(Json.toJson(MandateMissingAgentEmail)))
      val missingEmailResult = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate/isAgentMissingEmail/FAKE-UTR/ated")
        .get())
      missingEmailResult.status mustBe 204
      val agentsMissingEmail: String = "not-real-email@notrealemail.fake"
      val emailUpdated = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate/updateAgentEmail/FAKE-UTR/ated")
        .post(Json.toJson(agentsMissingEmail)))
      emailUpdated.status mustBe 200

    }
  }



  "/org/:org/mandate/updateClientEmail/:mandateId" should {
    "update client email" in {

  stubGetArn
  stubGetAuth
  stubPostAuth

  val result: WSResponse = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate")
    .post(Json.toJson(mandateDto)))
  val mandateID: String = (Json.parse(result.body) \ "mandateId").as[String]

  val MandateMissingAgentEmail = Mandate(mandateID, models.User("cred-id-113244018119", "First Last", Some("FAKE-AB123456")), None, None,
    Party("FAKE-UTR", "First Last", PartyType.Individual, ContactDetails("not-real-email@notrealemail.fake")),
    Some(Party("Fake-ID", "First Last", PartyType.Individual, ContactDetails("", Some("No")))),
    MandateStatus(New, DateTime.now(), "cred-id-113244018119"), Nil, Subscription(None, Service("ATED", "ated")), "display-name")
  await(hitApplicationEndpoint("/org/approvedstatus/mandate/approve").post(Json.toJson(MandateMissingAgentEmail)))

  val clientsNewEmail: String = "not-real-email@notrealemail.fake"
  val emailUpdated = await(hitApplicationEndpoint("/org/:org/mandate/updateClientEmail/:mandateId")
    .post(Json.toJson(clientsNewEmail)))
  emailUpdated.status mustBe 200

    }
  }

  "/agent/:ac/mandate/updateAgentCredId" should {
    "update old agent credId" in {

      stubGetAuthOldId

      val oldCred: WSResponse = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate")
        .post(Json.toJson(mandateDto)))
      oldCred.status mustBe 201

      val agentID: String = (Json.parse(oldCred.body) \ "agentParty" \ "ContactDetails" \ "email" ).as[String]
      val updateMandate: MandateStatus = await(mandateRepo.repository.updateMandate(agentID))
      updateMandate.status mustBe(200)

      stubGetAuth

      val newCred = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate/updateAgentCredId")
        .post(Json.toJson(mandateDto)))

    }
  }

  "/agent/:ac/mandate/clientCancelledNames/:arn/:service " should {
    "get client names for cancelled mandates" in {

      stubGetArn
      stubGetAuth
      stubPostAuth
      stubEmail
      stubGetSubscription

      val result: WSResponse = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate")
        .post(Json.toJson(mandateDto)))

      val mandateID: String = (Json.parse(result.body) \ "mandateId").as[String]
      val mandateForApproval = Mandate(mandateID, models.User("cred-id-113244018119", "First Last", Some("FAKE-AB123456")), None, None,
        Party("FAKE-UTR", "First Last", PartyType.Individual, ContactDetails("not-real-email@notrealemail.fake")),
        Some(Party("Fake-ID", "First Last", PartyType.Individual, ContactDetails("not-real-email@notrealemail.fake", Some("No")))),
        MandateStatus(New, DateTime.now(), "cred-id-113244018119"), Nil, Subscription(None, Service("ATED", "ated")), "display-name")
      await(hitApplicationEndpoint("/org/approvedstatus/mandate/approve")
        .post(Json.toJson(mandateForApproval)))
      val mandateForCancellation = Mandate(mandateID, models.User("cred-id-113244018119", "First Last", Some("FAKE-AB123456")), None, None,
        Party("FAKE-UTR", "First Last", PartyType.Individual, ContactDetails("not-real-email@notrealemail.fake")),
        Some(Party("Fake-ID", "First Last", PartyType.Individual, ContactDetails("not-real-email@notrealemail.fake", Some("No")))),
        MandateStatus(PendingCancellation, DateTime.now(), "cred-id-113244018119"), Nil, Subscription(None, Service("ATED", "ated")), "display-name")
      await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate/edit")
        .post(Json.toJson(mandateForCancellation)))

      stubEmail

      val cancelledMandate = Mandate(mandateID, models.User("cred-id-113244018119", "First Last", Some("FAKE-AB123456")), None, None,
        Party("FAKE-UTR", "First Last", PartyType.Individual, ContactDetails("not-real-email@notrealemail.fake")),
        Some(Party("Fake-ID", "First Last", PartyType.Individual, ContactDetails("not-real-email@notrealemail.fake", Some("No")))),
        MandateStatus(Cancelled, DateTime.now(), "cred-id-113244018119"), Nil, Subscription(None, Service("ATED", "ated")), "display-name")
      await(hitApplicationEndpoint(s"/org/:org/mandate/remove/$mandateID")
        .post(Json.toJson(cancelledMandate)))

      val cancellationApproved = await(hitApplicationEndpoint(
        "/agent/:ac/mandate/clientCancelledNames/FAKE-UTR/ated").get())

      cancellationApproved mustBe Status.Cancelled

    }
  }

}
