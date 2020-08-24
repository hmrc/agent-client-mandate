package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import helpers.IntegrationSpec
import org.scalatest
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentclientmandate.models.{CreateMandateDto, Status}
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetchStatus, MandateFetched}

class MandateCreateServiceISpec extends IntegrationSpec {

  "agent/[agentcode]/mandate" should {
    "create a agent client mandate" when {
      "a mandate can be successfully created" in {
        val email = "not-real-email@notrealemail.fake"
        val serviceName = "ated"
        val displayName = "display-name"
        val fakeUTR = "FAKE-UTR"
        val mandateDto = CreateMandateDto(
          email, serviceName, displayName
        )

        stubFor(get(urlMatching("/auth/authority"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                s"""{
                  | "accounts": {
                  |  "agent": {
                  |   "agentBusinessUtr": "$fakeUTR"
                  |  }
                  | },
                  | "credentials": {
                  |  "gatewayId" : "GID"
                  | }
                }""".stripMargin
              )
          )
        )



        stubFor(get(urlMatching(s"/registration/details\\?arn=$fakeUTR"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                s"""{
                   |"isAnIndividual" : true,
                   |"individual": {
                   |  "firstName": "First",
                   |  "lastName" : "Last"
                   |}
                   |}""".stripMargin
              )
          )
        )

        stubFor(post(urlMatching(s"/auth/authorise"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                s"""{
                   |	"groupId": "42424200-0000-0000-0000-000000000000",
                   |	"affinityGroup": "Agent",
                   |	"users": [
                   |		{
                   |			"credId": "42424211-Agent-Admin",
                   |			"name": "Assistant Agent",
                   |			"email": "default@example.com",
                   |			"credentialRole": "Assistant",
                   |			"description": "User Description"
                   |		}
                   |	],
                   |	"allEnrolments": [
                   |		{
                   |			"key": "HMRC-ATED-ORG",
                   |			"identifiers": [
                   |				{
                   |					"key": "ATEDRefNumber",
                   |					"value": "XN1200000100001"
                   |				}
                   |			],
                   |			"enrolmentFriendlyName": "Ated Enrolment",
                   |			"assignedUserCreds": [
                   |				"42424211-Client-Admin"
                   |			],
                   |			"state": "Activated",
                   |			"enrolmentType": "delegated",
                   |			"assignedToAll": false
                   |		},
                   |		{
                   |			"key": "HMRC-AGENT-AGENT",
                   |			"identifiers": [
                   |				{
                   |					"key": "AgentRefNumber",
                   |					"value": "FAKE-UTR"
                   |				}
                   |			],
                   |			"enrolmentFriendlyName": "Agent Enrolment",
                   |			"assignedUserCreds": [
                   |				"42424211-Client-Admin"
                   |			],
                   |			"state": "Activated",
                   |			"enrolmentType": "delegated",
                   |			"assignedToAll": false
                   |		}
                   |	],
                   |  "agentInformation": {
                   |    "agentId": "007",
                   |	  "agentCode": "123456789123",
                   |    "agentFriendlyName": "FakeTim"
                   |   },
                   |  "optionalCredentials": {
                   |    "providerType": "GovernmentGateway",
                   |    "providerId": "cred-id-113244018119"
                   |  }
                   |}
                   |""".stripMargin
              )
          )
        )

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
  }

}
