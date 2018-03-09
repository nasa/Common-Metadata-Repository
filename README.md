# cmr-graph

A Clojure library designed to ... well, that part is up to you.


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

Once all the containers are running, you can load up Kibana in a browser:

* http://localhost:5601/

What used to be Marvel in previous releases of Elasticsearch is now
available by navigating to the "DevTools" menu item, or this direct link:

* http://localhost:5601/app/kibana#/dev_tools/console

Stopping (in a different terminal) is done in a similar manner as start:

```
$ lein stop-infra
```

## Usage

TBD


## License

Copyright © 2018 NASA

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
