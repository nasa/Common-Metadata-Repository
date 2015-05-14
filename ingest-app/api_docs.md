***

## API Overview

### Metadata Ingest API Overview

  * /providers/\<provider-id>/validate/collection/\<native-id>
    * [POST - Validate collection metadata.](#validate-collection)
  * /providers/\<provider-id>/collections/\<native-id>
    * [PUT - Create or update a collection.](#create-update-collection)
    * [DELETE - Delete a collection.](#delete-collection)
  * /providers/\<provider-id>/validate/granule/\<native-id>
    * [POST - Validate granule metadata.](#validate-granule)
  * /providers/\<provider-id>/granules/\<native-id>
    * [PUT - Create or update a granule.](#create-update-granule)
    * [DELETE - Delete a granule.](#delete-granule)

### Administrative API Overview

  * /providers
    * [GET - Get a list of providers.](#get-providers)
    * [POST - Create provider.](#create-provider)
  * /providers/\<provider-id>
    * [PUT - Update Provider.](#update-provider)
    * [DELETE - Delete a provider.](#delete-provider)
  * /jobs
    * [POST /jobs/pause - Pause all jobs](#pause-jobs)
    * [POST /jobs/resume - Resumes all jobs](#resume-jobs)
    * [GET /jobs/status - Gets pause/resume state of jobs](#jobs-status)
    * [POST /jobs/reindex-collection-permitted-groups - Runs the reindex collection permitted groups job.](#reindex-collection-permitted-groups)
    * [POST /jobs/reindex-all-collections - Runs to job to reindex all collections.](#reindex-all-collections)
    * [POST /jobs/cleanup-expired-collections - Runs the job to remove expired collections.](#cleanup-expired-collections)
  * /caches
    * [GET /caches - Gets a list of the caches in ingest.](#get-caches)
    * [GET /caches/\<cache-name> - Gets a list of the keys stored in the specific cache.](#get-cache-keys)
    * [GET /caches/\<cache-name>/\<cache-key> - Gets the value of the cache key in the specific cache](#get-cache-ialue)
    * [POST /caches/clear-cache - Clears the ingest caches.](#clear-cache)
  * /health
    * [GET - Gets the health of the ingest application.](#health)

***

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

#### <a name="revision-id-header"></a> Revision Id Header

The revision id header allows specifying the [revision id](#revision-id) to use when saving the concept. If the revision id specified is not the latest a HTTP Status code of 409 will be returned indicating a conflict.

#### <a name="concept-id-header"></a> Concept Id Header

The concept id header allows specifying the [concept id](#concept-id) to use when saving a concept. This should normally not be sent by clients. The CMR should normally generate the concept id.

***

### <a name="responses"></a> Responses

#### <a name="http-status-codes"></a> HTTP Status Codes

| Status Code |                                               Description                                                |
| ----------- | -------------------------------------------------------------------------------------------------------- |
|         200 | Success                                                                                                  |
|         201 | Success creating an entity                                                                               |
|         400 | Bad request. The body will contain errors.                                                               |
|         404 | Not found. This could be returned either because the URL isn't known by ingest or the item wasn't found. |
|         409 | Conflict. This is returned when a revision id conflict occurred while saving the item.                   |
|         500 | Internal error. Contact CMR Operations if this occurs.                                                   |
|         503 | Internal error because a service dependency is not available.                                            |

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

##### <a name="umm-ialidation-errors"></a> UMM Validation Errors

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

***


### <a name="cmr-ids"></a> CMR Ids

This documents different identifiers used in the CMR.

#### <a name="provider-id"></a> Provider Id

A provider id identifies a provider and is composed of a combination of upper case letters, digits, and underscores. Example: LPDAAC_ECS

#### <a name="native-id"></a> Native Id

The native id is the id that a provider client uses to refer to a granule or collection in the URL. For example a provider could create a new collection with native id "cloud_sat_5" in provider "PROV" by sending a HTTP PUT request to `/providers/PROV/collections/cloud_sat_5`. The native id must be unique within a provider. Two collections could not share a native id for example. The native id doesn't have to matche an id in the metadata but providers are encouraged to use something like entry id or entry title for their native ids.

#### <a name="revision-id"></a> Revision Id

Every update or deletion of a concept is stored separately as a separate revision in the CMR database. Deletion revisions are called tombstones. The CMR uses this to improve caching, synchronization, and to maintain an audit log of changes to concepts. Every revision is given a separate id starting with 1 for the first revision.

##### Example Revision Ids

Here's a table showing an example set of revisions for one collection.

| Concept Id | Revision Id | Metadata | Deleted |
| ---------- | ----------- | -------- | ------- |
| C1-PROV1   |           1 | ...      | false   |
| C1-PROV1   |           2 | ...      | false   |
| C1-PROV1   |           3 | null     | true    |
| C1-PROV1   |           4 | ...      | false   |

The table shows one collection with 4 revisions. It was created and then updated. The third revision was a deletion. The last revision was when the collection was recreated.

#### <a name="concept-id"></a> Concept Id

A concept is any type of metadata that is managed by the CMR. Collections and granules are the current concept types the CMR manages. The concept id is the unique identifier of concepts in the CMR.

The format of the concept id is:

    <letter> <unique-number> "-" <provider-id>

An example concept id is C179460405-LPDAAC_ECS. The letter identifies the concept type. G is for granule. C is for collection. The [provider id](#provider-id) is the upper case unique identifier for a provider.

***

## <a name="metadata-ingest"></a> Metadata Ingest

### <a name="validate-collection"></a> Validate Collection

Collection metadata can be validated without having to ingest it. The validation performed is schema validation, UMM validation, and inventory specific validations. It returns status code 200 on successful validation, status code 400 with a list of validation errors on failed validation.

```
curl -i -XPOST -H "Content-type: application/echo10+xml" -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1/validate/collection/sampleNativeId15 -d \
"<Collection>
  <ShortName>ShortName_Larc</ShortName>
  <VersionId>Version01</VersionId>
  <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
  <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
  <DeleteTime>2015-05-23T22:30:59</DeleteTime>
  <LongName>LarcLongName</LongName>
  <DataSetId>LarcDatasetId</DataSetId>
  <Description>A minimal valid collection</Description>
  <Orderable>true</Orderable>
  <Visible>true</Visible>
</Collection>"
```


### <a name="create-update-collection"></a> Create / Update a Collection

Collection metadata can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/providers/<provider-id>/collections/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id).

```
curl -i -XPUT -H "Content-type: application/echo10+xml" -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1/collections/sampleNativeId15 -d \
"<Collection>
  <ShortName>ShortName_Larc</ShortName>
  <VersionId>Version01</VersionId>
  <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
  <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
  <DeleteTime>2015-05-23T22:30:59</DeleteTime>
  <LongName>LarcLongName</LongName>
  <DataSetId>LarcDatasetId</DataSetId>
  <Description>A minimal valid collection</Description>
  <Orderable>true</Orderable>
  <Visible>true</Visible>
</Collection>"
```

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>C1200000000-PROV1</concept-id>
  <revision-id>1</revision-id>
</result>
```

#### Successful Response in JSON

```
{"concept-id":"C1200000000-PROV1","revision-id":1}
```

### <a name="delete-collection"></a> Delete a Collection

Collection metadata can be deleted by sending an HTTP DELETE the URL `%CMR-ENDPOINT%/providers/<provider-id>/collections/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

    curl -i -XDELETE -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1/collections/sampleNativeId15

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>C1200000000-PROV1</concept-id>
  <revision-id>2</revision-id>
</result>
```

#### Successful Response in JSON

```
{"concept-id":"C1200000000-PROV1","revision-id":2}
```

***

### <a name="validate-granule"></a> Validate Granule

Granule metadata can be validated without having to ingest it. The validation performed is schema validation, UMM validation, and inventory specific validations. It returns status code 200 on successful validation, status code 400 with a list of validation errors on failed validation.

A collection is required when validating the granule. The granule being validated can either refer to an existing collection in the CMR or the collection can be sent in a multi-part HTTP request.

#### Validate Granule Referencing Existing Collection

This shows how to validate a granule that references an existing collection in the database.

```
curl -i -XPOST -H "Content-type: application/echo10+xml" -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1/validate/granule/sampleGranuleNativeId33 -d \
"<Granule>
   <GranuleUR>SC:AE_5DSno.002:30500511</GranuleUR>
   <InsertTime>2009-05-11T20:09:16.340Z</InsertTime>
   <LastUpdate>2014-03-19T09:59:12.207Z</LastUpdate>
   <Collection>
     <DataSetId>LarcDatasetId</DataSetId>
   </Collection>
   <Orderable>true</Orderable>
</Granule>"
```

#### Validate Granule With Parent Collection

Granule validation also allows the parent collection to be sent along with the granule as well. This allows validation of a granule that may not have a parent collection ingested. The granule and collection XML are sent over HTTP using form multi-part parameters. The collection and granule XML are specified with the parameter names "collection" and "granule".

Here's an example of validating a granule along with the parent collection using curl. The granule is in the granule.xml file and collection is in collection.xml.

    curl -i -XPOST -H "Echo-Token: XXXX" \
    -F "granule=<granule.xml;type=application/echo10+xml" \
    -F "collection=<collection.xml;type=application/echo10+xml" \
    "%CMR-ENDPOINT%/providers/PROV1/validate/granule/sampleGranuleNativeId33"

### <a name="create-update-granule"></a> Create / Update a Granule

Granule metadata can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/providers/<provider-id>/granules/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id).

    curl -i -XPUT -H "Content-type: application/echo10+xml" -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1/granules/sampleGranuleNativeId33 -d \
    "<Granule>
       <GranuleUR>SC:AE_5DSno.002:30500511</GranuleUR>
       <InsertTime>2009-05-11T20:09:16.340Z</InsertTime>
       <LastUpdate>2014-03-19T09:59:12.207Z</LastUpdate>
       <Collection>
         <DataSetId>LarcDatasetId</DataSetId>
       </Collection>
       <Orderable>true</Orderable>
    </Granule>"

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>G1200000001-PROV1</concept-id>
  <revision-id>1</revision-id>
</result>
```

#### Successful Response in JSON

```
{"concept-id":"G1200000001-PROV1","revision-id":1}
```
### <a name="delete-granule"></a> Delete a Granule

Granule metadata can be deleted by sending an HTTP DELETE the URL `%CMR-ENDPOINT%/providers/<provider-id>/granules/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

    curl -i -XDELETE -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1/granules/sampleGranuleNativeId33

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>G1200000001-PROV1</concept-id>
  <revision-id>2</revision-id>
</result>
```

#### Successful Response in JSON

```
{"concept-id":"G1200000001-PROV1","revision-id":2}
```

***

## <a name="administrative-api"></a> Administrative API

### <a name="providers"></a> Providers

The providers that exist in the CMR are administered through the Ingest API. A provider consists of the following fields

 * `provider-id` - The alpha numeric upper case string identifying the provider. See [provider id](#provider-id).
 * `cmr-only` - True or false value that indicates if this is a provider that ingests directly through the CMR Ingest API or the legacy ECHO Catalog REST Ingest API. A CMR Only provider will still have ACLs configured in ECHO and support ordering through ECHO. A CMR Only provider may even still have data in Catalog REST but it will not be kept in sync with the CMR. `cmr-only` defaults to false.

The provider API only supports requests and responses in JSON.

#### <a name="get-providers"></a> Get Providers

Returns a list of the configured providers in the CMR.

```
curl %CMR-ENDPOINT%/providers

[{"provider-id":"PROV2","cmr-only":true},{"provider-id":"PROV1","cmr-only":false}]
```

#### <a name="create-provider"></a> Create Provider

Creates a provider in the CMR. The provider id specified should match that of a provider configured in ECHO.

```
curl -i -XPOST -H "Content-Type: application/json" -H "Echo-Token: XXXX" %CMR-ENDPOINT/providers -d \
'{"provider-id": "PROV1", "cmr-only": false}'
```

#### <a name="update-provider"></a> Update Provider

Updates the attributes of a provider in the CMR.

```
curl -i -XPUT -H "Content-Type: application/json" -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1 -d \
'{"provider-id": "PROV1", "cmr-only":true}'
```

#### <a name="delete-provider"></a> Delete Provider

Removes a provider from the CMR. Deletes all data for the provider in Metadata DB and unindexes all data in Elasticsearch.

curl -i -XDELETE -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1

***

### <a name="caches"></a> Caches

The caches of the ingest application can be queries to help debug caches issues in the system. Endpoints are provided for querying the contents of the various caches used by the application.

The following curl will return the list of caches:

    curl -i %CMR-ENDPOINT/caches

The following curl will return the keys for a specific cache:

    curl -i %CMR-ENDPOINT/caches/<cache-name>

This curl will return the value for a specific key in the named cache:

    curl -i %CMR-ENDPOINT/caches/<cache-name>/<cache-key>

***

### <a name="application-health"></a> Application Health


This will report the current health of the application. It checks all resources and services used by the application and reports their health status in the response body in JSON format. The report includes an "ok?" status and a "problem" field for each resource. The report includes an overall "ok?" status and health reports for each of a service's dependencies. It returns HTTP status code 200 when the application is healthy, which means all its interfacing resources and services are healthy; or HTTP status code 503 when one of the resources or services is not healthy. It also takes pretty parameter for pretty printing the response.

    curl -i -XGET %CMR-ENDPOINT%/health?pretty=true

Example healthy response body:

```
{
  "oracle" : {
    "ok?" : true
  },
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
  },
  "indexer" : {
    "ok?" : true,
    "dependencies" : {
      "elastic_search" : {
        "ok?" : true
      },
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
      },
      "index-set" : {
        "ok?" : true,
        "dependencies" : {
          "elastic_search" : {
            "ok?" : true
          },
          "echo" : {
            "ok?" : true
          }
        }
      }
    }
  }
}
```

Example unhealthy response body:

```
{
  "oracle" : {
    "ok?" : false,
    "problem" : "Exception occurred while getting connection: oracle.ucp.UniversalConnectionPoolException: Cannot get Connection from Datasource: java.sql.SQLRecoverableException: IO Error: The Network Adapter could not establish the connection"
  },
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
  },
  "indexer" : {
    "ok?" : true,
    "dependencies" : {
      "elastic_search" : {
        "ok?" : true
      },
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
      },
      "index-set" : {
        "ok?" : true,
        "dependencies" : {
          "elastic_search" : {
            "ok?" : true
          },
          "echo" : {
            "ok?" : true
          }
        }
      }
    }
  }
}
```

***

### <a name="jobs"></a> Jobs

Ingest has internal jobs that run. They can be run manually and controlled through the Jobs API.

#### <a name="pause-jobs"></a> Pause Jobs


    curl -i -XPOST -H "Echo-Token: XXXX" %CMR-ENDPOINT%/jobs/pause

### <a name="resume-jobs"></a> Resume ingest scheduled jobs


    curl -i -XPOST -H "Echo-Token: XXXX" %CMR-ENDPOINT%/jobs/resume

### <a name="reindex-collection-permitted-groups"></a> Run Reindex Collections Permitted Groups Job

Collections which ACLs have changed can be reindexed by sending the following request.

    curl -i -XPOST -H "Echo-Token: XXXX" %CMR-ENDPOINT%/jobs/reindex-collection-permitted-groups

### <a name="reindex-all-collections"></a> Run Reindex All Collections Job

Reindexes every collection in every provider.

    curl -i -XPOST -H "Echo-Token: XXXX" %CMR-ENDPOINT%/jobs/reindex-all-collections

### <a name="cleanup-expired-collections"></a> Run Cleanup Expired Collections Job

Looks for collections that have a delete date in the past and removes them.

    curl -i -XPOST -H "Echo-Token: XXXX" %CMR-ENDPOINT%/jobs/cleanup-expired-collections



