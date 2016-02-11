## API Documentation

***

See the [CMR Data Partner User Guide](https://wiki.earthdata.nasa.gov/display/CMR/CMR+Data+Partner+User+Guide) for a general guide to utilizing the CMR Ingest API as a data partner.
See the [CMR Client Partner User Guide](https://wiki.earthdata.nasa.gov/display/CMR/CMR+Client+Partner+User+Guide) for a general guide to developing a CMR client.
Join the [CMR Client Developer Forum](https://wiki.earthdata.nasa.gov/display/CMR/CMR+Client+Developer+Forum) to ask questions, make suggestions and discuss topics like future CMR capabilities.

### Access Control API Overview

  * [/groups](#groups)
    * [POST - Create Group](#create-group)
    * [GET - Search Groups](#search-groups)
  * /groups/:group-id
    * [GET - Retrieve a group](#retrieve-group)
    * [PUT - Update a group](#update-group)
    * [DELETE - Delete a group](#delete-group)
  * /groups/:group-id/members
    * [GET - Retrieve group members] (#retrieve-group-members)
    * [POST - Add group members] (#add-group-members)
    * [DELETE - Remove group members] (#remove-group-members)
  * /health
    * [GET - Get the health of the access control application.](#application-health)

***

## <a name="api-conventions"></a> API Conventions

This defines conventions used across the Access Control API.

### <a name="headers"></a> Headers

This defines common headers on the Access Control API.

#### <a name="content-type-header"></a> Content-Type Header

Content-Type is a standard HTTP header that specifies the content type of the body of the request. Access Control only supports `application/json` as the representation type for data.

#### <a name="echo-token-header"></a> Echo-Token Header

All Access Control API operations require specifying a token obtained from URS or ECHO. The token should be specified using the `Echo-Token` header.

***

### <a name="responses"></a> Responses

### <a name="response-headers"></a> Response Headers

#### <a name="CMR-Request-Id-header"></a> cmr-request-id

This header returns the unique id assigned to the request. This can be used to help debug client errors. The value is a long string of the form

    828ef0b8-a876-4579-85db-3cc9d1b5f6e5

#### <a name="http-status-codes"></a> HTTP Status Codes

| Status Code | Description                                                                                                                                |
|-------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| 200         | Success                                                                                                                                    |
| 400         | Bad request. The body will contain errors.                                                                                                 |
| 404         | Not found. This could be returned either because the URL isn't known by access control or the item wasn't found.                           |
| 409         | Conflict. This is returned when a revision id conflict occurred while saving the item.                                                     |
| 415         | Unsupported Media Type. The body will return an error message that contains the list of supported formats.                                 |
| 422         | Unprocessable entity. Access control understood the request, but the concept failed ingest validation rules. The body will contain errors. |
| 500         | Internal error. Contact CMR Operations if this occurs.                                                                                     |
| 503         | Internal error because a service dependency is not available.                                                                              |

#### <a name="successful-responses"></a> Successful Responses

Successful responses will return an HTTP Status code of 200 and a body containing the CMR Concept Id of the item that was updated or deleted along with the revision id.

    {"concept-id":"AG12345-PROV","revision-id":1}

## <a name="groups"></a> Groups

Groups are used to identify sets of users for the assignment of access privileges. Groups are either owned by a provider or the system. Provider level groups define sets of users access to that providers data. System level groups define sets of users for assigning system level access. Groups have the following fields:

* `name` - Required field that uniquely identifies a system group or a group within a provider.
* `provider-id` - Id of the provider that owns the group. If this isn't present then the group will be a system level group.
* `description` - Required field that describes the group.
* `legacy-guid` - Internal use only. This is used for ECHO Kernel interoperability.

### <a name="create-group"></a> Create Group

Groups are created by POSTing a JSON representation of a group to `%CMR-ENDPOINT%/groups` along with a valid ECHO token. The response will contain a concept id identifying the group along with the group revision id.

#### Creating a System Level Group

```
curl -XPOST -i -H "Content-Type: application/json" -H "Echo-Token: XXXXX" %CMR-ENDPOINT%/groups -d \
'{
  "name": "Administrators",
  "description": "The group of users that manages the CMR."
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1

{"revision-id":1,"concept-id":"AG1200000000-CMR"}
```

#### Creating a Provider Level Group

```
curl -XPOST -i -H "Content-Type: application/json" -H "Echo-Token: XXXXX" %CMR-ENDPOINT%/groups -d \
'{
  "name": "Administrators",
  "provider-id": "PROV1"
  "description": "The group of users that manages PROV1s data holdings."
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1

{"revision-id":1,"concept-id":"AG1200000001-PROV1"}
```

### <a name="retrieve-group"></a> Retrieve Group

A single group can be retrieved by sending a GET request to `%CMR-ENDPOINT%/groups/<concept-id>` where `concept-id` is the concept id of the group returned when it was created.

```
curl -i -H "Echo-Token: XXXX" %CMR-ENDPOINT%/groups/AG1200000000-CMR?pretty=true

HTTP/1.1 200 OK
Content-Type: application/json

{
  "name" : "Administrators",
  "description" : "The group of users that manages PROV1s data holdings."
}
```

### <a name="update-group"></a> Update Group

Groups are updated by sending a PUT request with the JSON representation of a group to `%CMR-ENDPOINT%/groups/<concept-id>` where `concept-id` is the concept id of the group returned when it was created. The same rules apply when updating a group as when creating it but only the description can be modified. The response will contain the concept id along with the group revision id.

```
curl -XPUT -i -H "Content-Type: application/json" -H "Echo-Token: XXXXX" %CMR-ENDPOINT%/groups/AG1200000000-CMR -d \
'{
  "name": "Administrators",
  "description": "The group of users that manages the CMR and related systems."
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1

{"concept-id":"AG1200000000-CMR","revision-id":2}
```

### <a name="deleted-group"></a> Delete Group

Groups are deleted by sending a DELETE request to `%CMR-ENDPOINT%/groups/<concept-id>` where `concept-id` is the concept id of the group returned when it was created. Deleting a group creates a tombstone that marks the group as deleted. The concept id of the group and the revision id of the tombstone are returned from a delete request.

```
curl -XDELETE -i  -H "Echo-Token: XXXXX" %CMR-ENDPOINT%/groups/AG1200000000-CMR

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1

{"concept-id":"AG1200000000-CMR","revision-id":3}
```

### <a name="retrieve-group-members"></a> Retrieve Group Members

The members in a group can be retrieved by sending a GET request to `%CMR-ENDPOINT%/groups/<concept-id>/members` where `concept-id` is the concept id of the group returned when it was created.

```
curl -i -H "Echo-Token: XXXX" %CMR-ENDPOINT%/groups/AG1200000000-CMR/members?pretty=true

HTTP/1.1 200 OK
Content-Type: application/json

["user1", "user2", "user3"]
```

### <a name="add-group-members"></a> Add Group Members

The members in a group can be added by sending a POST request to `%CMR-ENDPOINT%/groups/<concept-id>/members` where `concept-id` is the concept id of the group returned when it was created. The body of the request should be a JSON array of Earthdata Login usernames. The concept id and updated revision number is returned.

```
curl -i -XPOST -H "Echo-Token: XXXX" -H "Content-Type application/json" %CMR-ENDPOINT%/groups/AG1200000000-CMR/members -d
'["user1", "user2", "user3"]'

HTTP/1.1 200 OK
Content-Type: application/json

{"concept-id":"AG1200000000-CMR","revision-id":3}
```

### <a name="remove-group-members"></a> Remove Group Members

The members in a group can be removed by sending a DELETE request to `%CMR-ENDPOINT%/groups/<concept-id>/members` where `concept-id` is the concept id of the group returned when it was created. The body of the request should be a JSON array of Earthdata Login usernames to remove. The concept id and updated revision number is returned.

```
curl -i -XDELETE -H "Echo-Token: XXXX" -H "Content-Type application/json" %CMR-ENDPOINT%/groups/AG1200000000-CMR/members -d
'["user1", "user2", "user3"]'

HTTP/1.1 200 OK
Content-Type: application/json

{"concept-id":"AG1200000000-CMR","revision-id":4}
```

### <a name="search-groups"></a> Search Groups

Groups can be searched for by sending a GET request to `%CMR-ENDPOINT%/groups`

##### Group Search Parameters

The following parameters are supported when searching for groups.

##### Standard Parameters:

* page_size
* page_num
* pretty

##### Group Matching Parameters

These parameters will match fields within a group. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `provider[]=PROV1&provider[]=PROV2`.

* provider
  * options: ignore_case, pattern
  * This allows searching for either system level groups or groups owned by a specific provider. Use the provider id of "CMR" to search for system level groups.

##### Group Search Response

The response is always returned in JSON and includes the following parts.

* hits - How many total groups were found.
* took - How long the search took in milliseconds
* items - a list of the current page of groups with the following fields
  * concept-id
  * revision-id
  * name
  * description
  * provider-id - if a provider level group
  * member-count - The number of users in the group

##### Group Search Example

```
curl -i %CMR-ENDPOINT%/groups?pretty=true

HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
CMR-Hits: 4
CMR-Took: 5
CMR-Request-Id: 5689303f-574d-4edf-b2f1-5219dc0ae6c5
Content-Length: 702

{
  "hits" : 4,
  "took" : 5,
  "items" : [ {
    "member-count" : 2,
    "name" : "Administrators",
    "description" : "na",
    "revision-id" : 2,
    "concept-id" : "AG1200000002-PROV1",
    "provider-id" : "PROV1"
  }, {
    "name" : "Administrators",
    "member-count" : 2,
    "description" : "na",
    "revision-id" : 2,
    "concept-id" : "AG1200000000-CMR"
  }, {
    "name" : "Data Readers",
    "member-count" : 2,
    "description" : "na",
    "revision-id" : 2,
    "concept-id" : "AG1200000001-CMR"
  } ]
}
```


### <a name="application-health"></a> Application Health


This will report the current health of the application. It checks all resources and services used by the application and reports their health status in the response body in JSON format. The report includes an "ok?" status and a "problem" field for each resource. The report includes an overall "ok?" status and health reports for each of a service's dependencies. It returns HTTP status code 200 when the application is healthy, which means all its interfacing resources and services are healthy; or HTTP status code 503 when one of the resources or services is not healthy. It also takes pretty parameter for pretty printing the response.

    curl -i -XGET %CMR-ENDPOINT%/health?pretty=true

Example healthy response body:

```
{
  "echo" : {
    "ok?" : true
  },
  "metadata-db" : {
    "ok?" : true,
    "dependencies" : {
      "oracle" : {
        "ok?" : true
      },
      "echo" : {
        "ok?" : true
      }
    }
  }
}
```

Example unhealthy response body:

```
{
  "echo" : {
    "ok?" : true
  },
  "metadata-db" : {
    "ok?" : false,
    "dependencies" : {
      "oracle" : {
        "ok?" : false,
        "problem" : "Exception occurred while getting connection: oracle.ucp.UniversalConnectionPoolException: Cannot get Connection from Datasource: java.sql.SQLRecoverableException: IO Error: The Network Adapter could not establish the connection"
      },
      "echo" : {
        "ok?" : true
      }
    }
  }
}
```
