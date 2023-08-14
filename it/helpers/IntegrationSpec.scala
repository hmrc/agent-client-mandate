
package helpers

import helpers.application.IntegrationApplication
import helpers.wiremock.WireMockSetup
import org.scalatest._
import org.scalatestplus.play.PlaySpec
import play.api.libs.ws.WSRequest
import uk.gov.hmrc.agentclientmandate.repositories.MandateRepo
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames

trait IntegrationSpec
  extends PlaySpec
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with IntegrationApplication
    with WireMockSetup
    with AssertionHelpers
    with LoginStub {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val BearerToken: String = "mock-bearer-token"

  lazy val mandateRepo: MandateRepo = app.injector.instanceOf[MandateRepo]

  override def beforeAll(): Unit = {
    super.beforeAll()
    startWmServer()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    await(mandateRepo.repository.collection.drop().toFuture())
    resetWmServer()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    await(mandateRepo.repository.collection.drop().toFuture())
    stopWmServer()
  }

  def hitApplicationEndpoint(url: String): WSRequest = {
    val sessionId = HeaderNames.xSessionId -> SessionId
    val authorisation = HeaderNames.authorisation -> BearerToken
    val headers = List(sessionId, authorisation)

    val appendSlash = if(url.startsWith("/")) url else s"/$url"
    ws.url(s"$testAppUrl$appendSlash").withHttpHeaders(headers:_*)
  }
}
