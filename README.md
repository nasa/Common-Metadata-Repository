# A Clojure(Script) CMR Client

<!--
[![Build Status][travis badge]][travis]
[![Dependencies Status][deps-badge]][deps]
/-->
[![Clojars Project][clojars-badge]][clojars] [![Clojure version][clojure-v]](project.clj)
<!--
[![Tag][tag-badge]][tag]
/-->

A Clojure(Script) Client for NASA's Common Metadata Repository

[![][logo]][logo]


## Background

There are three major API endpoints for the CMR:

* /access-control - [Access Control API docs][ac-api-docs]
* /ingest - [Ingest API docs][ingest-api-docs]
* /search - [Search API docs][search-api-docs]

The last of these is the largest and most-used API. Regardless, this client
project aims to support them all, each in their own namespace. Respectively:

* `cmr.client.ac`
* `cmr.client.ingest`
* `cmr.client.search`


## Usage

*WIP*

This project has only just started, but that being said, here's what you can
do so far:


### Clojure

```bash
$ lein repl
```
```clj
(def client (ingest/create-client {:endpoint :local :return-body? true}))
(def results (ingest/get-providers client))
(pprint results)
[{:provider-id "PROV1",
  :short-name "PROV1",
  :cmr-only true,
  :small false}
 {:provider-id "PROV2",
  :short-name "PROV2",
  :cmr-only true,
  :small false}
 {:provider-id "PROV3",
  :short-name "PROV3",
  :cmr-only true,
  :small false}]
```


### ClojureScript

Start a `rhino` REPL:

```bash
$ lein rhino-repl
```
```clj
(require '[cmr.client.ingest.core :as ingest])
(def client (ingest/create-client {:endpoint :local :return-body? true}))
(def results (ingest/get-providers client))
```


## License

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
