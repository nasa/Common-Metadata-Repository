***

### Access Control API Overview

TODO document create user

  * /foo/bar
    * [POST - Sample Description](#sample-link)

***

## <a name="api-conventions"></a> API Conventions

This defines conventions used across the Access Control API.

### <a name="headers"></a> Headers

TODO describe headers

***

### <a name="responses"></a> Responses

### <a name="response-headers"></a> Response Headers

TODO describe response headers

#### <a name="http-status-codes"></a> HTTP Status Codes

TODO update this table for this application.

| Status Code |                                               Description                                                                          |
| ----------- | -----------------------------------------------------------------------------------------------------------------------------------|
|         200 | Success                                                                                                                            |
|         400 | Bad request. The body will contain errors.                                                                                         |
|         404 | Not found. This could be returned either because the URL isn't known by ingest or the item wasn't found.                           |
|         409 | Conflict. This is returned when a revision id conflict occurred while saving the item.                                             |
|         415 | Unsupported Media Type. The body will return an error message that contains the list of supported ingest formats.                  |
|         422 | Unprocessable entity. Ingest understood the request, but the concept failed ingest validation rules. The body will contain errors. |
|         500 | Internal error. Contact CMR Operations if this occurs.                                                                             |
|         503 | Internal error because a service dependency is not available.                                                                      |



## <a name="sample-link"></a> Foo Bar

TODO write real examples