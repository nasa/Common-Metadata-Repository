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

    curl -i -XPOST http://localhost:3004/caches/clear-cache?token=XXXX

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
  "message-queue": {
    "ok?": true
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
        "problem" : "db-spec cmr.common.memory_db.connection.MemoryStore@aead584 is missing a required parameter"
      },
      "echo" : {
        "ok?" : true
      }
    }
  },
  "message-queue": {
    "ok?": true
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

### Create index-set using json string

```
curl -i -H "Accept: application/json" -H "Content-type: application/json" -XPOST "http://localhost:3004/index-sets" -d "{\"index-set\":{\"name\":\"cmr-base-index-set\",\"create-reason\":\"include message about reasons for creating this index set\",\"granule\":{\"index-names\":[\"G2-PROV1\",\"G4-Prov3\",\"g5_prov5\"],\"mapping\":{\"granule\":{\"_all\":{\"enabled\":false},\"properties\":{\"collection-concept-id\":{\"store\":\"yes\",\"index_options\":\"docs\",\"norms\":\"false\",\"type\":\"string\",\"index\":\"not_analyzed\"},\"concept-id\":{\"store\":\"yes\",\"index_options\":\"docs\",\"norms\":\"false\",\"type\":\"string\",\"index\":\"not_analyzed\"}},\"dynamic\":\"strict\",\"_source\":{\"enabled\":false},\"_id\":{\"path\":\"concept-id\"}}},\"settings\":{\"index\":{\"number_of_replicas\":0,\"refresh_interval\":\"10s\",\"number_of_shards\":1}}},\"collection\":{\"index-names\":[\"C4-collections\",\"c6_Collections\"],\"mapping\":{\"collection\":{\"_all\":{\"enabled\":false},\"properties\":{\"entry-title\":{\"store\":\"yes\",\"index_options\":\"docs\",\"omit_norms\":\"true\",\"type\":\"string\",\"index\":\"not_analyzed\"},\"concept-id\":{\"store\":\"yes\",\"index_options\":\"docs\",\"omit_norms\":\"true\",\"type\":\"string\",\"index\":\"not_analyzed\"}},\"dynamic\":\"strict\",\"_source\":{\"enabled\":false},\"_id\":{\"path\":\"concept-id\"}}},\"settings\":{\"index\":{\"number_of_replicas\":0,\"refresh_interval\":\"20s\",\"number_of_shards\":1}}},\"id\":3}}"
```

### Get index-set by id

    curl -XGET "http://localhost:3004/index-sets/3"

### Get all index-sets

    curl -XGET "http://localhost:3004/index-sets"

### Delete index-set by id

    curl -XDELETE "http://localhost:3004/index-sets/3"

### Mark a collection as rebalancing

There are multiple granule indexes for performance. Larger collections are split out into their own indexes. Smaller collections are grouped in a small_collections index. When calling the endpoint the `target` query parameter is required, and it has two valid values, `separate-index` and `small-collections`. In either case the collection is added to the list of collections being rebalanced. If `target=separate-index` a new granule index is created in addition to updating the index-set.

    curl -XPOST http://localhost:3004/index-sets/3/rebalancing-collections/C5-PROV1/start?target=separate-index

### Finalize a rebalancing collection

Finalizing a rebalancing collection removes the collection from the list of collections are are being rebalanced and updates the index-set appropriately based on what the target destination was set to on the call to start.

    curl -XPOST http://localhost:3004/index-sets/3/rebalancing-collections/C5-PROV1/finalize

### Update a rebalancing collection's status

Make changes to the collection's rebalancing status. This will update a mapping of collection id to rebalancing status in the index-set.

    curl -XPOST http://localhost:3004/index-sets/3/rebalancing-collections/C5-PROV1/update-status?status=COMPLETE

### Reset for dev purposes

    curl -i -H "Accept: application/json" -H "Content-type: application/json" -XPOST "http://localhost:3004/reset"

### See indices listing

   curl http://localhost:9210/index_sets/_aliases?pretty=1

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

## Sample outputs

- Get all index-sets response
```
[{:id 3,
    :name "cmr-base-index-set",
    :concepts
    {:collection
     {:c6_Collections "3_c6_collections",
      :C4-collections "3_c4_collections"},
     :granule
     {:g5_prov5 "3_g5_prov5",
      :G4-Prov3 "3_g4_prov3",
      :G2-PROV1 "3_g2_prov1"}}}
   {:id 55,
    :name "cmr-base-index-set",
    :concepts
    {:collection
     {:c6_Collections "55_c6_collections",
      :C4-collections "55_c4_collections"},
     :granule
     {:g5_prov5 "55_g5_prov5",
      :G4-Prov3 "55_g4_prov3",
      :G2-PROV1 "55_g2_prov1"}}}]
```

- Get an index-set by id response
```
{:index-set
   {:concepts
    {:collection
     {:c6_Collections "3_c6_collections",
      :C4-collections "3_c4_collections"},
     :granule
     {:g5_prov5 "3_g5_prov5",
      :G4-Prov3 "3_g4_prov3",
      :G2-PROV1 "3_g2_prov1"}},
    :name "cmr-base-index-set",
    :create-reason
    "include message about reasons for creating this index set",
    :granule
    {:index-names ["G2-PROV1" "G4-Prov3" "g5_prov5"],
     :mapping
     {:granule
      {:_all {:enabled false},
       :properties
       {:collection-concept-id
        {:store "yes",
         :index_options "docs",
         :norms false,
         :type "string",
         :index "not_analyzed"},
        :concept-id
        {:store "yes",
         :index_options "docs",
         :norms false,
         :type "string",
         :index "not_analyzed"}},
       :dynamic "strict",
       :_source {:enabled false},
       :_id {:path "concept-id"}}},
     :settings
     {:index
      {:number_of_replicas 0,
       :refresh_interval "10s",
       :number_of_shards 1}}},
    :collection
    {:index-names ["C4-collections" "c6_Collections"],
     :mapping
     {:collection
      {:_all {:enabled false},
       :properties
       {:entry-title
        {:store "yes",
         :index_options "docs",
         :norms false,
         :type "string",
         :index "not_analyzed"},
        :concept-id
        {:store "yes",
         :index_options "docs",
         :norms false,
         :type "string",
         :index "not_analyzed"}},
       :dynamic "strict",
       :_source {:enabled false},
       :_id {:path "concept-id"}}},
     :settings
     {:index
      {:number_of_replicas 0,
       :refresh_interval "20s",
       :number_of_shards 1}}},
    :id 3}}
```

## License

Copyright © 2014-2021 NASA
