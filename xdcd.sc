import org.joda.time.DateTime
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientmandate.models._

val registeredAddressDetails = RegisteredAddressDetails("address1", "address2", None, None, None, "FR")
val contactDetails = EtmpContactDetails()
val x = AgentDetails("safeId", false, None,
  Some(Organisation("Org Name", Some(true), Some("org_type"))),
  registeredAddressDetails, contactDetails, None)

Json.toJson(NonUKClientDto("safeId", "atedRefNum", "ated", "aa@mail.com", "arn", "bb@mail.com", "client display name"))

