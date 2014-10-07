# cmr-indexer-app

This is the indexer application for the CMR. It is responsible for indexing modified data into Elasticsearch.

### Index a concept

    curl -i -XPOST -H "Content-Type: application/json" http://localhost:3004 -d '{"concept-id": "C1234-PROV1", "revision-id": "1"}'

### Delete a concept

    curl -i -XDELETE -H "Content-Type: application/json" http://localhost:3004/C1234-PROV1/2

## Reset elastic and cache

*WARNING - this endpoint drops all data from the index.*

Every CMR application has a reset function to reset it back to it's initial state. This will reset the indexes back to their initial state and also clear the cache.

    curl -i -XPOST http://localhost:3004/reset?token=XXXX

### Clear the cache cache

    curl -i -XPOST http://localhost:3004/clear-cache?token=XXXX

### Check application health

This will report the current health of the application. It checks all resources and services used by the application and reports their healthes in the response body in JSON format. For resources, the report includes an "ok?" status and a "problem" field if the resource is not OK. For services, the report includes an overall "ok?" status for the service and health reports for each of its dependencies. It returns HTTP status code 200 when the application is healthy, which means all its interfacing resources and services are healthy; or HTTP status code 503 when one of the resources or services is not healthy.

    curl -i -XGET "http://localhost:3004/health"

Example healthy response body:

```
{
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
```

Example un-healthy response body:

```
{
  "elastic_search" : {
    "ok?" : true
  },
  "echo" : {
    "ok?" : true
  },
  "metadata-db" : {
    "ok?" : false,
    "problem" : {
      "oracle" : {
        "ok?" : false,
        "problem" : "db-spec cmr.metadata_db.data.memory_db.MemoryDB@aead584 is missing a required parameter"
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
```

### Update the index set mappings

    curl -XPOST http://localhost:3004/update-indexes?token=XXXX


### Ignore version conflict

By default, version conflict returned from elasticsearch will be ignored. User can override the default by passing in query parameter "ignore_conflict=false" to the request.

## License

Copyright © 2014 NASA
