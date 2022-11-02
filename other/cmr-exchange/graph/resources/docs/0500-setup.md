# Setup


**Contents**

* Start
* Stop
* CMR Graph System


Docker is used to provide developers with the infrastructure necessary to
work effectively on CMR Graph.


### Start

To start this supporting infrastructure, you can use the `lein` alias we
have provided:

```
$ lein start-infra
```

That will use `docker-compose` to start instances of Neo4j, Elasticsearch, and
Kibana --  each in their own container.

Note that this command uses `docker-compose`; Mac users get this for free when
they install Docker, but Linux users will need to install it manually.

When the docker containers start, a local `data` directory will be created which
is used by each service so that we don't loose crucial information between
restarts of the containers.

Once all the containers are running, you can access web resources via
localhost, as demonstrated in the "Connecting" documentation.


### Stop

Stopping (in a different terminal) is done in a similar manner as start:

```
$ lein stop-infra
```


### CMR Graph System

If you don't need the REPL, you can start the CMR Graph system with the
following:

```
$ lein start-cmr-graph
```
