# cmr-ingest-app

This is the ingest component of the CMR system. It is responsible for collaborating with metadata db and indexer components of the CMR system to maintain the lifecycle of concepts coming into the system.

## Setting up the database

There are two ways database operations can be done. It can happen through leiningen commands for local development or using the built uberjar.

### Leiningen commands

1. Create the user

```bash
lein create-user
```

2. Run the migration scripts

```bash
lein migrate
```

You can use `lein migrate -version version` to restore the database to
a given version. `lein migrate -version 0` will clean the datbase
completely.

3. Remove the user

```bash
lein drop-user
```

### Java commands through uberjar

1. Create the user

```bash
CMR_DB_URL=thin:@localhost:1521/orcl CMR_INGEST_PASSWORD=****** java -cp target/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar cmr.db create-user
```

2. Run db migration

```bash
CMR_DB_URL=thin:@localhost:1521/orcl CMR_INGEST_PASSWORD=****** java -cp target/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar cmr.db migrate
```

You can provider additional arguments to migrate the database to a given version as in lein migrate.

3. Remove the user

```bash
CMR_DB_URL=thin:@localhost:1521/orcl CMR_INGEST_PASSWORD=****** java -cp target/cmr-ingest-app-0.1.0-SNAPSHOT-standalone.jar cmr.db drop-user
```

### Message queues

The ingest application will publish messages to a fanout exchange for the indexer application and other consumers. Other applications setup their own
queues and bind to the ingest exchange.

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
    * [POST /jobs/reindex-autocomplete-suggestions - Runs to job to reindex all autocomplete suggestions.](#reindex-all-suggestions)
    * [POST /jobs/cleanup-expired-collections - Runs the job to remove expired collections.](#cleanup-expired-collections)
    * [POST /jobs/trigger-granule-task-cleanup-job - Start cleanup of old granule bulk update tasks.](#trigger-granule-task-cleanup-job)
    * [POST /jobs/trigger-email-subscription-processing - Run the subscription job over a specified granule revision date range.](#trigger-email-subscription-processing)
    * [GET /jobs/email-subscription-processing-job-state - Get the current email-subscription-processing job state.](#email-subscription-processing-job-state)
    * [POST /jobs/enable-email-subscription-processing-job - Enable the email-subscription-processing job.](#enable-email-subscription-processing-job)
    * [POST /jobs/disable-email-subscription-processing-job - Disable the email-subscription-processing job.](#disable-email-subscription-processing-job)
  * /caches
    * [GET /caches - Gets a list of the caches in ingest.](#get-caches)
    * [GET /caches/\<cache-name> - Gets a list of the keys stored in the specific cache.](#get-cache-keys)
    * [GET /caches/\<cache-name>/\<cache-key> - Gets the value of the cache key in the specific cache](#get-cache-ialue)
    * [POST /caches/clear-cache - Clears the ingest caches.](#clear-cache)
  * /db-migrate
    * [POST - Run database migration.](#db-migrate)
  * /health
    * [GET - Gets the health of the ingest application.](#application-health)

### <a name="providers"></a> Providers

The providers that exist in the CMR are administered through the Ingest API. A provider consists of the following fields

 * `provider-id` - The alpha numeric upper case string identifying the provider. The maximum length of `provider-id` is 10 characters. See [provider id](#provider-id).
 * `short-name` - A unique identifier of the provider. It is similar to `provider-id`, but more descriptive. It allows spaces and other special characters. The maximum length of `short-name` is 128 characters. `short-name` defaults to `provider-id`.
 * `cmr-only` - True or false value that indicates if this is a provider that ingests directly through the CMR Ingest API or the legacy ECHO Catalog REST Ingest API. A CMR Only provider will still have ACLs configured in ECHO and support ordering through ECHO. A CMR Only provider may even still have data in Catalog REST but it will not be kept in sync with the CMR. `cmr-only` defaults to false.
 * `small` - True or false value that indicates if this is a provider that has a small amount of data and its collections and granules will be ingested into the `SMALL_PROV` tables. `small` defaults to false.
 * `consortiums` - An optional field consists of a string of space delimited consortiums, which are projects, efforts or tags that a provider is associated with. Consortium can only contain alphanumeric characters and underscores. For example: `CWIC EOSDIS GEOSS` is a valid value for consortiums.

The provider API only supports requests and responses in JSON.

#### <a name="get-providers"></a> Get Providers

Returns a list of the configured providers in the CMR.

```bash
curl %CMR-ENDPOINT%/providers
```

```json
[{"provider-id":"PROV2","short-name":"Another Test Provider","cmr-only":true,"small":false},{"provider-id":"PROV1","short-name":"Test Provider","cmr-only":false,"small":false}]
```

#### <a name="create-provider"></a> Create Provider

Creates a provider in the CMR. The provider id specified should match that of a provider configured in ECHO.

```bash
curl -i -XPOST -H "Content-Type: application/json" -H "Echo-Token: XXXX" %CMR-ENDPOINT/providers -d \
'{"provider-id": "PROV1", "short-name": "Test Provider", "cmr-only": false, "small":false}'
```

#### <a name="update-provider"></a> Update Provider

Updates the attributes of a provider in the CMR. The `small` attribute cannot be changed during update.

```bash
curl -i -XPUT -H "Content-Type: application/json" -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1 -d \
'{"provider-id": "PROV1", "short-name": "Test Provider", "cmr-only":true, "small":false}'
```

#### <a name="delete-provider"></a> Delete Provider

Removes a provider from the CMR. Deletes all data for the provider in Metadata DB and unindexes all data in Elasticsearch.

```bash
curl -i -XDELETE -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1
```

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


This will report the current health of the application. It checks all resources and services used by the application and reports their health status in the response body in JSON format. The report includes an "ok?" status and a "problem" field for each resource. The report includes an overall "ok?" status and health reports for each of a service's dependencies. It returns HTTP status code 200 when the application is healthy, which means all its interfacing resources and services are healthy; or HTTP status code 503 when one of the resources or services is not healthy. It also takes pretty parameter for pretty printing
the response.

```bash
curl -i -XGET %CMR-ENDPOINT%/health?pretty=true
```

Example healthy response body:

```json
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
      }
    }
  }
}
```

Example unhealthy response body:

```json
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
      }
    }
  }
}
```

***

### <a name="jobs"></a> Jobs

Ingest has internal jobs that run. They can be run manually and controlled through the Jobs API.

#### <a name="pause-jobs"></a> Pause Jobs

```bash
curl -i -XPOST -H "Echo-Token: XXXX" %CMR-ENDPOINT%/jobs/pause
```

### <a name="resume-jobs"></a> Resume ingest scheduled jobs

```bash
curl -i -XPOST -H "Echo-Token: XXXX" %CMR-ENDPOINT%/jobs/resume
```

### <a name="reindex-collection-permitted-groups"></a> Run Reindex Collections Permitted Groups Job

Collections which ACLs have changed can be reindexed by sending the following request.

```bash
curl -i -XPOST -H "Echo-Token: XXXX" %CMR-ENDPOINT%/jobs/reindex-collection-permitted-groups
```

### <a name="reindex-all-collections"></a> Run Reindex All Collections Job

Reindexes every collection in every provider.
```bash
curl -i -XPOST -H "Echo-Token: XXXX" %CMR-ENDPOINT%/jobs/reindex-all-collections
```

It accepts an optional parameter `force_version=true`. If this option is specified then Elasticsearch will be reindexed with `force` version instead of the normal `external_gte`. See https://www.elastic.co/guide/en/elasticsearch/reference/7.5/docs-index_.html#_version_types This will cause all data in the database to overwrite the elasticsearch index even if there's a newer version in Elasticsearch. This can be used to fix issues where a newer revision was force deleted or as in the case CMR-2673 the collections were indexed with a larger version and then that was changed at the database level. There's a race condition when this is run. If a collection comes in during indexing the reindexing could overwrite that data in Elasticsearch with an older revision of the collection. The race condition can be corrected by running reindex all collections _without_ the `force_version=true` which will index any revisions with larger transaction ids over top of older data.

### <a name="reindex-all-suggestions"></a> Run Reindex All Autocomplete Suggestions Job

Reindexes every collection in every provider.
```bash
curl -i -XPOST -H "Echo-Token: XXXX" %CMR-ENDPOINT%/jobs/reindex-all-autocomplete-suggestions
```

### <a name="cleanup-expired-collections"></a> Run Cleanup Expired Collections Job

Looks for collections that have a delete date in the past and removes them.

```bash
curl -i -XPOST -H "Echo-Token: XXXX" %CMR-ENDPOINT%/jobs/cleanup-expired-collections
```

### <a name="trigger-granule-task-cleanup-job"></a> Run Bulk Granule Update Task Cleanup Job

Removes bulk granule update tasks that are in COMPLETE state, and are at least 90 days old
```bash
curl -i -XPOST -H "Echo-Token: XXXX" %CMR-ENDPOINT%/jobs/trigger-granule-task-cleanup-job
```

### <a name="trigger-email-subscription-processing"></a> Run Subscription Job Over Granule Revision Date Range

Sends subscription emails covering granules that were updated in the specified revision date range.
```bash
curl -i -XPOST -H "Echo-Token: XXXX" %CMR-ENDPOINT%/jobs/trigger-email-subscription-processing?revision-date-range=2011-01-01T10:00:00Z,2012-01-01T10:00:00Z
```

### <a name="email-subscription-processing-job-state"></a> Get the current state of the subscription job

Returns a JSON response containing the current state of the email-subscription-processing job in the `state` attribute.  The state value corresponds with possible org.Quartz.Trigger.TriggerState values, with NORMAL being enabled and PAUSED being disabled.
```bash
curl -i -XGET -H "Echo-Token: XXXX" %CMR-ENDPOINT%/jobs/email-subscription-processing-job-state
```

### <a name="enable-email-subscription-processing-job"></a> Enable the subscription job

Sets the email-subscription-processing job to an enabled state.  If it is already enabled, there is no change.
```bash
curl -i -XPOST -H "Echo-Token: XXXX" %CMR-ENDPOINT%/jobs/enable-email-subscription-processing-job
```

### <a name="disable-email-subscription-processing-job"></a> Disable the subscription job

Sets the email-subscription-processing job to an disabled state.  If it is already disabled, there is no change.
```bash
curl -i -XPOST -H "Echo-Token: XXXX" %CMR-ENDPOINT%/jobs/disable-email-subscription-processing-job
```

### Refresh Collection Granule Aggregate Cache

The collection granule aggregate cache is used to cache information about all the granules within a collection that are indexed with that collection. That's currently limited to the granule temporal minimum and maximum. The cache is refreshed by a periodic job. The cache is located in the indexer but refresh scheduling is handled by Ingest so that singleton jobs can be used.

There are two kinds of cache refreshes that can be triggered. The full cache refresh will refresh the entire cache. Collections must be manually reindexed after the cache has been refreshed to get the latest data indexed.

```bash
curl -i -XPOST http://localhost:3002/jobs/trigger-full-collection-granule-aggregate-cache-refresh?token=XXXX
```

The partial cache refresh will look for granules ingested over the last trigger period (configurable) and expand the collection granule aggregate temporal times to cover any new data that was ingested. The collections that had changes will automatically be queued for reindexing after this runs.

```bash
curl -i -XPOST http://localhost:3002/jobs/trigger-partial-collection-granule-aggregate-cache-refresh?token=XXXX
```

### <a name="db-migrate"></a> Run database migration

Migrate database to the latest schema version:

```bash
curl -v -XPOST -H "Echo-Token: XXXX" http://localhost:3002/db-migrate
```

Migrate database to a specific schema version (e.g. 3):

```bash
curl -v -XPOST -H "Echo-Token: XXXX" http://localhost:3002/db-migrate?version=3
```

## License

Copyright © 2014-2021 NASA
