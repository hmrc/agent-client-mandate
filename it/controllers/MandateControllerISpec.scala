package controllers

import helpers.IntegrationSpec
import org.joda.time.DateTime
import org.scalatest
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentclientmandate.models
import uk.gov.hmrc.agentclientmandate.models.Status.{Approved, Cancelled, New, PendingCancellation}
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetchStatus, MandateFetched, MandateRemoved}
import utils.Stubs._

class MandateControllerISpec extends IntegrationSpec {

  val mandateDto: CreateMandateDto = CreateMandateDto(
    email = "not-real-email@notrealemail.fake", serviceName = "ated", displayName = "display-name"
  )

  "/agent/:ac/mandate" should {
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

  "/org/mandate/:clientId/:service" should {
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
        await(hitApplicationEndpoint("/org/mandate/approve")
          .post(Json.toJson(mandateForApproval)))

        stubGetSafeId

        val result2 = await(hitApplicationEndpoint("/org/mandate/Test/ated").get())
        val mandateStatus: String = (Json.parse(result2.body) \ "currentStatus" \ "status").as[String]

          mandateStatus mustBe Status.Approved.toString
      }
    }

  "/agent/mandate/service/:arn/:service" should {
    "find all mandates by service" in {

      stubGetArn
      stubGetAuth
      stubPostAuth
      stubEmail
      stubGetSubscription

      val result: WSResponse = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate")
        .post(Json.toJson(mandateDto)))
      val mandateID: String = (Json.parse(result.body) \ "mandateId").as[String]

      val result2 = await(hitApplicationEndpoint("/agent/mandate/service/FAKE-UTR/ated")
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



  "/agent/mandate/isAgentMissingEmail/:arn/:service" should {
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
      await(hitApplicationEndpoint("/org/mandate/approve").post(Json.toJson(MandateMissingAgentEmail)))
      val missingEmailResult = await(hitApplicationEndpoint("/agent/mandate/isAgentMissingEmail/FAKE-UTR/ated")
        .get())
      missingEmailResult.status mustBe 204
    }
  }

  "/agent/mandate/updateAgentEmail/:arn/:service" should {
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
      await(hitApplicationEndpoint("/org/mandate/approve").post(Json.toJson(MandateMissingAgentEmail)))
      val missingEmailResult = await(hitApplicationEndpoint("/agent/mandate/isAgentMissingEmail/FAKE-UTR/ated")
        .get())
      missingEmailResult.status mustBe 204
      val agentsMissingEmail: String = "not-real-email@notrealemail.fake"
      val emailUpdated = await(hitApplicationEndpoint("/agent/mandate/updateAgentEmail/FAKE-UTR/ated")
        .post(Json.toJson(agentsMissingEmail)))
      emailUpdated.status mustBe 200

    }
  }



  "/org/mandate/updateClientEmail/:mandateId" should {
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
  val emailUpdated = await(hitApplicationEndpoint(s"/org/mandate/updateClientEmail/$mandateID")
    .post(Json.toJson(clientsNewEmail)))
  emailUpdated.status mustBe 200

    }
  }

  "/agent/mandate/updateAgentCredId" should {
    "update old agent credId" in {

      stubGetArn
      stubGetAuth
      stubPostAuth
      stubEmail

      val oldCred: WSResponse = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate")
        .post(Json.toJson(mandateDto)))
      oldCred.status mustBe 201

      stubGetSubscription

      val newCred = await(hitApplicationEndpoint("/agent/mandate/updateAgentCredId")
        .post(Json.toJson("cred ID")))
      newCred.status mustBe 200
    }
  }

  "/agent/mandate/clientCancelledNames/:arn/:service" should {
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
      await(hitApplicationEndpoint("/org/mandate/approve")
        .post(Json.toJson(mandateForApproval)))
      val mandateForCancellation = Mandate(mandateID, models.User("cred-id-113244018119", "First Last", Some("FAKE-AB123456")), None, None,
        Party("FAKE-UTR", "First Last", PartyType.Individual, ContactDetails("not-real-email@notrealemail.fake")),
        Some(Party("Fake-ID", "First Last", PartyType.Individual, ContactDetails("not-real-email@notrealemail.fake", Some("No")))),
        MandateStatus(Cancelled, DateTime.now(), "cred-id-113244018118"), Nil, Subscription(None, Service("ATED", "ated")), "display-name")
      await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate/edit")
        .post(Json.toJson(mandateForCancellation)))



      stubEmail

      val clientCancelledNames = await(hitApplicationEndpoint(
        "/agent/mandate/clientCancelledNames/FAKE-UTR/ated").get())

      clientCancelledNames.status mustBe 200

    }
  }

  "/mandate/remove/:mandateId" should {
    "remove mandate" in {

      stubGetArn
      stubGetAuth
      stubPostAuth
      stubEmail
      stubGetSubscription

      val result: WSResponse = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate")
        .post(Json.toJson(mandateDto)))

      val mandateID: String = (Json.parse(result.body) \ "mandateId").as[String]
      val mandateForRemoval = Mandate(mandateID, models.User("cred-id-113244018119", "First Last", Some("FAKE-AB123456")), None, None,
        Party("FAKE-UTR", "First Last", PartyType.Individual, ContactDetails("not-real-email@notrealemail.fake")),
        Some(Party("Fake-ID", "First Last", PartyType.Individual, ContactDetails("not-real-email@notrealemail.fake", Some("No")))),
        MandateStatus(New, DateTime.now(), "cred-id-113244018119"), Nil, Subscription(None, Service("ATED", "ated")), "display-name")
      val removeMandate = await(hitApplicationEndpoint(s"/mandate/remove/$mandateID ")
        .post(Json.toJson(mandateForRemoval)))

      removeMandate.status mustBe 200

    }
  }
}
