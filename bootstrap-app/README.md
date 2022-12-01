# cmr-bootstrap-app

Bootstrap is a CMR application that can bootstrap the CMR with data from Catalog REST. It has API methods for copying data from Catalog REST to the metadata db. It can also bulk index everything in the Metadata DB.

#### leiningen commands - used to create/remove user for bootstrap jobs

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

#### java commands through uberjar

1. Create the user

```
CMR_DB_URL=thin:@localhost:1521/orcl CMR_BOOTSTRAP_DB_PASSWORD=****** java -cp target/cmr-bootstrap-app-0.1.0-SNAPSHOT-standalone.jar cmr.db create-user
```

2. Run db migration

```
CMR_DB_URL=thin:@localhost:1521/orcl CMR_BOOTSTRAP_DB_PASSWORD=****** java -cp target/cmr-bootstrap-app-0.1.0-SNAPSHOT-standalone.jar cmr.db migrate
```

You can provider additional arguments to migrate the database to a given version as in lein migrate.

3. Remove the user

```
CMR_DB_URL=thin:@localhost:1521/orcl CMR_BOOTSTRAP_DB_PASSWORD=****** java -cp target/cmr-bootstrap-app-0.1.0-SNAPSHOT-standalone.jar cmr.db drop-user
```

## Rebalancing Collections

### Start Rebalancing a Collection

Starts moving all the granules in a specified collection from its current index to the target index. If no target is provided the default is to move the granules from the small collections index into their own separate index. The work is performed asynchronously in the background. The job should be monitored through the bootstrap application's logs and using the status endpoint detailed below. The two valid values for the `target` query parameter are `separate-index` and `small-collections`. This also supports a `synchronous=true` query parameter to cause the collection reindexing to happen synchronously with the request. This is mostly for testing purposes.

```
curl -i -XPOST http://localhost:3006/rebalancing_collections/C5-PROV1/start?target=separate-index

HTTP/1.1 200 OK
{"message":"Rebalancing started for collection C5-PROV1"}
```

### Get Rebalancing Collection Status

Fetches the status of rebalancing. It returns counts of the collection in the small collections index and in the separate index.

```
curl -i  -H "Accept: application/json" http://localhost:3006/rebalancing_collections/C5-PROV1/status

HTTP/1.1 200 OK
{"small-collections":4,"separate-index":4, "rebalancing-status": "COMPLETE"}
```


### Finalize a Rebalancing Collection

Finalizes a rebalancing collection. Removes the collection from the list of rebalancing collections in the index set. If the target was a separate index this call deletes all granules from the small collections index for the specified collection. If the target was small collections the collection specific index will be removed from the index-set, but the index will not be deleted since this could cause errors until search caches have all been refreshed. The caller is expected to manually delete the index after a period of time.

```
curl -i -XPOST  http://localhost:3006/rebalancing_collections/C5-PROV1/finalize

HTTP/1.1 200 OK
{"message":"Rebalancing completed for collection C5-PROV1"}
```

## Bulk Operations

### Bulk copy provider FIX_PROV1 and all it's collections and granules to the metadata db

    curl -i -XPOST  -H "Content-Type: application/json" -d '{"provider_id": "FIX_PROV1"}' http://localhost:3006/bulk_migration/providers

For the echo-reverb test fixture data, the following curl can be used to check metadata db
to make sure the new data is available:

    curl -i http://localhost:3001/concepts/G1000000033-FIX_PROV1

This should return the granule including the echo-10 xml.

### Copy a single collection's granules

    curl -i -XPOST -H "Content-Type: application/json" -d '{"provider_id": "FIX_PROV1", "collection_id": "C1000000073-FIX_PROV1"}' http://localhost:3006/bulk_migration/collections

### Bulk index a provider

    curl -i -XPOST -H "Content-Type: application/json" -d '{"provider_id": "FIX_PROV1"}' http://localhost:3006/bulk_index/providers

NOTE from CMR-1908 that when reindexing a provider the collections are not reindexed in the all revisions index. The workaround here is to use the indexer endpoint for reindexing collections.

Operator can use the `start_index` parameter to index concepts with sequence number larger than the `start_index` value. This parameter is used to recover from a prematurely terminated bulk index request.

### Bulk index all providers

    curl -i -XPOST http://localhost:3006/bulk_index/providers/all

Bulk indexing all collections and granules for all providers. Similar to the bulk index a provider endpoint, this reindexing of providers will not reindex the collections in the all revisions index. The workaround here is to use the indexer endpoint for reindexing collections.

### Bulk index a single collection

    curl -i -XPOST  -H "Content-Type: application/json" -d '{"provider_id": "FIX_PROV1", "collection_id":"C123-FIX_PROV1"}' http://localhost:3006/bulk_index/collections

Operator can use the `start_index` parameter to index concepts with sequence number larger than the `start_index` value. This parameter is used to recover from a prematurely terminated bulk index request.

### Bulk index concepts by concept-id - for indexing multiple specific items

    curl -XPOST -H "Content-Type: application/json" "http://localhost:3006/bulk_index/concepts"
    -d '{"provider_id": "PROV1", "concept_type":"collection", "concept_ids":["C123-PROV1","C124-PROV1"]}'

### Bulk index concepts newer than a given date-time

For all providers and all system concepts:

    curl -i -XPOST http://localhost:3006/bulk_index/after_date_time?date_time=2015-02-02T10:00:00Z"

For a given list of providers (use provider `CMR` to index all system concepts):

    curl -i -XPOST -H "Content-Type: application/json" -d '{"provider_ids": ["PROV1", "PROV2", "CMR"]}' http://localhost:3006/bulk_index/after_date_time?date_time=2015-02-02T10:00:00Z"

Similar to the bulk index a provider endpoint, this reindexing of concepts newer than a given date-time will not reindex the concepts in the all revisions index. The workaround here is to use the indexer endpoint for reindexing collections and the concept specific bootstrap bulk indexing endpoint for variables, services, tools and subscriptions.

### Bulk index all system concepts (tags/acls/access-groups)

    curl -i -XPOST http://localhost:3006/bulk_index/system_concepts

### Bulk index variables:

For a single provider:

    curl -i -XPOST http://localhost:3006/bulk_index/variables/PROV1

For all providers:

    curl -i -XPOST http://localhost:3006/bulk_index/variables

### Bulk index services:

For a single provider:

    curl -i -XPOST http://localhost:3006/bulk_index/services/PROV1

For all providers:

    curl -i -XPOST http://localhost:3006/bulk_index/services

### Bulk index tools:

For a single provider:

    curl -i -XPOST http://localhost:3006/bulk_index/tools/PROV1

For all providers:

    curl -i -XPOST http://localhost:3006/bulk_index/tools

### Bulk index subscriptions:

For a single provider:

    curl -i -XPOST http://localhost:3006/bulk_index/subscriptions/PROV1

For all providers:

    curl -i -XPOST http://localhost:3006/bulk_index/subscriptions

### Bulk index generic documents:

Generic document bulk index endpoints are generated dynamically based on the value of the
approved generic documents array defined in common lib. All endpoints take the plural form,
so for example, for the generic concept type "data-quality-summary", they would be:

For a single provider:

    curl -i -XPOST http://localhost:3006/bulk_index/data-quality-summaries/PROV1

For all providers:

    curl -i -XPOST http://localhost:3006/bulk_index/data-quality-summaries/

### Update fingerprints of variables:

For a single variable:

    curl -i -XPOST http://localhost:3006/fingerprint/variables/V1-PROV1

For all variables:

    curl -i -XPOST http://localhost:3006/fingerprint/variables

For all variables of a single provider:

    curl -i -XPOST -H "Content-Type: application/json" http://localhost:3006/fingerprint/variables -d '{"provider_id": "PROV1"}'

### Initialize Virtual Products

Virtual collections contain granules derived from a source collection. Only granules specified in the source collections in the virtual product app configuration will be considered. Virtual granules will only be created in the configured destination virtual collections if they already exist. To initialize virtual granules from existing source granules, use the following command:

    curl -i -XPOST http://localhost:3006/virtual_products?provider-id=PROV1&entry-title=et1

Note that provider-id and entry-title are required.

### Index recently replicated concepts

This endpoint should only be using in an AWS environment where the Database Migration Service (DMS)
is being used to replicate changes from another database to this environment. This will index any
concepts that have been replicated since the last replication run.

    curl -i -XPOST http://localhost:3006/index_recently_replicated

### Run database migration

Migrate database to the latest schema version:

    curl -i -XPOST -H "Echo-Token: XXXX" http://localhost:3006/db-migrate

Migrate database to a specific schema version (e.g. 3):

    curl -i -XPOST -H "Echo-Token: XXXX" http://localhost:3006/db-migrate?version=3

### Check application health

This will report the current health of the application. It checks all resources and services used by the application and reports their healthes in the response body in JSON format. For resources, the report includes an "ok?" status and a "problem" field if the resource is not OK. For services, the report includes an overall "ok?" status for the service and health reports for each of its dependencies. It returns HTTP status code 200 when the application is healthy, which means all its interfacing resources and services are healthy; or HTTP status code 503 when one of the resources or services is not healthy. It also takes pretty parameter for pretty printing the response.

    curl -i -XGET "http://localhost:3006/health?pretty=true"

Example healthy response body:

```
{
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
  "internal-metadata-db" : {
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

Example un-healthy response body:

```
{
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
  "internal-metadata-db" : {
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
    "ok?" : false,
    "dependencies" : {
      "elastic_search" : {
        "ok?" : false,
        "problem" : {
          "status" : "Inaccessible",
          "problem" : "Unable to get elasticsearch cluster health, caught exception: Connection refused"
        }
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

## License

Copyright © 2014-2021 NASA
