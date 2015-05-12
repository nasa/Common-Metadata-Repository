## API Overview

### Metadata Ingest API Overview

  * /providers/\<provider-id>/validate/collection/\<native-id>
    * [POST](#validate-collection) - Validate collection metadata.
  * /providers/\<provider-id>/collections/\<native-id>
    * [PUT](#create-update-collection) - Create or update a collection.
    * [DELETE](#delete-collection) - Delete a collection.
  * /providers/\<provider-id>/validate/granule/\<native-id>
    * [POST](#validate-granule) - Validate granule metadata.
  * /providers/\<provider-id>/granules/\<native-id>
    * [PUT](#create-update-granule) - Create or update a granule.
    * [DELETE](#delete-granule) - Delete a granule.

### Administrative API Overview

  * /providers
    * [GET](#get-providers) - Get a list of providers.
    * [POST](#create-provider) - Create provider.
  * /providers/\<provider-id>
    * [PUT](#update-provider) - Update Provider.
    * [DELETE](#delete-provider) - Delete a provider.
  * /jobs
    * [POST /jobs/pause](#pause-jobs) - Pause all jobs
    * [POST /jobs/resume](#resume-jobs) - Resumes all jobs
    * [GET /jobs/status](#jobs-status) - Gets pause/resume state of jobs
    * [POST /jobs/reindex-collection-permitted-groups](#reindex-collection-permitted-groups) - Runs the reindex collection permitted groups job.
    * [POST /jobs/reindex-all-collections](#reindex-all-collections) - Runs to job to reindex all collections.
    * [POST /jobs/cleanup-expired-collections](#cleanup-expired-collections) - Runs the job to remove expired collections.
  * /caches
    * [GET /caches](#get-caches) - Gets a list of the caches in ingest.
    * [GET /caches/\<cache-name>](#get-cache-keys) - Gets a list of the keys stored in the specific cache.
    * [GET /caches/\<cache-name>/\<cache-key>](#get-cache-value) - Gets the value of the cache key in the specific cache
    * [POST /caches/clear-cache](#clear-cache) - Clears the ingest caches.
  * /health
    * [GET](#health) - Gets the health of the ingest application.


## <a name="api-conventions"></a> API Conventions

This defines conventions used across the Ingest API.

### <a name="headers"></a> Headers

This defines common headers on the ingest API.

#### <a name="content-type-header"></a> Content-Type Header

Content-Type is a standard HTTP header that specifies the content type of the body of the request. Ingest supports the following content types for ingesting metadata.

|       Content-Type       |    Description    |    Concept Types    |
| ------------------------ | ----------------- | ------------------- |
| application/dif10+xml    | DIF 10            | collection          |
| application/dif+xml      | DIF 9             | collection          |
| application/echo10+xml   | ECHO 10           | collection, granule |
| application/iso19115+xml | ISO 19115 (MENDS) | collection, granule |
| application/iso:smap+xml | ISO 19115 SMAP    | collection, granule |


#### <a name="echo-token-header"></a> Echo-Token Header

All Ingest API operations require specifying a token obtained from URS or ECHO. The token should be specified using the `Echo-Token` header.

#### <a name="accept-header"></a> Accept Header

The Accept header specifies the format of the response message. The Accept header will default to XML for the normal Ingest APIs. `application/json` can be specified if you prefer responses in JSON.

### <a name="responses"></a> Responses

#### <a name="http-status-codes"></a> HTTP Status Codes

| Status Code |                                               Description                                                |
| ----------- | -------------------------------------------------------------------------------------------------------- |
|         200 | Success                                                                                                  |
|         201 | Success creating an entity                                                                               |
|         400 | Bad request. The body will contain errors.                                                               |
|         404 | Not found. This could be returned either because the URL isn't known by ingest or the item wasn't found. |
|         500 | Internal error. Contact CMR Operations if this occurs.                                                   |

#### <a name="successful-responses"></a> Successful Responses

Successful ingest responses will return an HTTP Status code of 200 or 201 and a body containing the [CMR Concept Id](#concept-id) of the item that was updated or deleted along with the [revision id](#revision-id).

    {"concept-id":"C12345-PROV","revision-id":1}

#### <a name="error-response"></a> Error Responses

Requests could fail for several reasons when communicating with the CMR as described in the [HTTP Status Codes](#http-status-codes).

##### <a name="general-errors"></a> General Errors

Ingest validation errors can take one of two shapes. General error messages will be returned as a list of error messages like the following:

```
<errors>
   <error>Parent collection for granule [SC:AE_5DSno.002:30500511] does not exist.</error>
</errors>
```

##### <a name="umm-validation-errors"></a> UMM Validation Errors

UMM Validation errors will be returned with a path within the metadata to the failed item. For example the following errors would be returned if the first and second spatial areas were invalid. The path is a set of UMM fields in camel case separated by a `/`. Numeric indices are used to indicate the index of an item within a list that failed.

```
<errors>
   <error>
      <path>SpatialCoverage/Geometries/1</path>
      <errors>
         <error>Spatial validation error: The shape contained duplicate points. Points 2 [lon=180 lat=-90] and 3 [lon=180 lat=-90] were considered equivalent or very close.</error>
      </errors>
   </error>
   <error>
      <path>SpatialCoverage/Geometries/0</path>
      <errors>
         <error>Spatial validation error: The polygon boundary points are listed in the wrong order (clockwise vs counter clockwise). Please see the API documentation for the correct order.</error>
      </errors>
   </error>
</errors>
```

Error messages can also be returned in JSON by setting the Accept header to application/json.

```
{
  "errors" : [ {
    "path" : [ "Platforms", 1, "Instruments", 1, "Sensors" ],
    "errors" : [ "Sensors must be unique. This contains duplicates named [S2]." ]
  }, {
    "path" : [ "Platforms", 1, "Instruments", 0, "Sensors" ],
    "errors" : [ "Sensors must be unique. This contains duplicates named [S1]." ]
  }, {
    "path" : [ "Platforms", 1, "Instruments" ],
    "errors" : [ "Instruments must be unique. This contains duplicates named [I1]." ]
  }, {
    "path" : [ "Platforms" ],
    "errors" : [ "Platforms must be unique. This contains duplicates named [P1]." ]
  } ]
}
```

### <a name="cmr-ids"></a> CMR Ids

#### <a name="concept-id"></a> Provider Id

#### <a name="native-id"></a> Native Id

#### <a name="revision-id"></a> Revision Id

#### <a name="concept-id"></a> Concept Id



## <a name="metadata-ingest"></a> Metadata Ingest

### <a name="validate-collection"></a> Validate Collection

TODO

### <a name="create-update-collection"></a> Create / Update a Collection

Create and update is done through a PUT

TODO show sample output

### <a name="delete-collection"></a> Delete a Collection

TODO


### <a name="validate-granule"></a> Validate Granule

TODO

### <a name="create-update-granule"></a> Create / Update a Granule

TODO show sample output

### <a name="delete-granule"></a> Delete a Granule

TODO


## <a name="administrative-api"></a> Administrative API

### <a name="providers"></a> Providers

#### <a name="get-providers"></a> Get Providers

#### <a name="create-provider"></a> Create Provider

#### <a name="update-provider"></a> Update Provider

#### <a name="delete-provider"></a> Delete Provider


TODO

### <a name="caches"></a> Caches

TODO

### <a name="application-health"></a> Application Health

TODO

### <a name="jobs"></a> Jobs