# cmr-nlp

*A service for converting natural language queries into CMR search parameters*

[![Build Status][travis-badge]][travis]
[![Security Scan][security-scan-badge]][travis]
[![Dependencies Status][deps-badge]][travis]
[![Open Pull Requests][prs-badge]][prs]

[![Clojars Project][clojars-badge]][clojars]
[![Tag][tag-badge]][tag]

[![Clojure version][clojure-v]](project.clj)

[![][logo]][logo]


#### Contents

* [About](#about-)
* [Dependencies](#dependencies-)
* [Usage](#usage-)
   * [Setup](#setup-)
   * [NLP Library](#nlp-library-)
   * [NLP via Elasticsearch](#nlp-via-elasticsearch-)
   * [Geolocation via Elasticsearch](#geolocation-via-elasticsearch-)
* [License](#license-)


## About [&#x219F;](#contents)

This project aims to provide basic natural language processing (NLP) support
for the NASA Earthdata Common Metadata Repository (CMR) clients wishing for a
greater user experience when making queries to the CMR Search endpoints.
Initial focus is on NLP support for spatio-temporal queries.

Future focus will be on supporting collection, granule, and variable
identification from natural language queries.


## Dependencies [&#x219F;](#contents)

* Java
* `lein`
* `curl` (used to download English language models)
* `docker` and `docker-compose` (used to run local Elasticsearch cluster)

Supported versions:

| cmr-nlp        | Elasticsearch | Status         |
|----------------|---------------|----------------|
| 0.1.0-SNAPSHOT | 6.5.2         | In development |


## Usage [&#x219F;](#contents)

There are several ways in which this project may be used:

* the NLP portion of the codebase as a library (in-memory NLP models will be required)
* the Geolocation functionality as a service (an Elasticsearch cluster, local or otherwise, will be required)
* both NLP and Geolocation running as a service (no in-memory models; requires Elasticsearch cluster)

Each approach requires slightly different setup.

### Setup [&#x219F;](#contents)

#### In-Memory Models

If running just the NLP portion of the code as a library, you will need to have
the required OpenNLP models available to the JVM on the classpath. You may do
this easily in a cloned `cmr-nlp` directory with the following command:

```
$ lein download-models
```

This executes the script `resources/scripts/download-models`, which may be
adapted for use in your own project.

#### Elasticsearch

Starting up a local Elasticsearch+Kibana cluster is as simple as:

```
$ lein start-es
```

Note that this utilizes `docker-compose` under the hood.

Once started, Elasticsearch's Kibana interface will be available here:

* [http://localhost:5601/](http://localhost:5601/)


#### OpenNLP Elasticsearch Ingest

TBD


#### Geonames Elasticsearch Ingest

Before ingesting Geonames data, you need to

1. Start your Elasticsearch cluster (see above), and
1. Download the Geonames gazzette files locally:

```
$ lein download-geonames
```

Note that this will also `unzip` the two compressed files that get downloaded:

* `allCountries.zip` (340MB) uncompresses to 1.4GB
* `shapes_all_low.zip` (1MB) uncompresses to 3.1MB

With that done, you're ready to ingest the Geonames files into Elasticsearch:

```
$ lein ingest
```


### NLP Library [&#x219F;](#contents)

Start up a repl, do a require, and define a testing query:

```
$ lein repl
```

```clj
(require '[cmr.nlp.core :as nlp])
(def query "What was the average surface temperature of Lake Superior last week?")
```

Tokenize:

```clj
[cmr.nlp.repl] λ=> (def tokens (nlp/tokenize query))
[cmr.nlp.repl] λ=> tokens
["What"
 "was"
 "the"
 "average"
 "surface"
 "temperature"
 "of"
 "Lake"
 "Superior"
 "last"
 "week"
 "?"]
```

Tag the parts of speech:

```clj
[cmr.nlp.repl] λ=> (def pos (nlp/tag-pos tokens))
[cmr.nlp.repl] λ=> pos
(["What" "WP"]
 ["was" "VBD"]
 ["the" "DT"]
 ["average" "JJ"]
 ["surface" "NN"]
 ["temperature" "NN"]
 ["of" "IN"]
 ["Lake" "NNP"]
 ["Superior" "NNP"]
 ["last" "JJ"]
 ["week" "NN"]
 ["?" "."])
```

Get chunked phrases:

```clj
[cmr.nlp.repl] λ=> (nlp/chunk pos)
({:phrase ["What"] :tag "NP"}
 {:phrase ["was"] :tag "VP"}
 {:phrase ["the" "average" "surface" "temperature"] :tag "NP"}
 {:phrase ["of"] :tag "PP"}
 {:phrase ["Lake" "Superior"] :tag "NP"}
 {:phrase ["last" "week"] :tag "NP"})
```

Find locations:

```clj
[cmr.nlp.repl] λ=> (nlp/find-locations tokens)
("Lake Superior")
```

Find dates:

```clj
[cmr.nlp.repl] λ=> (nlp/find-dates tokens)
("last week")
```

Get actual dates from English sentences:

```clj
[cmr.nlp.repl] λ=> (nlp/extract-dates query)
(#inst "2018-11-27T21:40:12.946-00:00")
```

This is returned as a collection due to the fact that a query may have more
than one date (i.e., indicate a range):

```clj
[cmr.nlp.repl] λ=> (def query2 "What was the average high temp between last year and two years ago?")
[cmr.nlp.repl] λ=> (nlp/extract-dates query2)
(#inst "2017-12-04T21:42:42.874-00:00"
 #inst "2016-12-04T21:42:42.878-00:00")
```

Create a CMR temporal parameter query string from a natural language sentence:

```clj
[cmr.nlp.repl] λ=> (require '[cmr.nlp.query :as query])
[cmr.nlp.repl] λ=> (query/->cmr-temporal {:query query2})
{:query "What was the average high temp between last year and two years ago?"
 :temporal "temporal%5B%5D=2016-12-12T13%3A58%3A05Z%2C2017-12-12T13%3A58%3A05Z"}
```

Which, when URL-decoded, gives us:

```
"temporal[]=2016-12-05T12:21:32Z,2017-12-05T12:21:32Z"
```


### NLP via Elasticsearch [&#x219F;](#contents)

TBD


### Geolocation via Elasticsearch [&#x219F;](#contents)

TBD


## License [&#x219F;](#contents)

Copyright © 2018 NASA

Distributed under the Apache License, Version 2.0.


<!-- Named page links below: /-->

[logo]: https://avatars2.githubusercontent.com/u/32934967?s=200&v=4
[travis]: https://travis-ci.org/cmr-exchange/cmr-nlp
[travis-badge]: https://travis-ci.org/cmr-exchange/cmr-nlp.png?branch=master
[deps-badge]: https://img.shields.io/badge/deps%20check-passing-brightgreen.svg
[tag-badge]: https://img.shields.io/github/tag/cmr-exchange/cmr-nlp.svg
[tag]: https://github.com/cmr-exchange/cmr-nlp/tags
[clojure-v]: https://img.shields.io/badge/clojure-1.9.0-blue.svg
[clojars]: https://clojars.org/gov.nasa.earthdata/cmr-nlp
[clojars-badge]: https://img.shields.io/clojars/v/gov.nasa.earthdata/cmr-nlp.svg
[security-scan-badge]: https://img.shields.io/badge/dependency%20check%20security%20scan-passing-brightgreen.svg
[prs]: https://github.com/pulls?utf8=%E2%9C%93&q=is%3Aopen+is%3Apr+org%3Acmr-exchange+archived%3Afalse+
[prs-badge]: https://img.shields.io/badge/Open%20PRs-org-yellow.svg
