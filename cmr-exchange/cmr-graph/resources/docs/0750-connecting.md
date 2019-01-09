# Connecting


**Contents**

* Web Interfaces
   * Neo4j Web Console
   * Kibana Web Console
   * Elasticsearch
* In-Container Shells
   * Neo4j Shell
   * Bash Shells


## Web Interfaces

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

If you want to use this setup for more then just a week or so, you'll want to
get a free Basic Elasticsearch license. To do this, navigate to
[the management tab][kibana-management] and click "Licsen Magagement" in the
main window, following the instructions for adding a new Basic license.


### Elasticsearch

The ports 9209 and 9210 are used in the CMR for different types of
Elasticsearch deployments, so we followed that trend. Elasticsearch web access
is available here:

* http://localhost:9211/


## In-Container Shells

### Neo4j Shell

You can run the Neo4j shell on the container by executing the following:

* `resources/scripts/neo4j-cypher.sh`

This will put you at the Cypher shell prompt:

```
Connected to Neo4j 3.3.3 at bolt://localhost:7687.
Type :help for a list of available commands or :exit to exit the shell.
Note that Cypher queries must end with a semicolon.
neo4j>
```


### Bash Shells

Should you wish to bring up a system shell on the containers, you can execute
any of the following:

* `resources/scripts/neo4j-bash.sh` (root user)
* `resources/scripts/elastic-bash.sh` (root user)
* `resources/scripts/kibana-bash.sh` (kibana user)


<!-- Named page links below: /-->

[neo4j-screen]: https://raw.githubusercontent.com/cmr-exchange/cmr-graph/master/resources/images/neo4j-web-screen.png
[neo4j-screen-thumb]: https://raw.githubusercontent.com/cmr-exchange/cmr-graph/master/resources/images/neo4j-web-screen-thumb.png

[kibana]: https://raw.githubusercontent.com/cmr-exchange/cmr-graph/master/resources/images/kibana.png
[kibana-thumb]: https://raw.githubusercontent.com/cmr-exchange/cmr-graph/master/resources/images/kibana-thumb.png

[kibana-query]: https://raw.githubusercontent.com/cmr-exchange/cmr-graph/master/resources/images/kibana-query.png
[kibana-query-thumb]: https://raw.githubusercontent.com/cmr-exchange/cmr-graph/master/resources/images/kibana-query-thumb.png
[kibana-management]: http://localhost:5601/app/kibana#/management
