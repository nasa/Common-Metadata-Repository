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

### Message queues

The ingest application will publish messages for the indexer application to consume.  The messages will be to index or delete concepts from elasticsearch.  Messaging is handled using the message-queue-lib which uses RabbitMQ.

#### Ingest unable to queue a message

This could happen because queueing the message times out, RabbitMQ has surpassed the configured limit for requests, or RabbitMQ is unavailable.  Ingest will treat this as an internal error and return the error to the provider. If this happens the data will still be left in Metadata DB, but won't be indexed. It's possible this newer version of a granule could be returned from CMR but it is unlikely to happen.

## Administrative API

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






## License

Copyright © 2014-2015 NASA
