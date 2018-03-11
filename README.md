# cmr-graph

*A service and API for querying CMR metadata relationships*

[![][logo]][logo]

#### Contents

* [Setup](#setup-)
   * [Neo4j](#neo4j-)
   * [Kibana](#kibana-)
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


## Dependencies [&#x219F;](#contents)

* Java
* `lein`
* `docker`
* `docker-compose`

Note that your Docker installation needs have at least 4GB of memory allocate
to it in order for Elasticsearch and Kibana to run.


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
2018-03-10T00:07:18.922 [nREPL-worker-1] INFO cmr.graph.components.config:63 - Starting config component ...
2018-03-10T00:07:18.924 [nREPL-worker-1] INFO cmr.graph.components.logging:22 - Starting logging component ...
2018-03-10T00:07:18.925 [nREPL-worker-1] INFO cmr.graph.components.neo:24 - Starting Neo4j component ...
2018-03-10T00:07:18.933 [nREPL-worker-1] INFO cmr.graph.components.elastic:22 - Starting Elasticsearch component ...
2018-03-10T00:07:18.934 [nREPL-worker-1] INFO cmr.graph.components.httpd:23 - Starting httpd component ...
```

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

[repl]: resources/images/repl-screen.png
