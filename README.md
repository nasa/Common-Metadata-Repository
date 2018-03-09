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
neo4j
```


### Bash [&#x219F;](#contents)

Should you wish to bring up a system shell on the containers, you can execute
any of the following:

* `resources/scripts/neo4j-bash.sh` (root user)
* `resources/scripts/elastic-bash.sh` (root user)
* `resources/scripts/kibana-bash.sh` (kibana user)


## Usage [&#x219F;](#contents)


### REST API [&#x219F;](#contents)

TBD


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
```
               ____
            ,dP9CGG88@b,
          ,IIIIYICCG888@@b,
         dIIIIIIIICGG8888@b
        dCIIIIIIICCGG8888@@b
        GCCIIIICCCGGG8888@@@
        GGCCCCCCCGGG88888@@@
        GGGGCCCGGGG88888@@@@
        Y8GGGGGG8888888@@@@P
         Y88888888888@@@@@P
         `Y8888888@@@@@@@P\
           |`@@@@@@@@@P'\  \
           | .  """"     \   \
           ' .            \   \         .d$#T!!!~"#*b.
           ' |             \    \     d$MM!!!!~~~     "h
           " |              \     \ dRMMM!!!~           ^k
           = "               \     $RMM!!~                .__
  ____   ____________         \  ________________  ______ |  |__
_/ ___\ /     \_  __ \  ______  / ___\_  __ \__  \ \____ \|  |  \
\  \___|  Y Y  \  | \/ /_____/ / /_/  >  | \// __ \|  |_> >   Y  \
 \___  >__|_|  /__|            \___  /|__|  (____  /   __/|___|  /
     \/      \/               /_____/            \/|__|        \/
      .X+.   .      ___----     'k~~                        :
    .Xx+-.     . '''  ____----""" 3>                        F
    XXx++-..     --'''            9>                       F
    XXxx++--..                     "i                    :"
    `XXXxx+++--'                     t.                .P
      `XXXxxx'                         #c.          .z#
         ""                               ^#*heee*#"

nREPL server started on port 52433 on host 127.0.0.1 - nrepl://127.0.0.1:52433
REPL-y 0.3.7, nREPL 0.2.12
Clojure 1.9.0
Java HotSpot(TM) 64-Bit Server VM 1.8.0_161-b12
    Docs: (doc function-name-here)
          (find-doc "part-of-name-here")
  Source: (source function-name-here)
 Javadoc: (javadoc java-object-or-class-here)
    Exit: Control+D or (exit) or (quit)
 Results: Stored in vars *1, *2, *3, an exception in *e

[cmr.graph.dev] λ=>
```

At this point, you're ready to bring up the CMR Graph system components:

```clj
[cmr.graph.dev] λ=> (startup)
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
