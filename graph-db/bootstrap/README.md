# Bootstrap

Serverless Nodejs application to perform bootstrapping all collections from a CMR environment (SIT, UAT, PROD) into the graph database.

# Development Environment

## Prerequisites
* [Nodejs](https://nodejs.org/en/)
* [Serverless](https://www.serverless.com/)


### Node
Bootstrap runs on Node.js. In order to run the application, you'll need to install nodejs.

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

## Run
To invoke the bootstrap function and load data into your Gremlin server from the CMR run the following command:
```
npm run bootstrap
```

# Test
To run the test suite one time run
```
npm run test
```

To run the test suite in watch mode, run
```
npm run test -- --watch
```
