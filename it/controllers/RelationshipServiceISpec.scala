package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import helpers.IntegrationSpec
import org.joda.time.DateTime
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentclientmandate.models._
import uk.gov.hmrc.agentclientmandate.repositories.{MandateFetchStatus, MandateFetched}

class RelationshipServiceISpec extends IntegrationSpec {

  "agent/[agentcode]/mandate/activate/[mandateid]" should {
    "activate a agent client mandate" when {
      "a mandate can be successfully activated" in {
        val email = "not-real-email@notrealemail.fake"
        val serviceName = "ATED"
        val displayName = "display-name"
        val mandateID = "mandateID"
        val fakeUTR = "FAKE-UTR"
        val user = User(
          "credID", "Name"
        )
        val subscription = Subscription(None, Service("ATED", serviceName))
        val agentParty = Party(
          fakeUTR,
          "First Name",
          PartyType.Individual,
          ContactDetails(email, None)
        )
        val mandateStatus = MandateStatus(
          Status.Approved, DateTime.parse("2010-06-30T01:20"), "credID"
        )
        val mandate = Mandate(
          mandateID, user, None, None, agentParty, None, mandateStatus, Nil, subscription, displayName
        )

        await(mandateRepo.repository.insertMandate(mandate))

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

        stubFor(post(urlMatching(s"/auth/authorise"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                s"""{
                   |"credentials": {
                   |  "providerId": "12345-credId",
                   |  "providerType": "GovernmmentGateway"
                   |},
                   |"groupIdentifier" : "testGroupId-1234 "
                   |}""".stripMargin
              )
          )
        )

        val result: WSResponse = await(
          hitApplicationEndpoint(s"/agent/FAKE-AB123456/mandate/activate/$mandateID")
            .withHeaders(HeaderNames.SET_COOKIE -> getSessionCookie())
            .post(Json.toJson("""{}"""))
        )

        result.status mustBe 200

        val fetchedMandate: MandateFetchStatus = await(mandateRepo.repository.fetchMandate(mandateID))
        fetchedMandate match {
          case MandateFetched(fetched) => fetched.currentStatus.status mustBe Status.PendingActivation
        }
      }
    }
  }

}