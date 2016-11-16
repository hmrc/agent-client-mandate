# agent-client-mandate

[![Build Status](https://travis-ci.org/hmrc/agent-client-mandate.svg)](https://travis-ci.org/hmrc/agent-client-mandate) [ ![Download](https://api.bintray.com/packages/hmrc/releases/agent-client-mandate/images/download.svg) ](https://bintray.com/hmrc/releases/agent-client-mandate/_latestVersion)

# Adding a new service

You need to update the code to allow it to mantain the relationship with etmp. This requires the following properties which are stored against the relevant service name in
identifiers.properties

```json
    ated.identifier = ATEDRefNumber
    ated.serviceId = ATED
    ated.ggEnrolment = HMRC_ATED_ORG
```

 | Name | Example |  Description     |
 |--------|-------------|
 | identifier | ATEDRefNumber | The name of the id field in the gateway enrolments   |
 | serviceId | ATED | The service name          |
 | ggEnrolment | HMRC_ATED_ORG | The gateway enrolment name  |

## Code Change
Currently RelationshipService.maintainRelationship has only been written to work with ATED. This will have to be refactored to allow it to work for all services.



### POST /agent-client-mandate/agent/:ac/mandate
Used to create a new Client Mandate

| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 400    | Bad Request |

**Example request with a valid body**

```json
  {
    "email": "a@b.c"
    "serviceName": "ATED"
    "displayName": "ACME Ltd."
  }
```

**Response body**

```json
  {
    "mandateId": "AS12345678"
  }
```

### GET /agent-client-mandate/{agent/:ac || org/:org}/mandate/:mandateId
Retrieve the specific mandate
| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 404    | Not Found   |


### POST /agent-client-mandate/org/:org/mandate/approve
Client Approves the mandate

### POST /agent-client-mandate/agent/:ac/mandate/activate/:mandateId
Agent actives/approves Mandate

### GET /agent-client-mandate/agent/:ac/mandate/service/:arn/:service
Fetch all Mandates for this agent

### POST /agent-client-mandate/agent/:ac/mandate/rejectClient/:mandateId
Agents Reject the Clients with this mandate Id

### GET /agent-client-mandate/agent/:ac/mandate/agentDetails
Fetch Agent Details
| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 404    | Not Found   |

**Example response with a valid body**

```json
  {
    "agentName": "abc agency"
    "addressDetails": {
        "addressLine1": "mandatory line 1",
        "addressLine2": "mandatory line 2",
        "addressLine3": "optional line 3",
        "addressLine4": "optional line 4",
        "postalCode": "optional NE1 1JE",
        "countryCode": "GB"
    }
  }
```


### POST /agent-client-mandate/agent/:ac/mandate/remove/:mandateId
Remove Client

### POST /agent-client-mandate/org/:org/mandate/remove/:mandateId
Remove Agent

### POST /agent-client-mandate/agent/:ac/mandate/importExisting
Import Existing Relationship

### POST /agent-client-mandate/agent/:ac/mandate/non-uk
Create relationship for non-uk clients by agent




### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
