# Indexer

Serverless Nodejs application to perform live indexing of CMR collection ingest/update events into the graph database. Indexer is deployed via Serverless and is connected to a SQS queue that subscribes to the CMR collection ingest/update SNS topics.


# Development Environment

## Prerequisites
* [Nodejs](https://nodejs.org/en/)
* [Serverless](https://www.serverless.com/)


### Node
Indexer runs on Node.js. In order to run the application, you'll need to install nodejs.

Recommended: Use Homebrew

```
brew install node
```

### NPM
npm is a separate project from Node.js, and tends to update more frequently. As a result, even if you’ve just downloaded Node.js (and therefore npm), you’ll probably need to update your npm. Luckily, npm knows how to update itself! To update your npm, type this into your terminal:

```
npm install -g npm@latest
```

### Serverless
The Serverless Framework consists of an open source CLI and a hosted dashboard. Together, they provide you with full serverless application lifecycle management. Get started at: https://www.serverless.com/framework/docs/getting-started/


## Build
```
npm install
```

## Deploy
To deploy the graph indexer application into a CMR environment, run the following command (e.g. in SIT environment):
```
export AWS_PROFILE=cmr-sit
npm run deploy -- --stage sit
```

## Rollback
To roll back the deployed graph indexer application in a CMR environment, run the following command (e.g. in SIT environment):
```
export AWS_PROFILE=cmr-sit
serverless remove -v --stage sit
```
