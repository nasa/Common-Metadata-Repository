# Setup


**Contents**

* [Neo4j](#neo4j-)
* [Kibana](#kibana-)
* [Elasticsearch](#elasticsearch-)


Docker is used to provide developers with the infrastructure necessary to
work effectively on CMR Graph.

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
localhost, as shown in the following subsections.


## Neo4j [&#x219F;](#contents)

Load up the web interface for Neo4j:

* http://localhost:7474/browser/

[![][neo4j-screen-thumb]][neo4j-screen]


## Kibana [&#x219F;](#contents)

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

## Elasticsearch [&#x219F;](#contents)

If you want to use this setup for more then just a week or so, you'll want to
get a free Basic Elasticsearch license. To do this, navigate to
[the management tab][kibana-management] and click "Licsen Magagement" in the
main window, following the instructions for adding a new Basic license.


<!-- Named page links below: /-->

[neo4j-screen]: resources/images/neo4j-web-screen.png
[neo4j-screen-thumb]: resources/images/neo4j-web-screen-thumb.png

[kibana]: resources/images/kibana.png
[kibana-thumb]: resources/images/kibana-thumb.png

[kibana-query]: resources/images/kibana-query.png
[kibana-query-thumb]: resources/images/kibana-query-thumb.png
[kibana-management]: http://localhost:5601/app/kibana#/management
