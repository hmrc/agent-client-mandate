# agent-client-mandate

[![Build Status](https://travis-ci.org/hmrc/agent-client-mandate.svg)](https://travis-ci.org/hmrc/agent-client-mandate) [ ![Download](https://api.bintray.com/packages/hmrc/releases/agent-client-mandate/images/download.svg) ](https://bintray.com/hmrc/releases/agent-client-mandate/_latestVersion)

### POST /agent-client-mandate/agent/:ac/mandate
Used to create a new Client Mandate

| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 400    | Bad Request |

**Example request with a valid body**

```json
  {
    "party": {
      "id": "JARN1234567",
      "name": "Joe Bloggs",
      "type": "Organisation"
    },
    "contactDetails": {
      "email": "joe.bloggs@test.com",
      "phone": "0123456789"
    },
    "service": "ATED"
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
