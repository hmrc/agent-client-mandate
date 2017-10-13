agent-client-mandate
====================

[![Build Status](https://travis-ci.org/hmrc/agent-client-mandate.svg)](https://travis-ci.org/hmrc/agent-client-mandate) [ ![Download](https://api.bintray.com/packages/hmrc/releases/agent-client-mandate/images/download.svg) ](https://bintray.com/hmrc/releases/agent-client-mandate/_latestVersion)

Microservice for Agent Client Mandate service. This implements the main business logic for maintaining relationship between HMRC agents and clients using mandates, communicating with ETMP(HOD), Government Gateway and Mongo Database for storage/retrieval of mandates. 
The microservice is based on the RESTful API structure, receives and sends data using JSON to either from.

All data received is validated against the relevant schema to ensure correct format of the data being received.

### Adding a new service

In order to work on behalf of a client to work on a particular service, the service related information must be collected from GG (and, ETMP). The code need to be updated to allow it to mantain the relationship with ETMP. This requires the following properties which are stored against the relevant service name in
identifiers.properties

For example:
```json
    ated.identifier = ATEDRefNumber
    ated.serviceId = ATED
    ated.ggEnrolment = HMRC_ATED_ORG
```

where,

| Name | Example |  Description     |
|--------|-------------|-------------|
| identifier | ATEDRefNumber | The name of the id field in the gateway enrolments   |
| serviceId | ATED | The service name          |
| ggEnrolment | HMRC_ATED_ORG | The gateway enrolment name  |

### Code Change
Currently RelationshipService.maintainRelationship has only been written to work with ATED. This will have to be refactored to allow it to work for all services.

```scala
 if (mandate.subscription.service.name.toUpperCase == AtedService) {
      val serviceId = mandate.subscription.service.id
      val identifier = identifiers.getString(s"${serviceId.toLowerCase()}.identifier")
      val clientId = mandate.subscription.referenceNumber.getOrElse("")
      val credId = getCredId()

      for {
        updatedBy <- credId
      } yield {
        //Then do this each time a 'create' needs to be done
        val task = Task("create", Map("clientId" -> clientId,
          "agentPartyId" -> mandate.agentParty.id,
          "serviceIdentifier" -> identifier,
          "agentCode" -> agentCode,
          "mandateId" -> mandate.id,
          "credId" -> updatedBy))
        //execute asynchronously
        TaskController.execute(task)
      }
    } else {
      throw new BadRequestException("This is only defined for ATED")
    }

```


### Create a new Client Mandate
``` POST /agent-client-mandate/agent/:ac/mandate ```

| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 400    | Bad Request |

**Example request with a valid body**

```json
  {
    "email": "a@b.c",
    "serviceName": "ATED",
    "displayName": "ACME Ltd."
  }
```

**Response body**

```json
  {
    "mandateId": "AS12345678"
  }
```

### Retrieve the specific mandate
``` GET /agent-client-mandate/{agent/:ac || org/:org}/mandate/:mandateId```

| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 404    | Not Found   |

**Response body**

```json
{
	"id": "123",
	"createdBy": {
		"credId": "credid",
		"name": "name"
	},
	"agentParty": {
		"id": "JARN123456",
		"name": "Joe Bloggs",
		"type": "Organisation",
		"contactDetails": {
			"email": "test@test.com",
			"phone": "0123456789"
		}
	},
	"currentStatus": {
		"status": "New",
		"timestamp": 1507809964876,
		"updatedBy": "credid"
	},
	"statusHistory": [],
	"subscription": {
		"service": {
			"id": "ated",
			"name": "ATED"
		}
	},
	"clientDisplayName": "client display name"
}
```

### Client Approves the mandate
``` POST /agent-client-mandate/org/:org/mandate/approve```

| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 404    | Not Found   |
| 500    | Internal Server Error   |

### Agent activates/accepts Mandate
``` POST /agent-client-mandate/agent/:ac/mandate/activate/:mandateId```

 Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 404    | Not Found   |

### Fetch all Mandates for this agent
``` GET /agent-client-mandate/agent/:ac/mandate/service/:arn/:service```

### Agents Reject the Clients with this mandate Id
``` POST /agent-client-mandate/agent/:ac/mandate/rejectClient/:mandateId```

### Fetch Agent Details
``` GET /agent-client-mandate/agent/:ac/mandate/agentDetails```

| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 404    | Not Found   |

**Example response with a valid body**

```json
  {
    "agentName": "abc agency",
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


### Agent Remove Client (break relationship)
``` POST /agent-client-mandate/agent/:ac/mandate/remove/:mandateId ```

### Client Remove Agent (break relationship)
``` POST /agent-client-mandate/org/:org/mandate/remove/:mandateId ```

### Import Existing Relationship
``` POST /agent-client-mandate/agent/:ac/mandate/importExisting```

### Create relationship for non-uk clients by agent (self-authorised)
``` POST /agent-client-mandate/agent/:ac/mandate/non-uk ```

### Get client friendly names where client cancelled within 28 days
``` GET /agent/:ac/mandate/clientCancelledNames/:arn/:service ```

### Update an existing non-uk mandate
```POST /agent/:ac/mandate/non-uk/update```              

### edit an existing mandate
```POST  /agent/:ac/mandate/edit``` 

**Example request with a valid body**
```json
{
	"id": "123",
	"createdBy": {
		"credId": "credid",
		"name": "name"
	},
	"agentParty": {
		"id": "JARN123456",
		"name": "Joe Bloggs ---Edited",
		"type": "Organisation",
		"contactDetails": {
			"email": "test@test.com",
			"phone": "0123456789"
		}
	},
	"currentStatus": {
		"status": "New",
		"timestamp": 1507809964876,
		"updatedBy": "credid"
	},
	"statusHistory": [],
	"subscription": {
		"service": {
			"id": "ated",
			"name": "ATED"
		}
	},
	"clientDisplayName": "client display name"
}
```   
                
### fetch mandate for client
```GET  /org/:org/mandate/:clientId/:service```         

### check for agents missing email in mandate
```GET  /agent/:ac/mandate/isAgentMissingEmail/:arn/:service``` 

### update missing email for agent in mandate
```POST /agent/:ac/mandate/updateAgentEmail/:arn/:service```    

### update client email in mandate
```POST /org/:org/mandate/updateClientEmail/:mandateId```       

### update agent credId in mandate
```POST /agent/:ac/mandate/updateAgentCredId```            

### get client friendly names where client cancelled within 28 days
```GET  /agent/:ac/mandate/clientCancelledNames/:arn/:service```   

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
