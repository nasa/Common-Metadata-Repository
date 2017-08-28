# A Clojure(Script) CMR Client

<!--
[![Build Status][travis badge]][travis]
[![Dependencies Status][deps-badge]][deps]
/-->
[![Clojars Project][clojars-badge]][clojars] [![Clojure version][clojure-v]](project.clj)
<!--
[![Tag][tag-badge]][tag]
/-->

*A Clojure(Script) Client for NASA's Common Metadata Repository*

[![][logo]][logo]


#### Contents

* [Background](#background-)
* [CMR API](#cmr-api-)
  * [Overview](#overview)
  * [Environments](#environments)
* [Usage](#usage-)
  * [Clojure](#clojure-)
  * [ClojureScript](#clojurescript-)
  * [JavaScript](#javascript-)
* [License](#license-)


## Background [&#x219F;](#contents)

The [Common Metadata Repository][cmr-project] (CMR) is a high-performance,
high-quality, continuously evolving metadata system that catalogs all data and
service metadata records for the [EOSDIS][eosdis] system and will be the
authoritative management system for all EOSDIS metadata. These metadata records
are registered, modified, discovered, and accessed through programmatic
interfaces leveraging standard protocols and APIs.

Code for the CMR is up on [github][cmr-github].


## CMR API [&#x219F;](#contents)


### Overview

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


### Environments

By default, new clients are instantiated to run against CMR in production. You
may override this with the `:endpoint` key in the options passed to
`create-client`, e.g., `(create-client {:endpoint :local ...})`. The following
keys are used to create clients against their respective CMR deployments:

* `:prod`
* `:uat`
* `:sit`
* `:local`

The last one being for developers running an instance of CMR locally.


## Usage [&#x219F;](#contents)

*WIP*

This project has only just started, but that being said, here's what you can
do so far:


### Clojure [&#x219F;](#contents)

```bash
$ lein repl
```
```clj
(def client (ingest/create-client))
(def results (ingest/get-providers client))
(pprint results)
```
```clj
{:request-time 1007,
 :repeatable? false,
 :protocol-version {:name "HTTP", :major 1, :minor 1},
 :streaming? true,
 :chunked? false,
 :reason-phrase "OK",
 :headers
 {"CMR-Request-Id" "5b2b1090-bd3f-4776-a903-168c34f7a44c",
  "Content-Type" "application/json; charset=utf-8",
  "Date" "Sun, 27 Aug 2017 06:25:06 GMT",
  "Server" "nginx",
  "Strict-Transport-Security" "max-age=31536000; includeSubDomains;",
  "Vary" "Accept-Encoding, User-Agent",
  "Connection" "keep-alive"},
 :orig-content-encoding nil,
 :status 200,
 :length -1,
 :body
 [{:provider-id "LARC_ASDC",
   :short-name "LARC_ASDC",
   :cmr-only false,
   :small false}
   ...]}
```

Or, if you just want the body:

```clj
(def client (ingest/create-client {:return-body? true}))
(def results (ingest/get-providers client))
(pprint results)
```
```clj
[{:provider-id "LARC_ASDC",
  :short-name "LARC_ASDC",
  :cmr-only false,
  :small false}
 {:provider-id "USGS_EROS",
  :short-name "USGS_EROS",
  :cmr-only true,
  :small false}
 ...]
```


### ClojureScript [&#x219F;](#contents)

Start a the figwheel REPL:

```bash
$ rlwrap lein figwheel
```

Then open up a browser to
[http://localhost:3449/dev](http://localhost:3449/dev) so that the REPL can
connect to it.

Back in the terminal where you started the REPL:
```clj
(require '[cmr.client.ingest :as ingest]
         '[cmr.client.common.util :refer [with-callback]])
(def client (ingest/create-client))
(def ch (ingest/get-providers client))
```

The ClojureScript API uses the cljs-http library, so requests return a
`core.async` channel. However, if you don't want to work with channels, you
can easily process results in a callback:

```clj
(with-callback ch #(println "Got response:" %))
```
Which gives:
```clj
Got response: {:status 200, :success true, :body [{:provider-id LARC_ASDC ...
```

If you don't need the full response and are just after the data, you can
do the same thing that the Clojure CMR client API does when creating the
client:

```clj
(def client (ingest/create-client {:return-body? true}))
(def ch (ingest/get-providers client))
(with-callback ch #(println "Got body:" %))
```
```
Got body: [{:provider-id LARC_ASDC, :short-name LARC_ASDC, :cmr-only false ...}
```


## JavaScript [&#x219F;](#contents)

You can use the compiled ClojureScript client in the browser like so:

```js
var client = cmr.client.ingest.create_client({"return-body?": true});
var channel = cmr.client.ingest.get_providers(client);
cmr.client.common.util.with_callback(channel, function(data) {
  alert("Got body: " + data);
});
```
Then you'll get an `alert` dialog with the following content:
```clj
Got body: [{:provider-id LARC_ASDC, :short-name LARC_ASDC, :cmr-only false ...}
```


## License [&#x219F;](#contents)

Copyright © 2017 Duncan McGreggor

Distributed under the Apache License, Version 2.0.


<!-- Named page links below: /-->

[logo]: misc/images/ohboyohboyohboy.gif
[travis]: https://travis-ci.org/oubiwann/cmr-client
[travis badge]: https://img.shields.io/travis/oubiwann/cmr-client.svg

[deps]: http://jarkeeper.com/gov.nasa.earthdata/cmr-client
[deps-badge]: http://jarkeeper.com/clojusc/gov.nasa.earthdata/cmr-client.svg
[tag-badge]: https://img.shields.io/github/tag/gov.nasa.earthdata/cmr-client.svg
[tag]: https://github.com/clojusc/dragon/tags
[clojure-v]: https://img.shields.io/badge/clojure-1.8.0-blue.svg
[jdk-v]: https://img.shields.io/badge/jdk-1.7+-blue.svg
[clojars]: https://clojars.org/gov.nasa.earthdata/cmr-client
[clojars-badge]: https://img.shields.io/clojars/v/gov.nasa.earthdata/cmr-client.svg

[ac-api-docs]: https://cmr.earthdata.nasa.gov/access-control/site/docs/access-control/api.html
[ingest-api-docs]: https://cmr.earthdata.nasa.gov/ingest/site/docs/ingest/api.html
[search-api-docs]: https://cmr.earthdata.nasa.gov/search/site/docs/search/api.html
[cmr-project]: https://earthdata.nasa.gov/about/science-system-description/eosdis-components/common-metadata-repository
[cmr-github]: https://github.com/nasa/Common-Metadata-Repository
[eosdis]: https://earthdata.nasa.gov/about
