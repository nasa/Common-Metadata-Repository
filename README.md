# cmr-graph

*A service and API for querying CMR metadata relationships*

[![][logo]][logo]


#### Contents

* [About](#about-)
* [Dependencies](#dependencies-)
* [Quick Start](#quick-start-)
* [Setup](#setup-)
   * [Neo4j](#neo4j-)
   * [Kibana](#kibana-)
   * [Elasticsearch](#elasticsearch-)
* [Connecting](#connecting-)
   * [Neo4j Shell](#neo4j-shell-)
   * [Bash](#bash-)
* [Usage](#usage-)
   * [REST API](#rest-api-)
   * [JVM Library](#jvm-library-)
   * [Client Library](#client-library-)
* [Deploying with CMR](#deploying-with-cmr-)
* [Development](#development-)
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


## Dependencies [&#x219F;](#contents)

* Java
* `lein`
* `docker`
* `docker-compose`

Note that your Docker installation needs have at least 4GB of memory allocate
to it in order for Elasticsearch and Kibana to run.


## Quick Start [&#x219F;](#contents)

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

Be sure to check out the rest of this README fore more details on setup and use
of CMR Graph!


## Setup [&#x219F;](#contents)

Start supporting infrastructure:

```
$ lein start-infra
```

That will use Docker to start instances of Neo4j, Elasticsearch, and Kibana
each in their own container. This command uses `docker-compose`; Mac users
get this for free when they install Docker, but Linux users will need to
install it manually.

This will create a local `data` directory which will be used by each service
so that we don't loose anything saved between restarts of the containers.

Once all the containers are running, you can access web resources via
localhost, as shown in the following subsections:


### Neo4j [&#x219F;](#contents)

Load up the web interface for Neo4j:

* http://localhost:7474/browser/

[![][neo4j-screen-thumb]][neo4j-screen]


### Kibana [&#x219F;](#contents)

Load up Kibana in a browser:

* http://localhost:5601/

[![][kibana-thumb]][kibana]

What used to be Marvel in previous releases of Elasticsearch is now
available by navigating to the "DevTools" menu item, or this direct link:

* http://localhost:5601/app/kibana#/dev_tools/console

[![][kibana-query-thumb]][kibana-query]

Stopping (in a different terminal) is done in a similar manner as start:

```
$ lein stop-infra
```

If you don't need the REPL, you can start the CMR Graph system with the
following:

```
$ lein start-cmr-graph
```


### Elasticsearch  [&#x219F;](#contents)

If you want to use this setup for more then just a week or so, you'll want to
get a free Basic Elasticsearch license. To do this, navigate to
[the management tab][kibana-management] and click "Licsen Magagement" in the
main window, following the instructions for adding a new Basic license.


## Connecting [&#x219F;](#contents)


### Neo4j Shell [&#x219F;](#contents)

You can run the Neo4j shell on the container by executing the following:

* `resources/scripts/neo4j-cypher.sh`

This will put you at the Cypher shell prompt:

```
Connected to Neo4j 3.3.3 at bolt://localhost:7687.
Type :help for a list of available commands or :exit to exit the shell.
Note that Cypher queries must end with a semicolon.
neo4j>
```


### Bash [&#x219F;](#contents)

Should you wish to bring up a system shell on the containers, you can execute
any of the following:

* `resources/scripts/neo4j-bash.sh` (root user)
* `resources/scripts/elastic-bash.sh` (root user)
* `resources/scripts/kibana-bash.sh` (kibana user)


## Usage [&#x219F;](#contents)


### REST API [&#x219F;](#contents)

The CMR Graph REST API is accessible via the endpoint
[http://localhost:3012](http://localhost:3012), following the port numbering
conventions of the CMR.

For this endpoint, the following resources have been defined:

* `GET /health`
* `GET /ping`
* `POST /ping`
* `GET /demo/movie/graph/:limit`
* `GET /demo/movie/search?q=`
* `GET /demo/movie/title/:title`

Examples:

```
$ curl --silent "http://localhost:3012/health"|jq .
```
```json
{
  "config": {
    "ok?": true
  },
  "httpd": {
    "ok?": true
  },
  "elastic": {
    "ok?": true
  },
  "logging": {
    "ok?": true
  },
  "neo4j": {
    "ok?": true
  }
}
```
```
$ curl --silent "http://localhost:3012/demo/movie/search?q=Matrix"|jq .
```
```json
[
  {
    "movie": {
      "tagline": "Welcome to the Real World",
      "title": "The Matrix",
      "released": 1999
    }
  },
  {
    "movie": {
      "tagline": "Free your mind",
      "title": "The Matrix Reloaded",
      "released": 2003
    }
  },
  {
    "movie": {
      "tagline": "Everything that has a beginning has an end",
      "title": "The Matrix Revolutions",
      "released": 2003
    }
  }
]
```

### JVM Library [&#x219F;](#contents)

TBD


### Client Library  [&#x219F;](#contents)

TBD


## Deploying with CMR [&#x219F;](#contents)

TBD


## Development [&#x219F;](#contents)

To write new code for CMR Graph, you'll need to have the infrastructure running
as described above (see the "Setup" section) and then you'll want to start up
the REPL:

```
$ lein repl
```
[![][repl]][repl]

At this point, you're ready to bring up the CMR Graph system components:

```clj
[cmr.graph.dev] λ=> (startup)
```

This will start the following CMR Graph components:

* Configuration
* Logging
* A Neo4j connection
* An Elasticsearch connection
* The CMR Graph HTTP server for the REST API

as the log messages show:

```
2018-03-09T17:13:42.947 [nREPL-worker-0] INFO c.g.c.config:35 - Starting config component ...
2018-03-09T17:13:42.987 [nREPL-worker-0] INFO c.g.c.logging:22 - Starting logging component ...
2018-03-09T17:13:42.988 [nREPL-worker-0] INFO c.g.c.neo4j:21 - Starting Neo4j component ...
2018-03-09T17:13:42.992 [nREPL-worker-0] INFO c.g.c.elastic:21 - Starting Elasticsearch component ...
2018-03-09T17:13:42.993 [nREPL-worker-0] INFO c.g.c.httpd:22 - Starting httpd component ...
```

A convenience function has been provided for use in the REPL which returns
the dynamic var where the system state is stored:

```clj
[cmr.graph.dev] λ=> (system)
```

Convenience wrappers have been provided for the movie demo functions in the
REPL dev namespaces, automatically pulling the Neo4j connection information
from the system data structure:

```clj
[cmr.graph.dev] λ=> (pprint (search-movie "Matr"))
({:movie
  {:tagline "Welcome to the Real World",
   :title "The Matrix",
   :released 1999}}
 {:movie
  {:tagline "Free your mind",
   :title "The Matrix Reloaded",
   :released 2003}}
 {:movie
  {:tagline "Everything that has a beginning has an end",
   :title "The Matrix Revolutions",
   :released 2003}})
nil
```

```clj
[cmr.graph.dev] λ=> (pprint (get-movie "The Matrix"))
{"title" "The Matrix",
 "cast"
 [{:role ["Emil"], :name "Emil Eifrem", :job "acted"}
  {:role nil, :name "Joel Silver", :job "produced"}
  {:role nil, :name "Lana Wachowski", :job "directed"}
  {:role nil, :name "Lilly Wachowski", :job "directed"}
  {:role ["Agent Smith"], :name "Hugo Weaving", :job "acted"}
  {:role ["Morpheus"], :name "Laurence Fishburne", :job "acted"}
  {:role ["Trinity"], :name "Carrie-Anne Moss", :job "acted"}
  {:role ["Neo"], :name "Keanu Reeves", :job "acted"}]}
nil
```

```clj
[cmr.graph.dev] λ=> (pprint (get-movie-graph 100))
{:nodes
 ({:title "Apollo 13", :label :movie}
  {:title "Tom Hanks", :label :actor}
  {:title "Kevin Bacon", :label :actor}
  ...)
 :links
 ({:target 0, :source 1}
  {:target 0, :source 2}
  {:target 0, :source 3})}
nil
```

Additional convenience functions provided in the `cmr.graph.dev` namespace,
for use in the REPL:

* `banner`
* `current-health`
* `refresh` (does a reload of changed namespaces; shouldn't be used with a
  running system)
* `reset` (stops a running system, reloads the changed namespaces, and restarts
  the system)


## License [&#x219F;](#contents)

Copyright © 2018 NASA

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.


<!-- Named page links below: /-->

[logo]: https://avatars2.githubusercontent.com/u/32934967?s=200&v=4

[neo4j-screen]: resources/images/neo4j-web-screen.png
[neo4j-screen-thumb]: resources/images/neo4j-web-screen-thumb.png

[kibana]: resources/images/kibana.png
[kibana-thumb]: resources/images/kibana-thumb.png

[kibana-query]: resources/images/kibana-query.png
[kibana-query-thumb]: resources/images/kibana-query-thumb.png
[kibana-management]: http://localhost:5601/app/kibana#/management

[repl]: resources/images/repl-screen.png
