# cmr-ingest-app

This is the ingest component of the CMR system. It is responsible for collaborating with metadata db and indexer components of the CMR system to maintain the lifecycle of concepts coming into the system.

## Setting up the database

There are two ways database operations can be done. It can happen through leiningen commands for local development or using the built uberjar.

### Leiningen commands

1. Create the user

```
lein create-user
```

2. Run the migration scripts

```
lein migrate
```

You can use `lein migrate -version version` to restore the database to
a given version. `lein migrate -version 0` will clean the datbase
completely.

3. Remove the user

```
lein drop-user
```

### Java commands through uberjar

1. Create the user

```
CMR_DB_URL=thin:@localhost:1521:orcl CMR_INGEST_PASSWORD=****** java -cp target/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar cmr.db create-user
```

2. Run db migration

```
CMR_DB_URL=thin:@localhost:1521:orcl CMR_INGEST_PASSWORD=****** java -cp target/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar cmr.db migrate
```

You can provider additional arguments to migrate the database to a given version as in lein migrate.

3. Remove the user

```
CMR_DB_URL=thin:@localhost:1521:orcl CMR_INGEST_PASSWORD=****** java -cp target/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar cmr.db drop-user
```

## Ingest API

### Create provider

Creates a provider in the CMR. The provider id specified should match that of a provider configured in ECHO. The `cmr-only` parameter indicates if this is a provider that has ingest directly through the CMR. A CMR Only provider will still have ACLs configured in ECHO and support ordering through ECHO. A CMR Only provider may even still have data in Catalog REST but it will not be kept in sync with the CMR. `cmr-only` defaults to false.

    curl -v -XPOST -H "Content-Type: application/json" -H "Echo-Token: mock-echo-system-token" -d '{"provider-id": "PROV1", "cmr-only": false}' http://localhost:3002/providers

### Delete provider

Removes a provider from the CMR. Deletes all data for the provider in Metadata DB and unindexes all data in Elasticsearch

    curl -v -XDELETE -H "Echo-Token: mock-echo-system-token" http://localhost:3002/providers/PROV1

### Get providers

Returns a list of the configured providers in the CMR.

    curl http://localhost:3002/providers

    [{"provider-id":"PROV2","cmr-only":true},{"provider-id":"PROV1","cmr-only":false}]

### Create concept

    curl -i -v  -X PUT -H "Content-Type: application/echo10+xml" -H "Accept:application/json" --data \
"<Collection> <ShortName>ShortName_Larc</ShortName> <VersionId>Version01</VersionId> <InsertTime>1999-12-31T19:00:00-05:00</InsertTime> <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate> <DeleteTime>2015-05-23T22:30:59</DeleteTime><LongName>LarcLongName</LongName> <DataSetId>LarcDatasetId</DataSetId> <Description>A minimal valid collection</Description> <Orderable>true</Orderable> <Visible>true</Visible> </Collection>"  \
http://localhost:3002/providers/PROV1/collections/nativeId8

sample output:
{"concept-id":"C12-CurlPROV009","revision-id":0}

### Delete concept

    curl -i -v -XDELETE -H "Content-Type: application/json" http://localhost:3002/providers/CurlPROV009/collections/nativeId8

sample output:
{"concept-id":"C12-CurlPROV009","revision-id":1}

### Validate Collection

Validates collection metadata by performing schema validation, UMM validation, and inventory specific validations. Returns status code 200 on successful validation, status code 400 with a list of validation errors on failed validation.

    curl -i -XPOST -H "Content-type: application/echo10+xml" http://localhost:3002/providers/PROV1/validate/collection/sampleNativeId15 -d \
    "<Collection> \
      <ShortName>ShortName_Larc</ShortName> \
      <VersionId>Version01</VersionId> \
      <InsertTime>1999-12-31T19:00:00-05:00</InsertTime> \
      <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate> \
      <DeleteTime>2015-05-23T22:30:59</DeleteTime> \
      <LongName>LarcLongName</LongName> \
      <DataSetId>LarcDatasetId</DataSetId> \
      <Description>A minimal valid collection</Description> \
      <Orderable>true</Orderable> \
      <Visible>true</Visible> \
      </Collection>"

### Validate Granule

Validates granule metadata by performing schema validation, UMM validation, and inventory specific validations. Returns status code 200 on successful validation, status code 400 with a list of validation errors on failed validation.

    curl -i -XPOST -H "Content-type: application/echo10+xml" http://localhost:3002/providers/PROV1/validate/granule/sampleGranuleNativeId33 -d \
    "<Granule> \
        <GranuleUR>SC:AE_5DSno.002:30500511</GranuleUR> \
        <InsertTime>2009-05-11T20:09:16.340Z</InsertTime> \
        <LastUpdate>2014-03-19T09:59:12.207Z</LastUpdate> \
        <Collection> \
        <DataSetId>AMSR-E/Aqua 5-Day L3 Global Snow Water Equivalent EASE-Grids V002</DataSetId> \
        </Collection> \
        <Orderable>true</Orderable> \
    </Granule>"

#### Validate Granule With Parent Collection

Granule validation also allows the parent collection to be sent along with the granule as well. This allows validation of a granule that may not have a parent collection ingested. The granule and collection XML are sent over HTTP using form multipart parameters. The collection and granule XML are specified with the parameter names "collection" and "granule".

Here's an example of validating a granule along with the parent collection using curl. The granule is in the granule.xml file and collection is in collection.xml.

    curl -i -XPOST \
    -F "granule=<granule.xml;type=application/echo10+xml" \
    -F "collection=<collection.xml;type=application/echo10+xml" \
    "http://localhost:3002/providers/PROV1/validate/granule/ur4"

### Response Format

The response format will be XML if the context type is an XML format, JSON otherwise. The response
format can be forced to either JSON or XML by setting the Accept header to applicaiton/json
or application/xml.


### Error Messages

#### General Errors

Ingest validation errors can take one of two shapes. General error messages will be returned as a list of error messages like the following:

```
<errors>
   <error>Parent collection for granule [SC:AE_5DSno.002:30500511] does not exist.</error>
</errors>
```

#### UMM Validation Errors

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

#### JSON Error Messages

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

## Administrative API

### Clear the cache cache

    curl -i -XPOST http://localhost:3002/clear-cache?token=XXXX

### Querying caches

Endpoints are provided for querying the contents of the various caches used by the application.
The following curl will return the list of caches:

    curl -i http://localhost:3002/caches

The following curl will return the keys for a specific cache:

    curl -i http://localhost:3002/caches/cache-name

This curl will return the value for a specific key in the named cache:

    curl -i http://localhost:3002/caches/cache-name/cache-key

### Check application health

This will report the current health of the application. It checks all resources and services used by the application and reports their healthes in the response body in JSON format. For resources, the report includes an "ok?" status and a "problem" field if the resource is not OK. For services, the report includes an overall "ok?" status for the service and health reports for each of its dependencies. It returns HTTP status code 200 when the application is healthy, which means all its interfacing resources and services are healthy; or HTTP status code 503 when one of the resources or services is not healthy. It also takes pretty parameter for pretty printing the response.

    curl -i -XGET "http://localhost:3002/health?pretty=true"

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

Example un-healthy response body:

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

### Run Reindex Collections Permitted Groups Job

Collections which ACLs have changed can be reindexed by sending the following request.

    curl -i -XPOST -H "Echo-Token: mock-echo-system-token" http://localhost:3002/jobs/reindex-collection-permitted-groups

### Run Reindex All Collections Job

Reindexes every collection in every provider.

    curl -i -XPOST -H "Echo-Token: mock-echo-system-token" http://localhost:3002/jobs/reindex-all-collections

### Pause ingest scheduled jobs

Requires token with UPDATE ingest management permission.

    curl -v -XPOST -H "Echo-Token: mock-echo-system-token" http://localhost:3002/jobs/pause

### Resume ingest scheduled jobs

Requires token with UPDATE ingest management permission.

    curl -v -XPOST -H "Echo-Token: mock-echo-system-token" http://localhost:3002/jobs/resume

### Message queues

The ingest application will publish messages for the indexer application to consume.  The messages will be to index or delete concepts from elasticsearch.  Messaging is handled using the message-queue-lib which uses RabbitMQ.

#### Ingest unable to queue a message

This could happen because queueing the message times out, RabbitMQ has surpassed the configured limit for requests, or RabbitMQ is unavailable.  Ingest will treat this as an internal error and return the error to the provider. If this happens the data will still be left in Metadata DB, but won't be indexed. It's possible this newer version of a granule could be returned from CMR but it is unlikely to happen.

## License

Copyright © 2014-2015 NASA
