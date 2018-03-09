# cmr-graph

*A service and API for querying CMR metadata relationships*

[![][logo]][logo]


## Setup

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


### Neo4j

Load up the web interface for Neo4j:

* http://localhost:7474/browser/

[![][neo4j-screen-thumb]][neo4j-screen]


### Kibana

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


## Connecting


### Neo4j Shell

* `resources/scripts/neo4j-cypher.sh`


### Bash

Should you wish to bring up a system shell on the containers, you can execute
any of the following:

* `resources/scripts/neo4j-bash.sh` (root user)
* `resources/scripts/elastic-bash.sh` (root user)
* `resources/scripts/kibana-bash.sh` (kibana user)


## Usage

TBD


## License

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
