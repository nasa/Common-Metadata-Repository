# cmr-graph-db

Nodejs application to perform graph db operations in CMR

# Development Environment

## Prerequisites
* [Nodejs](https://nodejs.org/en/)
* [Docker](https://docs.docker.com/install/)
* [Tinkerpop](https://tinkerpop.apache.org/)

### Node
CMR graph-db runs on Node.js, in order to run the application you'll need to install nodejs.

##### NVM
To ensure that you're using the correct version of Node, it is recommended that you use Node Version Manager. Installation instructions can be found on [the repository](https://github.com/nvm-sh/nvm#install--update-script). The version used is defined in .nvmrc and will be used automatically if NVM is configured correctly.

### NPM
npm is a separate project from Node.js, and tends to update more frequently. As a result, even if you’ve just downloaded Node.js (and therefore npm), you’ll probably need to update your npm. Luckily, npm knows how to update itself! To update your npm, type this into your terminal:

```
npm install -g npm@latest
```

### Docker
Download docker on https://docs.docker.com/get-docker/

### TinkerPop
Apache TinkerPop™ is an open source, vendor-agnostic, graph computing framework distributed under the commercial friendly Apache2 license. When a data system is TinkerPop-enabled, its users are able to model their domain as a graph and analyze that graph using the Gremlin graph traversal language. We use the default Gremlin Server TinkerGraph db and Tinkerpop Gremlin Console in our local development.


### Graphexp
Graphexp is a lightweight web interface to explore and display a graph stored in a Gremlin graph database, via the Gremlin server. This is an easy way to visualize nodes and edges in the graph database.
Clone the graphexp repository at [https://github.com/bricaud/graphexp](https://github.com/bricaud/graphexp)

## Serverless Applications
There are two serverless applications that interact with the graph database:

### Bootstrap

  Bootstrap is a serverless application that load all collections from a CMR environment (SIT, UAT, PROD) into the graph database.

### Indexer

  Indexer is a serverless application that is connected to a SQS queue that is associated with the live CMR collection ingest/update events. It will index new CMR collection ingest/update into the graph database.

### Build
```
npm install
```

### Test
To run the test suite one time run (Note: to run all tests you must have the gremlin-sever docker container running)
```
npm run test
```

To run the test suite in watch mode, run
```
npm run test:watch
```

### Run Bootstrap
To bootstrap all CMR collections from the CMR environment as specified in the deployment (default in serverless.yml) to graph db, send the following test event in the deployed bootstrap lambda function in AWS.

```
{
  "Records": [
    {
      "body": "{}"
    }
  ]
}
```

To bootstrap all collections in a specific provider (e.g LPDAAC_TS2), send the following test event:
```
{
  "Records": [
    {
      "body": "{\"provider-id\": \"LPDAAC_TS2\"}"
    }
  ]
}
```

The easiest means of accomplishing this would be to modify `local-bootstrap.json` and running the following command:

```
npx serverless invoke local --function bootstrapGremlinServer -p local-bootstrap.json
```

## Explore Indexed Data
CMR graph database is a Neptune database hosted on AWS. Currently, we index collections and their related urls, projects, platforms and instruments as vertices in the graph database. See the following diagram for details:

![CMR Collection GraphDB Diagram](images/cmr_collection_graphdb_diagram.png)
