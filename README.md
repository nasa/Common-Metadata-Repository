# cmr-ingest-app

This is the ingest component of the CMR system. It is responsible for collaborating with metadata db and indexer components of the CMR system to maintain the lifecycle of concepts coming into the system.

## Prerequisites

You will need [Leiningen][1] 1.7.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein ring server

## Curl statements
- ensure Metadata db, ES, Indexer, Ingest are functioning
curl -i -v  -X PUT -H "Content-Type:application/json" -H "Accept:application/json" --data '{"concept-type":"collection","metadata":"xml md here ","format":"echo10"}' http://localhost:3002/providers/PROV009/collections/nativeId8

## License

Copyright © 2014 NASA
