# Common Metadata Repository
Visit the CMR at [https://earthdata.nasa.gov/about/science-system-description/eosdis-components/common-metadata-repository](https://earthdata.nasa.gov/about/science-system-description/eosdis-components/common-metadata-repository)


## About
The Common Metadata Repository (CMR) is an earth science metadata repository for [NASA](http://nasa.gov) [EOSDIS](https://earthdata.nasa.gov) data. The CMR Search API provides access to this metadata.

## Client-facing Components
- Search
  - Allows the user to search by collections, granules, and concepts with a myriad of different query types
  - API Docs: https://cmr.earthdata.nasa.gov/search/site/search_api_docs.html

- Ingest
  - Ingest refers to the process of validating, inserting, updating, or deleting metadata in the CMR system and affects only the metadata for the specific Data Partner. The CMR allows Data Partners to ingest metadata records through a RESTful API
  - API Docs: https://cmr.earthdata.nasa.gov/ingest/site/ingest_api_docs.html

- Access Control
  - Access Control Lists (ACLs) are the mechanism by which users are granted access to perform different operations in the CMR. CMR ACLs follow the same design as ECHO ACLs which in turn are derived from the generic ACL design pattern used in many other systems. At a high level, an ACL is a mapping of actors (subjects) to resources (object) to operations (predicate). For instance, a CMR ACL might specify that all Registered users have READ access to ASTER data or all users in a provider operations group have permissions to ingest data for a particular provider.
  - API Docs: https://cmr.earthdata.nasa.gov/access-control/site/access_control_api_docs.html

## Our Development Environment
  - Mac OSX
  - Atom: https://atom.io/
  - Proto-Repl: https://atom.io/packages/proto-repl
    - Installed and configured according to this guide: https://git.io/atom_clojure_setup

## Prerequisites
- Java 1.8.0 or higher
- Leiningen (http://leiningen.org) 2.5.1 or above.
  - We've had success with Homebrew and with the install script on the Leiningen website.

## Building and Running the CMR
The CMR is a system consisting of multiple services. The services can be run individually or can run in a single process. Running in a single process makes local development easier because it avoids having to start many different processes. The dev-system project allows the CMR to be run from a single REPL or Jar file. If you're developing a client against the CMR you can build and run the entire CMR with no external dependencies from this Jar file and use that instance for local testing. The sections below contain instructions for running the CMR as a single process or as multiple processes.

#### Building and Running CMR Dev System in a REPL
  1. cd cmr/dev-system
  2. ./support/setup_local_dev.sh
  3. lein repl
  4. Once given a clojure prompt, run `(reset)`

#### Building and Running CMR Dev System from a Jar
  1. cd cmr/dev-system
  2. ./support/setup_local_dev.sh
  3. lein uberjar
  4. See CMR Development Guide to read about specifying options and setting environment variables

#### Building and Running separate CMR Applications
This will build all of the applications, but will put each jar into the appropriate /target directory for each application.
The command shown in step 3 is an example. For the proper command to start up each application, see the `Applications` section below. Note: Steps 1 and 2 only need to be completed once.
  1. cd cmr/dev-system
  2. ./support/build.sh CMR_BUILD_UBERJARS
  3. cd cmr/<service>
  4. lein uberjar
  5. java -classpath ./target/<NAME OF SERVICE>-0.1.0-SNAPSHOT-standalone.jar <MAIN METHOD OF SERVICE>

# Code structure
The CMR is made up of several small services called microservices. These are small purposed-based services that do a small set of things well.
- For more reading on microservices: https://martinfowler.com/articles/microservices.html

### The Microservices
Each microservice has a README file in its root directory, which provides a short overview of the service's functionality.
There are a number of main applications, as well as several libraries and support applications.

#### Applications:
- access-control-app
  - The mechanism by which users are granted access to perform different operations in the CMR. Also maintains groups and access control rules. Note that users access is provided by either ECHO or URS as an external dependency. The mock-echo application implements both of the necessary interfaces for local testing.
  - Main method: cmr.access_control.runner

- bootstrap-app
  - Contains APIs for performing various bulk actions in the CMR
  - Main method: cmr.bootstrap.runner
  - See /bootstrap-app/README.md for a list of lein and uberjar commands

- cubby-app
  - Centralized caching for the CMR. Ideally each application will cache internally, but for situations that require centralized caching, we use Cubby.
  - Main method: cmr.cubby.runner

- dev-system
  - An app that combines together the separate microservices of the CMR into a single application to make it simpler to develop and run
  - Main method: cmr.dev_system.runner

- indexer-app
  - This handles indexing collections, granules, and tags in Elasticsearch
  - Main method: cmr.indexer.runner

- ingest-app
  - The Ingest app is responsible for collaborating with metadata db and indexer components of the CMR system to maintain the lifecycle of concepts coming into the system
  - Main method: cmr.ingest.runner

- search-app
  - Provides a public search API for concepts in the CMR
  - Main method: cmr.search.runner

- system-int-test
  - Black-box, system-level tests to ensure functionality of the CMR

- virtual-product-app
  - Adds the concept of Virtual Products to the CMR. In short: Virtual Products represent products at a data provider that are generated on demand from users when they are ordered or downloaded through a URL
  - Main method: cmr.virtual_product.runner

- index-set-app
  - An application that maintains the set of indexes in elasticsearch for multiple concept types
  - Main method: cmr.index_set.runner

- metadata-db-app
  - A database that maintains revisioned copies of metadata for the CMR
  - Main method: cmr.metadata_db.runner

- mock-echo-app
  - This mocks out the ECHO REST API and the URS API as well. Its purpose is to make it easier to integration test the CMR system without having to run a full instance of ECHO. It won't mock it perfectly or completely. It will only implement the minimum necessary to enable integration testing.
  - Main method: cmr.mock_echo.runner

#### Libraries:
- acl-lib
  - Contains utilities for retrieving and working with ACLs

- common-app-lib
  - Contains utilities used within multiple CMR applications

- common-lib
  - Provides common utility code for CMR projects

- elastic-utils-lib
  - Most interfacing with Elasticsearch is done through this application

- es-spatial-plugin
  - An Elasticsearch plugin that enables spatial search entirely within elastic

- oracle-lib
  - Contains utilities for connecting to and manipulating data in Oracle

- message-queue-lib
  - A library for interfacing with RabbitMQ, AWS SQS, and an in memory message queue

- spatial-lib
  - The spatial libraries provide utilities for working with spatial areas in the CMR

- transmit-lib
  - The Transmit Library defines functions for invoking CMR services

- umm-lib
  - This is the old source of UMM schemas and translation code. Since the advent of umm-spec-lib it is actively in the process of being removed

- umm-spec-lib
  - The UMM Spec lib contains JSON schemas that defined the Unified Metadata Model, as well as mappings to other supported formats, and code to migrate collections between any supported formats.

## Further Reading
- CMR Client Partner User Guide: https://wiki.earthdata.nasa.gov/display/CMR/CMR+Client+Partner+User+Guide
- CMR Data Partner User Guide: https://wiki.earthdata.nasa.gov/display/CMR/CMR+Data+Partner+User+Guide
- CMR Client Developer Forum: https://wiki.earthdata.nasa.gov/display/CMR/CMR+Client+Developer+Forum
