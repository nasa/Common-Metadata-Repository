# cmr-ingest-app

FIXME

## Prerequisites

You will need [Leiningen][1] 1.7.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein ring server

## Curl statements
- ensure metadata db is available on port 3001
curl -i -v -H "Content-type:application/json" "http://localhost:3002/providers/PROV009/collections/echocollid" -d '{"metadata":"xml here","format":"echo10”, “concept-id”:"CP009”}'

## License

Copyright © 2014 NASA
