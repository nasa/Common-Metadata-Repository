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
indexing methodologies with the aim of helping users find out more about the
Earth science data they care about.

Future explorations:

* Apache TinkerPop
* JanusGraph
* Clojure Ogre

A separate "CMR Recommend" service that utilizes the graph database service
to identify data sets the Earthdata Search user may be interested in.


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
```

Note that Elasticsearch and Kibana used in this setup need 4GB of free space
in Docker; be sure your settings have been updated to account for this.

Then, in another terminal:
```
$ lein repl
```
```clj
[cmr.graph.dev] λ=> (startup)
```

For a non-interactive environment:
```
$ lein start-infra
```
and:
```
$ lein start-cmr-graph
```

#### Ingesting Demo Data

If you want to exercise the movie demo portions of CMR Graph, you will need to
ingest the movie data. In particular, once the infrastructure is up:

1. Visit the Neo4j web console at http://localhost:7474/browser/.
1. In the "Jump into code" box, click the "Write Code" button.
1. In the new section that appears at the top of the page, click the "Create
   a Graph" button in the "Movie Graph" box.
1. In the tutorial carousel that appears at the top of the page, click the
   right arrow.
1. In the next frame of the carousel, there is a little "play" button at the
   top left of the code (right before the first `CREATE` statement): click it.
1. A code section now appears at the top of the page with a "play" button at
   the far right: click it.

At this point, your instance of Neo4j should have the demo movie data.

#### Ingesting Collection Data

1. `CREATE CONSTRAINT ON (url:Url) ASSERT url.name IS UNIQUE`
2. `CREATE CONSTRAINT ON (urlType:UrlType) ASSERT urlType.name IS UNIQUE`
3. `CREATE CONSTRAINT ON (coll:Collection) ASSERT coll.md5Leo IS UNIQUE`
4. `CREATE CONSTRAINT ON (dataCenter:DataCenter) ASSERT dataCenter.name IS UNIQUE`
5.  ```cypher
    LOAD CSV WITH HEADERS FROM "https://raw.githubusercontent.com/cmr-exchange/cmr-graph/master/resources/data/collections.csv" AS csvLine
    MERGE (format:MetadataFormat {name: csvLine.MetadataFormat})
    MERGE (version:Version {name: csvLine.VersionId})
    MERGE (provider:Provider {name: csvLine.ProviderId})
    CREATE (coll:Collection {md5Leo: csvLine.MD5Leo, conceptId: csvLine.ConceptId})
    CREATE (coll)-[:OWNED_BY]->(provider)
    CREATE (coll)-[:FORMATTED_IN]->(format)
    CREATE (coll)-[:VERSION_IS]->(version)
    ```
6.  ```cypher
    USING PERIODIC COMMIT 500
    LOAD CSV WITH HEADERS FROM "https://raw.githubusercontent.com/cmr-exchange/cmr-graph/master/resources/data/collection_and_urls.csv" AS csvLine
    MATCH (coll:Collection { md5Leo: csvLine.CollectionMD5Leo})
    MERGE (url:Url { name: csvLine.URL})
    MERGE (urlType:UrlType { name: csvLine.URLType})
    CREATE (coll)-[:LINKS_TO]->(url)
    CREATE (url)-[:HAS_TYPE]->(urlType)
    ```
7.  ```cypher
    USING PERIODIC COMMIT 500
    LOAD CSV WITH HEADERS FROM "https://raw.githubusercontent.com/cmr-exchange/cmr-graph/master/resources/data/collection_and_data_centers.csv" AS csvLine
    MATCH (coll:Collection { md5Leo: csvLine.CollectionMD5Leo})
    MERGE (dataCenter:DataCenter { name: csvLine.DataCenter})
    CREATE (coll)-[:AFFILIATED_WITH]->(dataCenter)
    ```

#### Read More

Be sure to check out the "Guides" section below for links to more details on
setup and use of CMR Graph.


### Example Usage [&#x219F;](#contents)

For a quick taste of using CMR Graph, we've put a snippet of code below.
See the links in the "Guides" section below for links to more detailed examples
and other usage.

```
TBD
```


### Project Guides [&#x219F;](#contents)

* [Setup][setup-docs]
* [Connecting to the Infrastructure][connecting-docs]
* [Usage Examples][usage-docs]
* [Development Environment][dev-docs]


### Project Reference [&#x219F;](#contents)

* [API Reference][api-docs]
* [Marginalia][marginalia-docs]


### Related Resources

* [Neo4j Cypher cheatsheet][cheatsheet]
* [Neo4j Manual: Cypher][cypher]


## License [&#x219F;](#contents)

Copyright © 2018 NASA

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.


<!-- Named page links below: /-->

[logo]: https://avatars2.githubusercontent.com/u/32934967?s=200&v=4
[api-docs]: http://cmr-exchange.github.io/cmr-graph/current/
[marginalia-docs]: http://cmr-exchange.github.io/cmr-graph/current/marginalia.html
[setup-docs]: http://cmr-exchange.github.io/cmr-graph/current/0500-setup.html
[connecting-docs]: http://cmr-exchange.github.io/cmr-graph/current/0750-connecting.html
[usage-docs]: http://cmr-exchange.github.io/cmr-graph/current/1000-usage.html
[dev-docs]: http://cmr-exchange.github.io/cmr-graph/current/2000-dev.html
[cypher]: https://neo4j.com/docs/developer-manual/current/cypher/
[cheatsheet]: https://neo4j.com/docs/cypher-refcard/current/
