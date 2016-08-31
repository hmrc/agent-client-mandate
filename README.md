# agent-client-mandate

[![Build Status](https://travis-ci.org/hmrc/agent-client-mandate.svg)](https://travis-ci.org/hmrc/agent-client-mandate) [ ![Download](https://api.bintray.com/packages/hmrc/releases/agent-client-mandate/images/download.svg) ](https://bintray.com/hmrc/releases/agent-client-mandate/_latestVersion)

### POST /agent-client-mandate/mandate

Used to create a new Client Mandate

| Status | Message
| 200    | Ok
| 400    | Bad Request

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

** Response body **

```json
{
  "mandateId": "AS12345678"
}



### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
