# cmr-metadata-db-app

## Web API

### Sample Concept JSON

#### Collection

    {
      "concept-type": "collection",
      "native-id": "provider collection id",
      "concept-id": "C1-PROV1",
      "provider-id": "PROV1",
      "user-id": "user101",
      "metadata": "xml here",
      "format": "application/echo10+xml",
      "revision-id": 1, //(optional field)
      "transaction-id" : 100005,
      "revision-date": "2012-01-01T00:00:00", //(optional field)
      "extra-fields": {
        "short-name": "short",
        "version-id": "V01",
        "entry-id": "short_V01"
        "entry-title": "Dataset V01"
      }
    }

#### Granule

    {
      "concept-type": "granule",
      "native-id": "provider granule id",
      "concept-id": "G1-PROV1",
      "provider-id": "PROV1",
      "metadata": "xml here",
      "format": "application/echo10+xml",
      "revision-id": 1, //(optional field)
      "revision-date": "2012-01-01T00:00:00", //(optional field)
      "extra-fields": {
        "parent-collection-id": "C5-PROV1",
        "parent-entry-title": "ET1"
      }
    }

#### Service

    {    
      "concept-type": "service",
      "native-id": "service1",
      "user-id": "user101",
      "metadata": "{\"Name\": \"AIRX3STD\",  \"Type\": \"OPeNDAP\",  \"Version\":
                   \"1.9\",  \"Description\": \"AIRS Level-3 retrieval product created using AIRS IR, AMSU without HSB.\",  \"OnlineResource\": {    \"Linkage\": \"https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/\",    \"Name\": \"OPeNDAP Service for AIRS Level-3 retrieval products\",    \"Description\": \"OPeNDAP Service\"  },  \"ServiceOptions\": {\"SubsetType\": [\"Spatial\", \"Variable\"],    \"SupportedProjections\": [\"Geographic\"], \"SupportedFormats\": [      \"netCDF-3\", \"netCDF-4\", \"Binary\", \"ASCII\"]}}",
      "format": "application/vnd.nasa.cmr.umm+json;version=1.0",
      "revision-id": 1, //(optional field)
      "revision-date": "2012-01-01T00:00:00", //(optional field)
      "extra-fields": {
        "service-name": "AIRX3STD"
      }
    }

#### Service Association

    {
      "concept-type": "service-association",
      "native-id": "S1200000008-PROV1/C1200000005-PROV1",
      "user-id": "user1",
      "format": "application/edn",
      "metadata": "{:service-concept-id \"S1200000008-PROV1\", :associated-concept-id \"C1200000005-PROV1\", :associated-revision-id 1}",
      "extra-fields": {
        "service-concept-id": "S1200000008-PROV1",
        "associated-concept-id": "C1200000005-PROV1",
        "associated-revision-id": 1
      }
    }

#### Tool

    {
      "concept-type": "tool",
      "native-id": "tool1",
      "user-id": "user101",
      "metadata": "{ \"Name\": \"USGS_TOOLS_LATLONG\", \"LongName\": \"WRS-2 Path/Row to Latitude/Longitude Converter\", \"Type\": \"Downloadable Tool\", \"Version\": \"1.0\", \"Description\": \"The USGS WRS-2 Path/Row to Latitude/Longitude Converter allows users to enter any Landsat path and row to get the nearest scene center latitude and longitude coordinates.\", \"URL\": { \"URLContentType\": \"DistributionURL\", \"Type\": \"DOWNLOAD SOFTWARE\", \"Description\": \"Access the WRS-2 Path/Row to Latitude/Longitude Converter.\", \"URLValue\": \"http://www.scp.byu.edu/software/slice_response/Xshape_temp.html\" }, \"ToolKeywords\" : [{ \"ToolCategory\": \"EARTH SCIENCE SERVICES\", \"ToolTopic\": \"DATA MANAGEMENT/DATA HANDLING\", \"ToolTerm\": \"DATA INTEROPERABILITY\", \"ToolSpecificTerm\": \"DATA REFORMATTING\" }], \"Organizations\" : [ { \"Roles\": [\"SERVICE PROVIDER\"], \"ShortName\": \"USGS/EROS\",    \"LongName\": \"US GEOLOGICAL SURVEY EARTH RESOURCE OBSERVATION AND SCIENCE (EROS) LANDSAT CUSTOMER SERVICES\", \"URLValue\": \"http://www.usgs.gov\" } ], \"MetadataSpecification\": { \"URL\": \"https://cdn.earthdata.nasa.gov/umm/tool/v1.0\", \"Name\": \"UMM-T\", \"Version\": \"1.0\" }"  
      "format": "application/vnd.nasa.cmr.umm+json;version=1.0",
      "revision-id": 1, //(optional field)
      "revision-date": "2012-01-01T00:00:00", //(optional field)
      "extra-fields": {
        "tool-name": "USGS_TOOLS_LATLONG"
      }
    }

#### Tool Association

    {
      "concept-type": "tool-association",
      "native-id": "TL1200000008-PROV1/C1200000005-PROV1",
      "user-id": "user1",
      "format": "application/edn",
      "metadata": "{:tool-concept-id \"TL1200000008-PROV1\", :associated-concept-id \"C1200000005-PROV1\", :associated-revision-id 1}",
      "extra-fields": {
        "tool-concept-id": "TL1200000008-PROV1",
        "associated-concept-id": "C1200000005-PROV1",
        "associated-revision-id": 1
      }
    }

#### Subscription

    {
      "concept-type": "subscription",
      "native-id": "subscription1",
      "user-id": "user101",
      "metadata": "{\"Name\": \"someSubscription\",  \"SubscriberId\": \"someSubscriberId\",  \"EmailAddress\": \"someaddress@gmail.com\",  \"CollectionConceptId\": \"C1234-PROV1.\",  \"Query\": \"polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78\"}",
      "format": "application/vnd.nasa.cmr.umm+json;version=1.0",
      "revision-id": 1, //(optional field)
      "revision-date": "2012-01-01T00:00:00", //(optional field)
      "extra-fields": {
        "subscription-name": "someSubscription",
        "collection-concept-id": "C1234-PROV1",
        "subscriber-id": "someSubscriberId",
        "email-address": "someaddress@gmail.com"
      }
    }

#### Group

    {
      "concept-type": "access-group",
      "native-id": "group name",
      "concept-id": "AG1-PROV1",
      "provider-id": "PROV1",
      "user-id": "user101",
      "metadata": "group representation here",
      "format": "application/edn",
      "revision-id": 1, //(optional field)
      "revision-date": "2012-01-01T00:00:00", //(optional field)
    }

The provider-id can be "CMR" (for system level groups) or another provider id.

#### Tag

    {
      "concept-type": "tag",
      "native-id": "org.nasa.something.quality",
      "user-id": "jnorton",
      "format": "application/edn",
      "metadata": "{:tag-key \"org.nasa.something.ozone\", :description \"A very good tag\", :originator-id \"jnorton\"}"
    }

#### Tag Association

    {
      "concept-type": "tag-association",
      "native-id": "org.nasa.something.quality/C12-PROV_A42",
      "user-id": "jnorton",
      "format": "application/edn",
      "metadata": "{:tag-key \"org.nasa.something.ozone\", :associated-concept-id \"C120000000-PROV1\", :revision-id 1, :value \"string to be indexed\"}",
      "extra-fields": {
        "tag-key": "org.nasa.something.quality",
        "associated-concept-id": "C12-PROV_A42",
        "associated-revision-id": 1
      }
    }

The tag association metadata can have "value": "string to be indexed" or "data": "arbitrary JSON <= 32K" (optional fields)

#### Humanizer

    {
      "concept-type": "humanizer",
      "native-id" : "humanizer",
      "metadata" : "[{\"type\":\"trim_whitespace\",\"field\":\"platform\",\"order\":-100},{\"type\":\"priority\",\"field\":\"platform\",\"source_value\":\"Aqua\",\"order\":10,\"priority\":10}]",
      "user-id" : "user1",
      "deleted" : false,
      "format" : "application/json"
    }

#### Variable

    {
      "concept-type": "variable",
      "native-id": "MyNumberOneVariable",
      "metadata": "{\"VariableType\":\"\",\"DataType\":\"float\",\"Offset\":\"0.0\",\"ScienceKeywords\":[],\"Scale\":\"1.0\",\"Sets\":[{\"Name\":\"/\",\"Type\":\"General\",\"Size\":1.0,\"Index\":1.0}],\"Dimensions\":[{\"Name\":\"Solution_3_Land\",\"Size\":3,\"Type\":\"OTHER\"},{\"Name\":\"YDim\",\"Size\":180.0,\"Type\":\"LATITUDE_DIMENSION\"}],\"FillValue\":\"-9999.0 \",\"Name\":\"totCldH2OStdErr\",\"AcquisitionSourceName\":\"Instrument1\",\"Units\":\"MB\",\"LongName\":\"totCldH2OStdErr\"}",
      "user-id": "user1",
      "deleted": false,
      "format": "application/vnd.nasa.cmr.umm+json;version=1.0",
      "coll-concept-id": "C1200000005-PROV1",
      "extra-fields": {
        "variable-name": "totCldH2OStdErr",
        "measurement": "totCldH2OStdErrMeasurement"
        "fingerprint": "7bec71103f3eada1f539acb9f1072fd7"
      }
    }

#### Variable Association

    {
      "concept-type": "variable-association",
      "native-id": "V1200000006-PROV1/C1200000005-PROV1",
      "user-id": "user1",
      "format": "application/edn",
      "metadata": "{:variable-concept-id \"V1200000006-PROV1\", :associated-concept-id \"C1200000005-PROV1\", :associated-revision-id 1}",
      "extra-fields": {
        "variable-concept-id": "V1200000006-PROV1",
        "associated-concept-id": "C1200000005-PROV1",
        "associated-revision-id": 1
      }
    }

_Note the absence of provider-id for tag, tag association, humanizer and variable. These are system level entities and are always assigned the system level provider, CMR._

### Sample Tombstone (deleted concept) JSON

  	{
        "concept-type": "collection",
        "native-id": "provider collection id",
        "concept-id": "C1-PROV1",
        "provider-id": "PROV1",
        "user-id": "user101", (Only for colletions)
        "deleted": true,
        "transaction-id": 100005,
        "revision-id": 10,
        "extra-fields": {
          "short-name": "short",
          "version-id": "V01",
          "entry-id": "short_V01",
          "entry-title": "Dataset V01"
        }
      }


### Setting up the database

There are two ways database operations can be done. It can happen through
leiningen commands for local development or using the built uberjar.

These commands run against the CMR database and as such, you will need
Oracle installed or to be running an Oracle VM in order to perform migration
tasks. Furthermore, the associated environment variables will need to be
set. As mentioned in the top-level CMR `README` in the "Testing CMR" section,
the required environemnt variables and their values can be provided to you by
a CMR core dev.

The CMR projects that currently support migrations are:

* `ingest-app`
* `bootstrap-app`
* `metadata-db-app`

#### leiningen commands

1. Create the user

```
lein create-user
```

2. Run the migration scripts

```
lein migrate
```

You can use `lein migrate -version version` to restore the database to
a given version. `lein migrate -version 0` will clean the database
completely.

3. Remove the user

```
lein drop-user
```

#### CMR-wide

The Oracle setup script in `dev-system/support` demonstrates how the above
`lein` commands are used to create database users and run migrations for
the ingest, boostrap, and metadata-db apps.

Note that, depending upon how up-to-date your local `.jar` files are, you
may first need to run `lein install-with-content!` in the top-level CMR
directory first.

#### java commands through uberjar

1. Create the user

```
CMR_DB_URL=thin:@localhost:1521/orcl CMR_METADATA_DB_PASSWORD=****** java -cp target/cmr-metadata-db-app-0.1.0-SNAPSHOT-standalone.jar cmr.db create-user
```

2. Run db migration

```
CMR_DB_URL=thin:@localhost:1521/orcl CMR_METADATA_DB_PASSWORD=****** java -cp target/cmr-metadata-db-app-0.1.0-SNAPSHOT-standalone.jar cmr.db migrate
```

You can provider additional arguments to migrate the database to a given version as in lein migrate.

3. Remove the user

```
CMR_DB_URL=thin:@localhost:1521/orcl CMR_METADATA_DB_PASSWORD=****** java -cp target/cmr-metadata-db-app-0.1.0-SNAPSHOT-standalone.jar cmr.db drop-user
```


## General Workflow

### Update Flow

  - Retrieve latest revision from DB using provider-id, concept-type, and native id.
  - Compare revision from client if given to DB revision. If the revision from the client is not the next one we send a conflict error back to the client.
  - Create a new concept record
    - increment revision from DB
    - Reuse concept-id
    - Set all other fields
  - Insert into table
  - If we get a conflict from a uniqueness constraint restart from beginning of this flow

### Insert Flow

  - Retrieve latest revision from DB (and none are found)
  - Check if revision id sent by client is 0 if present. If the revision from the client is not 0 we send a conflict error back to the client.
  - Create a new concept record
    - Revision is 0
    - Generate a new concept-id using a sequence from Oracle or use value from client if provided.
      - This supports catalog rest specifying the concept-id.
    - Set all other fields
  - Insert into table
  - If we get a conflict from a uniqueness constraint restart from beginning of this flow


## API


### GET /concept-id/:concept-type/:provider-id/:native-id

returns: new or existing concept-id

    curl -v http://localhost:3001/concept-id/collection/PROV1/native-id

### POST /concepts

params: [concept] - revision-id optionally in concept
returns: revision-id.  revision-id begins at 1.
throws error if revision-id is less than or equal to the current highest saved revision-id (if any).

    curl -v -XPOST -H "Content-Type: application/json" -d '{"concept-type": "collection", "native-id": "native-id", "concept-id": "C1-PROV1", "provider-id": "PROV1", "metadata": "<Collection><ShortName>MINIMAL</ShortName></Collection>", "format": "application/echo10+xml", "extra-fields": {"short-name": "MINIMAL", "version-id": "V01", "entry-id": "MINIMAL_V01", "entry-title": "native-id"}}' http://localhost:3001/concepts/

#### Note Regarding Tags and Tag Associations

No provider should be specified when creating tag or tag association concepts - the system level CMR provider is used for all tags and tag associations. As such, tag concept-ids always take the form of T\<number\>-CMR and tag association concept-ids TA\<number>\>-CMR, where \<number\> is a positive integer.

Tombstones can be created using the same end-point used for saving concepts by specifying "deleted" as true in the body of the POST:

    curl -v -XPOST -H "Content-Type: application/json" -d '{"concept-id": "C1200000000-REG_PROV", "revision-id": 4, "deleted": true}' http://localhost:3001/concepts/

concept-id is a required field. revision-id and revision date are optional. No other fields are allowed.

#### Note Regarding Transaction IDs

When a new collections revision is saved (including tombstones) a global transaction-id is saved with it. This transaction-id represents a unique identifier of the state of the database at any give time and is returned with the concept fields when a concept is retrieved. This is to be used during indexing as the version of the indexed record (replacing revision-id).

### GET /concepts/#concept-id

params: none
returns: latest revision of a concept with the given concept-id

    curl -v http://localhost:3001/concepts/C1-PROV1

### GET /concepts/#concept-id/#revision-id

params: none
returns: concept with the given concept-id and revision-id

    curl -v http://localhost:3001/concepts/C1-PROV1/2

### POST /concepts/search/concept-revisions

params: as JSON body: [[concept-id/revision-id tuple] ...]. The tuples can be of different concept types.
url param: allow_missing - if true missing concepts will not result in a 404 - defaults to false
returns: list of concepts matching the tuples provided in the body of the POST

    curl -v -XPOST -H "Content-Type: application/json" -d '[["C1-PROV1", 1], ["G1-PROV1", 1]]' http://localhost:3001/concepts/search/concept-revisions?allow_missing=true

### POST /concepts/search/latest-concept-revisions

params: as JSON body: [concept-id1, concept-id2 ...]. The concept ids can be of different concept types.
url param: allow_missing - if true missing concepts will not result in a 404 - defaults to false
returns: list of the latest revisions of concepts matching the ids provided in the body of the POST

    curl -v -XPOST -H "Content-Type: application/json" -d '["C1-PROV1", "G1-PROV1"]' http://localhost:3001/concepts/search/latest-concept-revisions?allow_missing=true

### GET /concepts/search/:concept-types?param1=value&...

This returns all revisions of the concept that matches the search parameters by default. It also supports parameter 'latest'. When latest=true, only the latest revision of the concepts are returned.
Metadata can be excluded from the results by setting the parameter 'exclude-metadata' to 'true'.  Multiple values for a parameter will be ORed.

Supported combinations of concept type and parameters:
  * collections with any combination of concept-id, provider-id, entry-id, entry-title, short-name, version-id and native-id
  * granules with provider-id, granule-ur
  * granules with provider-id, native-id
  * humanizers with concept-id, native-id
  * tags with concept-id, native-id
  * tag associations with concept-id, native-id, tag-key, associated-concept-id, associated-revision-id
  * services with concept-id, provider-id, native-id
  * service associations with concept-id, native-id, service-concept-id, associated-concept-id, associated-revision-id
  * subscriptions with collection-concept-id, provider-id, subscriber-id, native-id, concept-id, normalized-query, subscription-type
  * variables with concept-id, provider-id, native-id
  * variable associations with concept-id, native-id, variable-concept-id, associated-concept-id, associated-revision-id

```
curl "http://localhost:3001/concepts/search/collections?provider-id=PROV1&short-name=s&version-id=1"
curl "http://localhost:3001/concepts/search/collections?provider-id=PROV1&entry-title=et"
curl "http://localhost:3001/concepts/search/granules?provider-id=PROV1&granule-ur=ur"
curl "http://localhost:3001/concepts/search/granules?provider-id=PROV1&native-id=id"
curl "http://localhost:3001/concepts/search/tags"
curl "http://localhost:3001/concepts/search/tag-associatons?associated-concept-id=C12-PROV1"
```

### POST /concepts/search/:concept-types

Same as the GET endpoint above, but with parameters passed in the body of a POST e.g.

    curl -XPOST -H "Content-Type: application/x-www-form-urlencoded" -d 'provider-id=PROV1&short-name=s&version-id=1' http://localhost:3001/concepts/search/collections

### GET /concepts/search/expired-collections?provider=PROV

url params: provider id to search
returns: list of concept ids for collections that have a latest revision with an expiration date that has been passed.

    curl http://localhost:3001/concepts/search/expired-collections?provider=PROV1

### DELETE /concepts/#concept-id/#revision-id

params: revision-date - Optionally sets the revision date of the tombstone that is created. This is mainly
for testing deletion of old tombstones. The revision-id must be greater than the highest revision-id of
the stored concept.
returns: the revision id of the tombstone generated for the concept

    curl -v -XDELETE localhost:3001/concepts/C1-PROV1/1

### DELETE /concepts/#concept-id

params: revision-date - Optionally sets the revision date of the tombstone that is created. This is mainly for testing deletion of old tombstones.
returns: the revision id of the tombstone generated for the concept

    curl -v -XDELETE localhost:3001/concepts/C1-PROV1

### DELETE /concepts/force-delete/:concept-id/:revision-id

Force delete the given concept revision. Force deleting the latest revision of a concept is not allowed. We should use regular delete for deleting the latest revision of a concept.
params: none
returns: nothing (status 204)

    curl -v -XDELETE /concepts/force-delete/C1-PROV1/1

### GET /provider_holdings

params: pretty
returns: list of maps containing granule counts per collection in JSON

    curl http://localhost:3001/provider_holdings?pretty=true
    [ {
      "entry-title" : "long-name86 V87",
      "provider-id" : "PROV1",
      "concept-id" : "C1200000004-PROV1",
      "granule-count" : 10
    }, {
      "entry-title" : "long-name77 V78",
      "provider-id" : "PROV1",
      "concept-id" : "C1200000001-PROV1",
      "granule-count" : 4
    } ... ]


### POST /reset

params: none
returns: nothing (status 204)

    curl -v -XPOST -H "Echo-Token: XXXX" http://localhost:3001/reset

### POST /db-migrate

params: version
returns: nothing (status 204)

Migrate database to the latest schema version:

    curl -v -XPOST -H "Echo-Token: XXXX" http://localhost:3001/db-migrate

Migrate database to a specific schema version (e.g. 36):

    curl -v -XPOST -H "Echo-Token: XXXX" http://localhost:3001/db-migrate?version=36

## Jobs API

### POST /jobs/pause

Requires token with UPDATE ingest management permission.
params: none
returns: nothing (status 204)

    curl -v -XPOST -H "Echo-Token: mock-echo-system-token" http://localhost:3001/jobs/pause

### POST /jobs/resume

Requires token with UPDATE ingest management permission.
params: none
returns: nothing (status 204)

    curl -v -XPOST -H "Echo-Token: mock-echo-system-token" http://localhost:3001/jobs/resume

### POST /jobs/old-revision-concept-cleanup

Synchronously runs the old revision concept cleanup job. This will delete the old revisions as well as tombstones that are more than a configurable number of days old (through config parameter CMR_DAYS_TO_KEEP_TOMBSTONE which is default to 365). Requires token with UPDATE ingest management permission.

    curl -v -XPOST -H "Echo-Token: mock-echo-system-token" http://localhost:3001/jobs/old-revision-concept-cleanup

### POST /jobs/expired-concept-cleanup

Synchronously runs the expired concept cleanup job. Requires token with UPDATE ingest management permission.

    curl -v -XPOST -H "Echo-Token: mock-echo-system-token" http://localhost:3001/jobs/expired-concept-cleanup


## Providers API

### Create provider

Creates a provider in Metadata DB. The `short-name` parameter uniquely identifies a provider. It is similar to `provider-id`, but more descriptive. It allows spaces and other special characters. `short-name` defaults to `provider-id`. The `cmr-only` parameter indicates if this is a provider that ingests directly to the CMR and not through Catalog REST. `cmr-only` defaults to false. The `small` parameter indicates if this is a provider that has a small amount of data and its collections and granules will be ingested into the `SMALL_PROV` tables. `small` defaults to false.

    curl -v -XPOST -H "Content-Type: application/json" -H "Echo-Token: mock-echo-system-token" -d '{"provider-id": "PROV1", "short-name": "Test Provider", "cmr-only":false, "small":false}' http://localhost:3001/providers

### Update provider

Updates the attributes of a provider in Metadata DB. The `small` attribute cannot be changed during update.

    curl -v -XPUT -H "Content-Type: application/json" -H "Echo-Token: mock-echo-system-token" -d '{"provider-id": "PROV1", "short-name": "Test Provider", "cmr-only":true, "small":false}' http://localhost:3001/providers/PROV1

### Delete provider

Removes a provider from the CMR. Deletes all data for the provider.

    curl -v -XDELETE -H "Echo-Token: mock-echo-system-token" http://localhost:3001/providers/PROV1

### Get providers

Returns a list of the configured providers in Metadata DB.

    curl http://localhost:3001/providers

    [{"provider-id":"PROV2","short-name":"Another Test Provider","cmr-only":true,"small":true},{"provider-id":"PROV1","short-name":"Test Provider","cmr-only":false,"small":false}]

## Querying caches

Endpoints are provided for querying the contents of the various caches used by the application.
The following curl will return the list of caches:

    curl -i http://localhost:3001/caches

The following curl will return the keys for a specific cache:

    curl -i http://localhost:3001/caches/cache-name

This curl will return the value for a specific key in the named cache:

    curl -i http://localhost:3001/caches/cache-name/cache-key

## Check application health

This will report the current health of the application. It checks all resources and services used by the application and reports their healthes in the response body in JSON format. For resources, the report includes an "ok?" status and a "problem" field if the resource is not OK. For services, the report includes an overall "ok?" status for the service and health reports for each of its dependencies. It returns HTTP status code 200 when the application is healthy, which means all its interfacing resources and services are healthy; or HTTP status code 503 when one of the resources or services is not healthy. It also takes pretty parameter for pretty printing the response.

    curl -i -XGET "http://localhost:3001/health?pretty=true"

Example healthy response body:

```
{
  "oracle" : {
    "ok?" : true
  },
  "echo" : {
    "ok?" : true
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
  }
}
```

## License

Copyright © 2021 NASA
