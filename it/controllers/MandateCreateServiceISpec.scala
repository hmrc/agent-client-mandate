package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import helpers.IntegrationSpec
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

        val result: WSResponse = await(hitApplicationEndpoint("/agent/FAKE-AB123456/mandate")
          .post(Json.toJson(mandateDto)))

        result.status mustBe 201

        val mandateID: String = (Json.parse(result.body) \ "mandateId").as[String]
        val fetchedMandate: MandateFetchStatus = await(mandateRepo.repository.fetchMandate(mandateID))
        fetchedMandate match {
          case MandateFetched(fetched) => fetched.currentStatus.status mustBe Status.New
        }
      }
    }
  }

}