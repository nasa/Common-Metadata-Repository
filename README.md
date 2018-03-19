# cmr-graph

*A service and API for querying CMR metadata relationships*

[![][logo]][logo]


#### Contents

* [About](#about-)
* [Documentation](#documentation-)
  * [Dependencies](#dependencies-)
  * [Quick Start](#quick-start-)
  * [Example Usage](#example-usage-)
  * [Guides](#guides-)
  * [Reference](#reference-)
* [License](#license-)


## About [&#x219F;](#contents)

This is an experimental project created as part of a 2-day Hackfest for
improved relational data and discovery for NASA's Earthdata Search system.
In particular, here we are exploring the use of graph databases and related
indexing methodologies with the aim of helping uses find out more about the
Earth science data they care about.

Future explorations:

* Apache Tinkerpop
* JanusGraph
* Clojure Ogre


## Documentation [&#x219F;](#contents)


### Dependencies [&#x219F;](#contents)

* Java
* `lein`
* `docker`
* `docker-compose`

Note that your Docker installation needs have at least 4GB of memory allocate
to it in order for Elasticsearch and Kibana to run.


### Quick Start [&#x219F;](#contents)

For an interactive environment:
```
$ lein start-infra
$ lein repl
```
```clj
[cmr.graph.dev] λ=> (startup)
```

For a non-interactive environment:

```
$ lein start-infra
$ lein start-cmr-graph
```

Be sure to check out the "Documentation" section below to links with more
details on setup and use of CMR Graph.


### Example Usage [&#x219F;](#contents)

For a quick taste of using CMR Graph, we've put a snippet of code below.
See the links in the "Documentation" section below for more detailed examples
and other usage.

```
TBD
```


### Guides [&#x219F;](#contents)

* [Setup][setup-docs]
* [Conecting to the Infrastructure][connecting-docs]
* [Usage Examples][usage-docs]
* [Development Environment][dev-docs]


### Reference [&#x219F;](#contents)

* [API Reference][api-docs]
* [Marginalia][marginalia-docs]


## License [&#x219F;](#contents)

Copyright © 2018 NASA

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.


<!-- Named page links below: /-->

[logo]: https://avatars2.githubusercontent.com/u/32934967?s=200&v=4
[api-docs]: http://cmr-exchange.github.io/cmr-graph/current/
[marginalia-docs]: http://cmr-exchange.github.io/cmr-graph/current/9800-marginalia.html
[setup-docs]: http://cmr-exchange.github.io/cmr-graph/current/0500-setup.html
[connecting-docs]: http://cmr-exchange.github.io/cmr-graph/current/0750-connecting.html
[usage-docs]: http://cmr-exchange.github.io/cmr-graph/current/1000-usage.html
[dev-docs]: http://cmr-exchange.github.io/cmr-graph/current/2000-dev.html
