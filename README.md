agent-client-mandate
====================

[![Build Status](https://travis-ci.org/hmrc/agent-client-mandate.svg)](https://travis-ci.org/hmrc/agent-client-mandate)

Microservice for Agent Client Mandate service. This implements the main business logic for maintaining relationship between HMRC agents and clients using mandates, communicating with ETMP(HOD), Government Gateway and Mongo Database for storage/retrieval of mandates. 
The microservice is based on the RESTful API structure, receives and sends data using JSON to either from.

All data received is validated against the relevant schema to ensure correct format of the data being received.

## Adding a new service

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

## Code Change
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

## Agent Client Mandate APIs

| PATH | Supported Methods | Description |
|------|-------------------|-------------|
| ```/ated/agent/:ac/client/:ated``` | GET | authorisation Call for ATED for agent-client relationship  |
|```/agent/:ac/mandate/agentDetails ``` | GET | fetch agent details  |
| ```/agent/:ac/mandate ``` | POST | create a new mandate |
| ```/agent/:ac/mandate/:mandateId ``` | GE | agent retrieve mandate |
| ```/org/:org/mandate/:mandateId ``` | GET | client retrieve mandate |
| ```/agent/:ac/mandate/service/:arn/:service``` | GET | retrieve all mandates by service for the agent |
| ```/org/:org/mandate/approve``` | GET | client approves the mandate |
| ```/agent/:ac/mandate/activate/:mandateId``` | POST | agent activates/accepts mandate |
| ```/agent/:ac/mandate/rejectClient/:mandateId``` | POST | agent reject the clients with this mandate id |
| ```/agent/:ac/mandate/remove/:mandateId ``` | POST | agent removes client |
| ``` /org/:org/mandate/remove/:mandateId ``` | POST | client removes agent |
| ``` /agent/:ac/mandate/non-uk ``` | POST | create relationship for non-uk clients by agent (self-authorised) |
| ``` /agent/:ac/mandate/non-uk/update ``` | POST | update an existing non-uk mandate |
| ``` /agent/:ac/mandate/edit ``` | POST | edit mandate |
| ``` /org/:org/mandate/:clientId/:service ``` | GET | fetch mandate for client |
| ``` /agent/:ac/mandate/isAgentMissingEmail/:arn/:service ``` | GET | check for agents missing email |
| ``` /org/:org/mandate/updateClientEmail/:mandateId ``` | POST | update client email |
| ``` /agent/:ac/mandate/updateAgentCredId``` | POST | update agent email |
| ``` /agent/:ac/mandate/clientCancelledNames/:arn/:service``` | GET | get client friendly names where client cancelled within 28 days |

## Usage

### authorisation call for ATED for agent-client relationship 
```GET  /ated/agent/123456789/client/:ated```

**Response**

 Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 401    | Unauthorised |

 No body

### fetch agent details
```GET /agent/123456789/mandate/agentDetails ``` 

**Response**

 Status | Message     |
|--------|-------------|
| 200    | Ok          |

```json
{
	"safeId": "safeId",
	"isAnIndividual": false,
	"organisation": {
		"organisationName": "Org Name",
		"isAGroup": true,
		"organisationType": "org_type"
	},
	"addressDetails": {
		"addressLine1": "address1",
		"addressLine2": "address2",
		"countryCode": "FR"
	},
	"contactDetails": {}
}
```

### create a new Mandate
``` POST /agent/123456789/mandate ```

**Request body**

```json
  {
    "email": "a@b.c",
    "serviceName": "ATED",
    "displayName": "ACME Ltd."
  }
```

**Response**

| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 400    | Bad Request |

```json
  {
    "mandateId": "AS12345678"
  }
```

### client/agent retrieve mandate
 ```POST /agent/123456789/mandate/95D42795 ```
 
 ```POST /org/987654321/mandate/95D42795 ``` 

**Response**

| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 404    | Not Found   |

```json
{
	"id": "AS12345678",
	"createdBy": {
		"credId": "credid",
		"name": "Joe Bloggs"
	},
	"agentParty": {
		"id": "JARN123456",
		"name": "Joe Bloggs",
		"type": "Organisation",
		"contactDetails": {
			"email": "",
			"phone": ""
		}
	},
	"currentStatus": {
		"status": "New",
		"timestamp": 1508499813255,
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

### retrieve all mandates by service for the agent
```GET  /agent/123456789/mandate/service/JARN1234567/ATED```

**Response**

| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 404    | Not Found   |

```json
[{
	"id": "AS12345678",
	"createdBy": {
		"credId": "credid",
		"name": "Joe Bloggs"
	},
	"agentParty": {
		"id": "JARN123456",
		"name": "Joe Bloggs",
		"type": "Organisation",
		"contactDetails": {
			"email": "",
			"phone": ""
		}
	},
	"currentStatus": {
		"status": "New",
		"timestamp": 1508499813255,
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
}]
```

### client approves the mandate
``` POST /org/987654321/mandate/approve```

**Request body**

```json
{
	"id": "AS12345678",
	"createdBy": {
		"credId": "credid",
		"name": "Joe Bloggs"
	},
	"agentParty": {
		"id": "JARN123456",
		"name": "Joe Bloggs",
		"type": "Organisation",
		"contactDetails": {
			"email": "",
			"phone": ""
		}
	},
	"currentStatus": {
		"status": "New",
		"timestamp": 1508499813255,
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

**Response**

| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 404    | Not Found   |
| 500    | Internal Server Error   |

```json
{
	"id": "AS12345678",
	"createdBy": {
		"credId": "credid",
		"name": "Joe Bloggs"
	},
	"agentParty": {
		"id": "JARN123456",
		"name": "Joe Bloggs",
		"type": "Organisation",
		"contactDetails": {
			"email": "",
			"phone": ""
		}
	},
	"clientParty": {
		"id": "ABC12345",
		"name": "Client Name",
		"type": "Organisation",
		"contactDetails": {
			"email": "client@mail.com"
		}
	},
	"currentStatus": {
		"status": "Approved",
		"timestamp": 1508499660637,
		"updatedBy": "clientCredId"
	},
	"statusHistory": [{
		"status": "New",
		"timestamp": 1508499660637,
		"updatedBy": "credid"
	}],
	"subscription": {
		"service": {
			"id": "ated",
			"name": "ATED"
		}
	},
	"clientDisplayName": "client display name"
}
```

### agent activates/accepts Mandate
``` POST /agent/123456789/mandate/activate/95D42795```

**Request body**

```json
{
	"id": "AS12345678",
	"createdBy": {
		"credId": "credid",
		"name": "Joe Bloggs"
	},
	"agentParty": {
		"id": "JARN123456",
		"name": "Joe Bloggs",
		"type": "Organisation",
		"contactDetails": {
			"email": "",
			"phone": ""
		}
	},
	"clientParty": {
		"id": "ABC12345",
		"name": "Client Name",
		"type": "Organisation",
		"contactDetails": {
			"email": "client@mail.com"
		}
	},
	"currentStatus": {
		"status": "Approved",
		"timestamp": 1508499660637,
		"updatedBy": "clientCredId"
	},
	"statusHistory": [{
		"status": "New",
		"timestamp": 1508499660637,
		"updatedBy": "credid"
	}],
	"subscription": {
		"service": {
			"id": "ated",
			"name": "ATED"
		}
	},
	"clientDisplayName": "client display name"
}
```

**Response**

 Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 404    | Not Found   |

 No body

### agent reject the clients with this mandate id
``` POST /agent/123456789/mandate/rejectClient/95D42795```

**Response**

| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 404    | Not Found   |
| 500   | Internal Server Error |

 No body

### agent removes client (break relationship)
``` POST /agent/123456789/mandate/remove/95D42795 ```

**Response**

 Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 404    | Not Found   |

 No body

### client removes agent (break relationship)
``` POST /org/987654321/mandate/remove/95D42795 ```

**Response**

 Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 404    | Not Found   |

 No body

### create relationship for non-uk clients by agent (self-authorised)
``` POST /agent/123456789/mandate/non-uk ```

**Request body**
```json
{
	"safeId": "safeId",
	"subscriptionReference": "atedRefNum",
	"service": "ated",
	"clientEmail": "aa@mail.com",
	"arn": "arn",
	"agentEmail": "bb@mail.com",
	"clientDisplayName": "client display name"
}
```

**Response**

 Status | Message     |
|--------|-------------|
| 201    | Created     |

### update an existing non-uk mandate
```POST /agent/123456789/mandate/non-uk/update``` 

**Request body**
```json
{
	"safeId": "safeId",
	"subscriptionReference": "atedRefNum",
	"service": "ated",
	"clientEmail": "aa@mail.com",
	"arn": "arn",
	"agentEmail": "bb@mail.com",
	"clientDisplayName": "client display name"
}
```

**Response**

 Status | Message     |
|--------|-------------|
| 201    | Created     |

### edit an existing mandate
```POST  /agent/123456789/mandate/edit``` 

**Request body**
```json
{
	"id": "AS12345678",
	"createdBy": {
		"credId": "credid",
		"name": "Joe Bloggs"
	},
	"agentParty": {
		"id": "JARN123456",
		"name": "Joe Bloggs",
		"type": "Organisation",
		"contactDetails": {
			"email": "",
			"phone": ""
		}
	},
	"currentStatus": {
		"status": "New",
		"timestamp": 1508499813255,
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
**Response**

| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 500    | Internal Server Error   |

```json
{
	"id": "AS12345678",
	"createdBy": {
		"credId": "credid",
		"name": "Joe Bloggs"
	},
	"agentParty": {
		"id": "JARN123456",
		"name": "Joe Bloggs",
		"type": "Organisation",
		"contactDetails": {
			"email": "",
			"phone": ""
		}
	},
	"currentStatus": {
		"status": "New",
		"timestamp": 1508499813255,
		"updatedBy": "credid"
	},
	"statusHistory": [],
	"subscription": {
		"service": {
			"id": "ated",
			"name": "ATED"
		}
	},
	"clientDisplayName": "client display name edited"
}
``` 
                
### fetch mandate for client
```GET  /org/987654321/mandate/:clientId/ATED```

**Response**

| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 404    | Not Found   |

```json
{
	"id": "AS12345678",
	"createdBy": {
		"credId": "credid",
		"name": "Joe Bloggs"
	},
	"agentParty": {
		"id": "JARN123456",
		"name": "Joe Bloggs",
		"type": "Organisation",
		"contactDetails": {
			"email": "",
			"phone": ""
		}
	},
	"currentStatus": {
		"status": "New",
		"timestamp": 1508499813255,
		"updatedBy": "credid"
	},
	"statusHistory": [],
	"subscription": {
		"service": {
			"id": "ated",
			"name": "ATED"
		}
	},
	"clientDisplayName": "client display name edited"
}
```         

### check for agents missing email in mandate
```GET  /agent/123456789/mandate/isAgentMissingEmail/JARN1234567/ATED``` 

**Response**

| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 204    | No Content  |

 No body

### update missing email for agent in mandate
```POST /agent/123456789/mandate/updateAgentEmail/JARN1234567/ATED```    

**Request body**

```json
{"emailAddress": "agentNewEmail@mail.com"}
```   

**Response**

 Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 400    | Bad Request |
| 500    | Internal Server Error |

 No body

### update client email in mandate
```POST /org/987654321/mandate/updateClientEmail/95D42795```    

**Request body**

```json
{"emailAddress": "clientNewEmail@mail.com"}
```   

**Response**

 Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 400    | Bad Request |
| 500    | Internal Server Error |

 No body  

### update agent credId in mandate
```POST /agent/123456789/mandate/updateAgentCredId``` 

**Request body**

```json
{"credId": "credId-new-111"}
```   

**Response**

 Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 400    | Bad Request |
| 500    | Internal Server Error |

 No body  

### get client friendly names where client cancelled within 28 days
```GET  /agent/123456789/mandate/clientCancelledNames/JARN1234567/ATED``` 

**Response**

| Status | Message     |
|--------|-------------|
| 200    | Ok          |
| 404    | Not Found   |

```json
[{
	"id": "AS12345678",
	"createdBy": {
		"credId": "credid",
		"name": "Joe Bloggs"
	},
	"agentParty": {
		"id": "JARN123456",
		"name": "Joe Bloggs",
		"type": "Organisation",
		"contactDetails": {
			"email": "",
			"phone": ""
		}
	},
	"currentStatus": {
		"status": "New",
		"timestamp": 1508499813255,
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
}]
```

## License

This code is open source software licensed under the [Apache 2.0 License].

[Apache 2.0 License]: http://www.apache.org/licenses/LICENSE-2.0.html
