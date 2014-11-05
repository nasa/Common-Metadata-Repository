# cmr-bootstrap-app

Bootstrap is a CMR application that can bootstrap the CMR with data from Catalog REST. It has API methods for copying data from Catalog REST to the metadata db. It can also bulk index everything in the Metadata DB.


## Example curls

### Bulk copy provider FIX_PROV1 and all it's collections and granules to the metadata db

    curl -v -XPOST  -H "Content-Type: application/json" -d '{"provider_id": "FIX_PROV1"}' http://localhost:3006/bulk_migration/providers

For the echo-reverb test fixture data, the following curl can be used to check metadata db
to make sure the new data is available:

    curl -v http://localhost:3001/concepts/G1000000033-FIX_PROV1

This should return the granule including the echo-10 xml.

### Copy a single collection's granules

    curl -v -XPOST  -H "Content-Type: application/json" -d '{"provider_id": "FIX_PROV1", "collection_id": "C1000000073-FIX_PROV1"}' http://localhost:3006/bulk_migration/collections

### Bulk index a provider

  	curl -v -XPOST  -H "Content-Type: application/json" -d '{"provider_id": "FIX_PROV1"}' http://localhost:3006/bulk_index/providers

### Synchronize Catalog REST and CMR

Due to an issue in Catalog REST the CMR and Catalog REST databases diverged for a period of time because no ingest (new items, updates or deletes) were sent to the CMR. There is a mechanism for synchronizing the CMR with Catalog REST using the following endpoint:

    curl -v -XPOST http://localhost:3006/db_synchronize?start_date=2014-10-01T00:00:00Z&end_date=2014-10-05T00:00:00Z

The start date and end date should be used to indicate the time period during which Catalog REST and CMR went out of sync. This will start a synchronization process in the background and return immediately. The synchronization process iterates over each provider and executes the following steps:

  1. Synchronize missing collections
  2. Synchronize deleted collections
  3. Synchronize missing granules
  4. Synchronize deleted granules

#### Synchronizing Missing Items

  1. It finds any items in the Catalog REST database with an ingest_updated at between the start date and end date. These items are inserted into a table for processing.
  2. For each batch of N items from the work table:
     1. Get the latest revision ids of the items from the work table.
     2. For each item in the batch:
       1. Select the data from Catalog REST.
       2. Ingest it into Metadata DB with the expected revision id (latest revision id + 1)
         * An error could occur in this step if an ingest occurred after the revision id was retrieved. Metadata DB would reject are insert saying the revision id conflicted. We skip this item in this case as we've already received newer metadata.
       3. Index the item using the indexer.

Note that for every item found between start_date and end_date a new revision will be saved to Metadata DB

#### Synchronizing Deleted Items

  1. It finds any items which exist in Metadata DB and do not exist in Catalog REST. These items are inserted into a table for processing. The latest revision ids are retrieved at the same time.
  2. For each batch of N items from the work table:
     1. For each item in the batch:
       1. Create a tombstone in Metadata DB
         * An error could occur in this step if an ingest or delete occurred after the revision id was retrieved. Metadata DB would reject the tombstone saying the revision id conflicted. We skip this item in this case as we've already received a more recent update.
       2. Unindex the item using the indexer


**Important** - This endpoint does not validate that there isn't a database synchronization already running. Do not run it multiple times in a row unless you're sure it's not running currently. This will cause issues if you attempt to run it on multiple hosts since the work tables are truncated during processing.

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
      },
      "index-set" : {
        "ok?" : false,
        "problem" : {
          "elastic_search" : {
            "ok?" : false,
            "problem" : {
              "status" : "Inaccessible",
              "problem" : "Unable to get elasticsearch cluster health, caught exception: Connection refused"
            }
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

Copyright © 2014 NASA
