# cmr-indexer-app

This is the indexer application for the CMR. It is responsible for indexing modified data into Elasticsearch.

## Prerequisites

You will need [Leiningen][1] 1.7.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## config

Copy config/elasticsearch_config.json.template to config/elasticsearch_config.json

Replace the placeholder values of Elasticsearch configuration parameters with the appropriate values in config/elasticsearch_config.json

## Running

To start a web server for the application, run:

    lein ring server

### Index a concept

curl -i -XPOST -H "Content-Type: application/json" http://localhost:3004 -d '{"concept-id": "C1234-PROV1", "revision-id": "1"}'

### Delete a concept

curl -i -XDELETE -H "Content-Type: application/json" http://localhost:3004/C1234-PROV1/2

### Ignore version conflict

By default, version conflict returned from elasticsearch will be ignored. User can override the default by passing in query parameter "ignore_conflict=false" to the request.

## License

Copyright © 2014 NASA
