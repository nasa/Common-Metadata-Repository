# cmr-bootstrap-app

Bootstrap is a CMR application that can bootstrap the CMR with data from Catalog REST. It has API methods for copying data from Catalog REST to the metadata db. It can also bulk index everything in the Metadata DB.

## Running

To start a web server for the application, run:

    lein run

## Giving read permission to metadata db user for catalog rest tables

grant select on DEV_52_CATALOG_REST.providers to METADATA_DB;

grant select on DEV_52_CATALOG_REST.FIX_PROV1_DATASET_RECORDS to METADATA_DB;

grant select on DEV_52_CATALOG_REST.FIX_PROV1_GRANULE_RECORDS to METADATA_DB;


This needs to be done for each provider.


## Example curls

### Bulk copy provider FIX_PROV1 and all it's collections and granules to the metadata db

	curl -v -XPOST  -H "Content-Type: application/json" -d '{"provider_id": "FIX_PROV1"}' http://localhost:3006/bulk_migration/providers

For the echo-reverb test fixture data, the following curl can be used to check metadata db
to make sure the new data is available:

	curl -v http://localhost:3001/concepts/G1000000033-FIX_PROV1

This should return the granule including the echo-10 xml.

### Copy a single collection and all its granules

	curl -v -XPOST  -H "Content-Type: application/json" -d '{"provider_id": "FIX_PROV1", "collection_id": "C1000000073-FIX_PROV1"}' http://localhost:3006/bulk_migration/collections

### Bulk index a provider

	curl -v -XPOST  -H "Content-Type: application/json" -d '{"provider_id": "FIX_PROV1"}' http://localhost:3006/bulk_index/providers

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
