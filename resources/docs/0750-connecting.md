# Connecting


**Contents**

* [Neo4j Shell](#neo4j-shell-)
* [Bash](#bash-)


## Neo4j Shell [&#x219F;](#contents)

You can run the Neo4j shell on the container by executing the following:

* `resources/scripts/neo4j-cypher.sh`

This will put you at the Cypher shell prompt:

```
Connected to Neo4j 3.3.3 at bolt://localhost:7687.
Type :help for a list of available commands or :exit to exit the shell.
Note that Cypher queries must end with a semicolon.
neo4j>
```


## Bash [&#x219F;](#contents)

Should you wish to bring up a system shell on the containers, you can execute
any of the following:

* `resources/scripts/neo4j-bash.sh` (root user)
* `resources/scripts/elastic-bash.sh` (root user)
* `resources/scripts/kibana-bash.sh` (kibana user)
