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

## Development Environment
- We use several different editors based on preference, but the most popular setup is the following.
  - Mac OSX
  - Atom: https://atom.io/
  - Proto-Repl: https://atom.io/packages/proto-repl
    - Installed and configured according to this guide: https://git.io/atom_clojure_setup

## Prerequisites
- Java 1.8.0 or higher
- Leiningen (http://leiningen.org) 2.5.1 or above.
  - We've had success with Homebrew and with the install script on the Leiningen website.

## Building and running the CMR from source
There are multiple ways to build and run the CMR, but our preferred way is the following for general development:
- From atom
  1. Open the CMR's root directory
  2. Navigate to `dev-system` and open the `project.clj` file
  3. With the project file open, start proto-repl. The first startup will take some extra time, as Leiningen resolves dependencies

- From the terminal
  1. Navigate to `cmr/dev-system/`
  2. Enter `lein run`
    - You may also enter `lein uberjar` in the dev-system directory if you wish to build an executable
    - https://github.com/technomancy/leiningen/blob/master/doc/TUTORIAL.md#uberjar

#### The previous instructions will build or run the CMR as a singular application. If you want to build or run a single microservice, simply follow the above steps, but navigate to the desired service's directory or `project.clj` file.

# Code structure
The CMR is made up of several small services called microservices. These are small purposed-based services that do a small set of things well.
- For more reading on microservices: https://martinfowler.com/articles/microservices.html

### The Microservices
Each microservice has a README file in its root directory, which provides a short overview of the service's functionality.
There are a number of main applications, as well as several libraries and support applications.

#### The main applications are as follows:
- access-control-app
  - The mechanism by which users are granted access to perform different operations in the CMR

- common-app
  - Houses any common "utility" code or functionality used by multiple components of the CMR

- dev-system
  - An app that combines together the separate microservices of the CMR into a single application to make it simpler to develop and run

- indexer-app
  - This modifies existing data in Elasticsearch

- ingest-app
  - The Ingest app is responsible for collaborating with metadata db and indexer components of the CMR system to maintain the lifecycle of concepts coming into the system

- search-app
  - Provides a public search API for concepts in the CMR

- system-int-test
  - Black-box, system-level tests to ensure functionality of the CMR

- umm-spec-lib
  - The UMM Spec lib contains JSON schemas that defined the Unified Metadata Model, as well as mappings to other supported formats, and code to migrate collections between any supported formats.

#### The supporting applications:
- acl-lib
- bootstrap-app
  - Bootstrap is a CMR application that can bootstrap the CMR with data from Catalog REST. It has API methods for copying data from Catalog REST to the metadata db. It can also bulk index everything in the Metadata DB

- common-app-lib
- common-lib
- cubby-app
  - Centralized caching for the CMR. Ideally each application will cache internally, but for situations that require centralized caching, we use Cubby.

- elastic-utils-lib
  - Most interfacing with Elasticsearch is done through this application

- es-spatial-plugin
  - An Elasticsearch plugin that enables spatial search entirely within elastic

- index-set-app
  - An application that maintains the set of indexes in elasticsearch for multiple concept types

- message-queue-lib
  - A library for interfacing with RabbitMQ

- metadata-db-app
  - TODO

- mock-echo-app
  - This mocks out the ECHO REST API. It's purpose is to make it easier to integration test the CMR system without having to run a full instance of ECHO. It won't mock it perfectly or completely. It will only implement the minimum necessary to enable integration testing.

- oracle-lib
  - Contains utilities for connecting to and manipulating data in OracleDB

- spatial-lib
- transmit-lib
  - The Transmit Library is responsible for defining the common transmit libraries that invoke services within the CMR projects

- umm-lib
  - This is the old source of UMM schemas and translation code. Since the advent of umm-spec-lib it is actively in the process of being removed

- vdd-spacial-viz
  - A visualization tool for spatial libraries

- virtual-product-app
  - Adds the concept of Virtual Products to the CMR. In short: Virtual Products represent products at a data provider that are generated on demand from users when they are ordered or downloaded through a URL

## Further Reading
- CMR Client Partner User Guide: https://wiki.earthdata.nasa.gov/display/CMR/CMR+Client+Partner+User+Guide
- CMR Data Partner User Guide: https://wiki.earthdata.nasa.gov/display/CMR/CMR+Data+Partner+User+Guide
- CMR Client Developer Forum: https://wiki.earthdata.nasa.gov/display/CMR/CMR+Client+Developer+Forum
