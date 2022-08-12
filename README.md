# Common Metadata Repository

Visit the CMR at [https://earthdata.nasa.gov/about/science-system-description/eosdis-components/common-metadata-repository](https://earthdata.nasa.gov/about/science-system-description/eosdis-components/common-metadata-repository)

## About

The Common Metadata Repository (CMR) is an earth science metadata repository
for [NASA](https://www.nasa.gov/) [EOSDIS](https://earthdata.nasa.gov) data. The CMR
Search API provides access to this metadata.

## Client-facing Components

- Search
  - Allows the user to search by collections, granules, and concepts with a
    myriad of different query types
  - API Docs: https://cmr.earthdata.nasa.gov/search/site/search_api_docs.html

- Ingest
  - Ingest refers to the process of validating, inserting, updating, or
    deleting metadata in the CMR system. It affects only the metadata for the
    specific Data Partner. The CMR allows Data Partners to ingest metadata.
    records through a RESTful API
  - API Docs: https://cmr.earthdata.nasa.gov/ingest/site/ingest_api_docs.html

- Access Control
  - Access Control Lists (ACLs) are the mechanism which grants users
    access to perform different operations in the CMR. CMR ACLs follow the same
    design as ECHO ACLs, which are a superset of the generic ACL
    design pattern used in many other systems. An ACL is a
    mapping of actors (subjects) to resources (object) to operations
    (predicate).
  - Two quick examples of a CMR ACL could be:
    - All registered users have READ access to ASTER data
    - A provider's operations team may ingest data for that provider
  - API Docs: https://cmr.earthdata.nasa.gov/access-control/site/access_control_api_docs.html

## Our Development Environment

- Mac OSX
- Atom: https://atom.io/
- Proto-Repl: https://atom.io/packages/proto-repl
  - Installed and configured according to this guide: https://git.io/atom_clojure_setup

## Prerequisites

- Java 1.8.0 (a.k.a. JAVA8) only; higher versions are not currently supported.
- Leiningen (https://leiningen.org) 2.5.1 or above.
  - We've had success with Homebrew and with the install script on the
    Leiningen website.
- Ruby (used to support two legacy apps)
- Maven (https://maven.apache.org/install.html)
    - Mac OS X devs can use `brew install maven`
    - Linux devs can use `sudo apt-get install maven`
- GCC and libc
- Docker

## Obtaining the Code

You can get the CMR source code by cloning the repository from GitHub:

```
$ git clone git@github.com:nasa/Common-Metadata-Repository.git cmr
```

## Building and Running the CMR

The CMR is a system consisting of many services. The services can run
individually or in a single process. Running in a single process makes
local development easier because it avoids having to start many different
processes. The dev-system project allows the CMR to run from a single REPL
or Jar file. If you're developing a client against the CMR you can build and
run the entire CMR with no external dependencies from this Jar file and use
that instance for local testing. The sections below contain instructions for
running the CMR as a single process or as many processes.

#### Using the `cmr` CLI Tool

This project has its own tool that is able to do everything from initial setup to
running builds and tests on the CI/CD infrastructure. To use the tool
as we do below, be sure to run the following from the top-level CMR directory:

```
export PATH=$PATH:`pwd`/bin
source resources/shell/cmr-bash-autocomplete
```

(If you use a system shell not compatible with Bash, we'll accept a PR with
auto-complete for it.)

To make this change permanent:

```
echo "export PATH=\$PATH:`pwd`/bin" >> ~/.profile
echo "source `pwd`/resources/shell/cmr-bash-autocomplete" >> ~/.profile
```

#### Oracle Dependencies

Even if you're not going to develop against a local Oracle database,
you still need to have the Oracle libraries locally installed to use the
CMR.

Here are the steps to do so:

1. Ensure you have installed on your system the items listed above in the
   "Prerequisites" section.
1. Download the Oracle JDBC JAR files into `./oracle-lib/support` by
   following instructions in `./oracle-lib/README.md`. (The CMR must have these
   libraries to build but it does not depend on Oracle DB when running
   locally. It uses a local in-memory database by default.) If you're reading this
   guide on the web, [here is a handy link to the instructions.](https://github.com/nasa/Common-Metadata-Repository/tree/master/oracle-lib)
1. With the JAR files downloaded to the proper location, you're now ready
   to install them for use by the CMR:" `cmr install oracle-libs`

#### Building and Running CMR Dev System in a REPL with CMR CLI tool

1. `cmr setup profile` and then update the new `./dev-system/profiles.clj` file.
   it will look something like this:
   ``` clojure
   {:dev-config {:env {:cmr-metadata-db-password "<YOUR PASSWORD HERE>"
                       :cmr-sys-dba-password "<YOUR PASSWORD HERE>"
                       :cmr-bootstrap-password "<YOUR PASSWORD HERE>"
                       :cmr-ingest-password "<YOUR PASSWORD HERE>"
                       :cmr-urs-password "<YOUR PASSWORD HERE>"}}}
   ```

2. `cmr setup dev`
3. `cmr start repl`
4. Once given a Clojure prompt, run `(reset)`

Note that the `reset` action could potentially take a while, not only due to
the code reloading for a large number of namespaces, but for bootstrapping
services as well as starting up worker threads.

#### Building and Running CMR Dev System from a Jar

Assuming you have already run the above steps (namely `cmr setup dev`), to
build and run the default CMR development system (`dev-system`) from a
`.jar` file:

1. `cmr build uberjars`
2. `cmr build all`
3. `cmr start uberjar dev-system` will run the dev-system as a background task

See CMR Development Guide to read about specifying options and setting
environment variables

#### Building and Running separate CMR Applications

The following will build every application but will put each jar into the
appropriate `target` directory for each application. The command shown in step
3 is an example. For the proper command to start up each application, see the
`Applications` section below. Note: You only need to complete steps 1 and 2 once.

1. `cmr build uberjar APP`
2. `cmr run uberjar APP`

Where `APP` is any supported CMR app. You can touble-tap the `TAB` key on
your keyboard to get the `cmr` tool to show you the list of available apps
after entering `uberjar` in each step above.

Note: building uberjars will interfere with your repl. If you want to use your repl post-build you will need to,
`rm -f ./dev-system/target/`

## Checking Dependencies, Static Analysis, and Tests

There are several `lein` plugins within the CMR for performing
various tasks either at individual subproject levels or at the top-level for
all subprojects.

#### Dependency Versions

To check for up-to-date versions of all project dependencies, you can use
`cmr test versions PROJ`, where `PROJ` is any CMR sub-project under the
top-level directory.

You may run the same command without a project to check for all projects:
`cmr test versions`.

Note that this command fails with the first project that fails. If many
subprojects are failing their dependency version checks and you wish to see
them all, you may use your system shell:

```sh
for PROJ in `ls -1d */project.clj|xargs dirname`
do
  "Checking $PROJ ..."
  cmr test versions $PROJ
  cd - &> /dev/null
done
```

#### Dependency Ambiguities and `.jar` File Conflicts

To see if the JVM is having problems resolving which version of a
dependency to use, you can run `cmr test dep-tree PROJ`. To perform this
against all projects: `cmr test dep-trees`.

#### Static Analysis and Linting

To perform static analysis and linting for a project, you can run
`cmr test lint PROJ`. As above with dependency version checking, by
not passing a project, you can run for all projects: `cmr test lint`.

#### Dependency Vulnerability Scanning

You can see if your currently installed version of CMR has any reported Common Vulnerabilities and Exploits (CVEs) by running the helpful alias `lein check-sec` that you can use in each application, or at the root folder to scan all CMR apps together.

You will find the vulnerability summary in `./target/dependency-check-report.html` in each application.

#### Testing CMR

Test files in CMR should follow the naming convention of ending in `-test`.

There are two modes of testing the CMR:

* From the REPL
* Utilizing the CI/CD script to run against an Oracle database

For the first, the steps are as follows:

1. Ensure you have set up your development environment in `dev-system`
2. If you have built any `.jar` files, run `cmr clean PROJ` (for a given
   project) or `cmr clean` to clean all projects.
3. Start the REPL: `cmr start repl`
4. Once in the REPL, start the in-memory services: `(reset)`
5. Run the tests: `(run-all-tests)` or `(run-all-tests-future)`

You have the option of substituting the last step with `(run-suites)`. This
uses a third-party tool to display clear test results which are
easier copy/paste should you want to run them on an individual basis.
These results also contain easier to read
exception messages/stacktraces. Here's an excerpt:

```
   cmr.system-int-test.ingest.provider-ingest-test
     update-provider-test
       assertion 1 ........................................................ [OK]
       assertion 2 ........................................................ [OK]
       assertion 3 ........................................................ [OK]
       assertion 4 ........................................................ [OK]
     delete-provider-test
       assertion 1 ........................................................ [OK]
       assertion 2 ........................................................ [OK]
       assertion 3 ........................................................ [OK]
       assertion 4 ........................................................ [OK]
       assertion 5 ........................................................ [OK]
       assertion 6 ........................................................ [OK]
       assertion 7 ........................................................ [OK]
       assertion 8 ........................................................ [OK]
       assertion 9 ........................................................ [OK]
```

For non-terminal based dev, depending upon your IDE/editor, you may have
shortcuts available to you for starting/restarting the services and/or running the
tests. To find out what these are you can contact a CMR core dev.

To run the tests against an Oracle database, we recommend that
you use an Oracle VM built for this purpose. You will also need
configuration and authentication information that will be set as environment
variables. Be sure to contact a CMR core dev for this information.

To run only certain types of tests, you may run the following:

##### Unit Tests

``` sh
lein modules utest
```

##### Integration Tests

If running CMR with the in-memory database (default)
``` sh
lein modules itest --skip-meta :oracle

```

If running CMR with an external database
``` sh
lein modules itest --skip-meta :in-memory-db
```

If you want to run tests against Oracle, bring up the Oracle VM and execute
the following to create the users and run the migrations:

``` sh
cmr setup db
```

Then, in the CMR REPL:

```clj
user=> (reset :db :external)
...
user=> (run-all-tests)
...
```

Those tests will take much longer to run than when done with the in-memory
database (~25m vs. ~6m). To switch back to using the in-memory database,
call `(reset :db :in-memory)`.

There is also a different, optional test runner you can use. For more details
see the docstring for `run-suites` in `dev-system/dev/user.clj`.
It will contain usage instructions
#### Testing in the CI Environment

Throughout the modules, in the `project.clj` files there are additional `lein` aliseses for
executing the tests in the CI/CD environment. They are
* ci-itest
* ci-utest

These run the integration and unit tests, respectively, in the CI environment. The difference
between `itest` and `ci-itest` or `utest` and `ci-utest` are the settings passed to the
kaocha test runner.

In the CI environment, color is omitted, and certain tests that require an internal memory
database are excluded. The aliases may be used locally as well.

To see the differnce in detail, inspect the `tests.edn` files for each module to see the
profile in use in the CI environment. Kaocha supports the use of profiles so more may
be added as necessary.

### Test Development

CMR uses the [Kaocha](https://github.com/lambdaisland/kaocha) test library.
It provides plugins and grouping capabilities. Tests are organized in each module
with the standard being `:unit` and `:integration`.

Not all modules will contain `:integration` tests.

## Code structure

The CMR comprises several small services called microservices. These are
small purposed-based services that do a small set of things well.

- For more reading on microservices: https://martinfowler.com/articles/microservices.html

### The Microservices

Each microservice has a `README` file in its root directory, which provides a
short overview of the service's functionality. There are many main
applications, as well as several libraries and support applications.

#### Applications:

- access-control-app
  - The mechanism which grants users access to perform different
    operations in the CMR. It also maintains groups and access control rules.
    Note that ECHO and URS provide user access as an external dependency.
    The mock-echo application implements both of the necessary interfaces
    for local testing.
  - Main method: cmr.access_control.runner

- bootstrap-app
  - Contains APIs for performing various bulk actions in the CMR
  - Main method: cmr.bootstrap.runner
  - See `/bootstrap-app/README.md` for a list of lein and uberjar commands

- dev-system
  - An app that combines the separate microservices of the CMR into a single
  application. We use this to simplify development
  - Main method: cmr.dev_system.runner

- indexer-app
  - This handles indexing collections, granules, and tags in Elasticsearch
  - Maintains the set of indexes in elasticsearch for each concept
  - Main method: cmr.indexer.runner

- ingest-app
  - The Ingest app handles collaborating with metadata db and indexer systems.
  This maintains the lifecycle of concepts coming into the CMR
  - Main method: cmr.ingest.runner

- search-app
  - Provides a public search API for concepts in the CMR
  - Main method: cmr.search.runner

- search-relevancy-test
  - Tests to measure and report the effectiveness of CMR's search relevancy algorithm

- system-int-test
  - Black-box, system-level tests to ensure functionality of the CMR

- virtual-product-app
  - Adds the concept of Virtual Products to the CMR. Virtual Products represent
  products that a provider generates on demand from users. This takes place when
   a user places an order or downloads a product through a URL
  - Main method: cmr.virtual_product.runner

- metadata-db-app
  - A database that maintains revisioned copies of metadata for the CMR
  - Main method: cmr.metadata_db.runner

- mock-echo-app
  - This mocks out the ECHO REST API and the URS API as well. Its purpose is to
  make it easier to integration test the CMR system without having to run a full
   instance of ECHO. It will only provide the parts necessary to enable
   integration testing. You should not expect a perfect or complete
   implementation of ECHO.
  - Main method: cmr.mock_echo.runner

#### Libraries:

- acl-lib
  - Contains utilities for retrieving and working with ACLs

- common-app-lib
  - Contains utilities used within many CMR applications

- common-lib
  - Provides common utility code for CMR projects

- elastic-utils-lib
  - A library that handles most of the interfacing with Elasticsearch

- es-spatial-plugin
  - An Elasticsearch plugin that enables spatial search within elastic

- oracle-lib
  - Contains utilities for connecting to and manipulating data in Oracle

- orbits-lib
  - Clojure wrapper of a Ruby implementation of the Backtrack Orbit Search
    Algorithm (BOSA)

- message-queue-lib
  - A library for interfacing with RabbitMQ, AWS SQS, and an in-memory message queue

- spatial-lib
  - The spatial libraries provide utilities for working with spatial areas in the CMR

- transmit-lib
  - The Transmit Library defines functions for invoking CMR services

- umm-lib
  - This is the old source of UMM schemas and translation code. Since the
    advent of umm-spec-lib we are planning to remove it

- umm-spec-lib
  - The UMM Spec lib contains JSON schemas that define the Unified Metadata
    Model, as well as mappings to other supported formats, and code to migrate
    collections between any supported formats.

## Further Reading

- CMR Client Partner User Guide: https://wiki.earthdata.nasa.gov/display/ED/CMR+Client+Partner+User+Guide
- CMR Data Partner User Guide: https://wiki.earthdata.nasa.gov/display/CMR/CMR+Data+Partner+User+Guide

## License

Copyright © 2014-2022 United States Government as represented by the Administrator of the National Aeronautics and Space Administration. All Rights Reserved.
