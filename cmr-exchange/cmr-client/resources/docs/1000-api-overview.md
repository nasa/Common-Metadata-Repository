# CMR API Overview


## Endpoints & Upstream Documentation

There are three major API endpoints for the CMR:

* `/search` - [Search API docs][search-api-docs]
* `/ingest` - [Ingest API docs][ingest-api-docs]
* `/access-control` - [Access Control API docs][ac-api-docs]

The `/search` endpoint is the largest and most-used API. Regardless, this
client project aims to support them all, each in their own namespace.
Respectively:

* `cmr.client.search`
* `cmr.client.ingest`
* `cmr.client.ac`


## Environments

By default, new clients are instantiated to run against CMR in production. You
may override this with the `:endpoint` key in the options passed to
`create-client`, e.g., `(create-client {:endpoint :local ...})`. The following
keys are used to create clients against their respective CMR deployments:

* `:prod`
* `:uat`
* `:sit`
* `:local`

The last one being for developers running an instance of CMR locally.


## Client Implementations

This project defines a unified Clojure+ClojureScript implementation of a CMR
client. It also generates a JavaScript build that allows one to use the client
from web pages and JavaScript-based desktop and mobile applications.

For more information on the clients, see the [Guides][guides].


<!-- Named page links below: /-->

[ac-api-docs]: https://cmr.earthdata.nasa.gov/access-control/site/docs/access-control/api.html
[ingest-api-docs]: https://cmr.earthdata.nasa.gov/ingest/site/docs/ingest/api.html
[search-api-docs]: https://cmr.earthdata.nasa.gov/search/site/docs/search/api.html
[guides]: https://cmr-exchange.github.io/cmr-client/current/3000-guides.html
