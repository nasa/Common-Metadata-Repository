# cmr-indexer-app

This is the indexer application for the CMR. It is responsible for indexing modified data into Elasticsearch.

### Index a concept

    curl -i -XPOST -H "Content-Type: application/json" http://localhost:3004 -d '{"concept-id": "C1234-PROV1", "revision-id": "1"}'

### Delete a concept

    curl -i -XDELETE -H "Content-Type: application/json" http://localhost:3004/C1234-PROV1/2

### Delete a provider

This will un-index all concepts within the given provider.

    curl -i -XDELETE http://localhost:3004/provider/PROV1?token=XXXX

## Administrative Tasks

These tasks require an admin user token with the INGEST_MANAGEMENT_ACL with read or update
permission.

### Reset elastic and cache

*WARNING - this endpoint drops all data from the index.*

Every CMR application has a reset function to reset it back to it's initial state. This will reset the indexes back to their initial state and also clear the cache.

    curl -i -XPOST http://localhost:3004/reset?token=XXXX

### Clear the cache cache

    curl -i -XPOST http://localhost:3004/clear-cache?token=XXXX

### Querying caches

Endpoints are provided for querying the contents of the various caches used by the application.
The following curl will return the list of caches:

    curl -i http://localhost:3004/caches

The following curl will return the keys for a specific cache:

    curl -i http://localhost:3004/caches/cache-name

This curl will return the value for a specific key in the named cache:

    curl -i http://localhost:3004/caches/cache-name/cache-key

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

By default, a comparison is run between the existing elasticsearch indexes and what is configured in index-set, and only apply the update when there is a difference between the two. User can override the default by passing in query parameter "force=true" and always update the elasticsearch indexes with the current configuration.

    curl -XPOST http://localhost:3004/update-indexes?token=XXXX

### Reindex collections in a provider

    curl -XPOST -H "Content-Type: application/json" http://localhost:3004/reindex-provider-collections?token=XXXX -d '["PROV1","PROV2"]'

### Reindex all tags

    curl -XPOST http://localhost:3004/reindex-tags?token=XXXX'

### Ignore version conflict

By default, version conflict returned from elasticsearch will be ignored. User can override the default by passing in query parameter "ignore_conflict=false" to the request.

### Message queues

The ingest application will publish messages for the indexer application to consume.  The messages will be to index or delete concepts from elasticsearch.  Messaging is handled using the message-queue-lib which uses RabbitMQ.

#### Message Queue Error Handling

##### Caught Error in the Indexer

If an error occurs in the indexer either because Elasticsearch is unavailable or an unexpected error occurs during indexing we will catch that error. The message will be placed on a Wait Queue as described in the message-queue-lib README. We will use an exponential backoff to retry after a set period of time. After the message has been successfully queued on the wait queue the indexer will acknowledge the message.

##### Uncaught Error in the Indexer

An uncaught error such as indexer dying or running out of memory will be handled through non-acknowledgment of the message. RabbitMQ will consider the messages as not having been processed and requeue it.

##### Alerts

The indexer has a background job that monitors the RabbitMQ message queue size and logs it. If the message queue size exceeds the configured size (CMR_INDEXER_WARN_QUEUE_SIZE) we will log extra infomation that splunk can detect. We will add a splunk alert to look for the log mesage indicating the queue size has exceeded threshhold and email CMR Operations.

## License

Copyright © 2014-2015 NASA
