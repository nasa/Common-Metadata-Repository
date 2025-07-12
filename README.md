# [Common Metadata Repository](https://earthdata.nasa.gov/about/science-system-description/eosdis-components/common-metadata-repository)

## About

The Common Metadata Repository (CMR) is an earth science metadata repository
for [NASA](https://www.nasa.gov/) [EOSDIS](https://earthdata.nasa.gov) data. The CMR
Search API provides access to this metadata.

### Associated Projects

[cmr-token-service-client](https://github.com/nasa/cmr-token-service-client)
[cmr-tea-config-generator](https://github.com/nasa/cmr-tea-config-generator)

## [Prerequisites](#prerequisites)

### Java 17

[https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)

### Leiningen
[https://leiningen.org/](https://leiningen.org/)

```brew install leiningen```

### Maven
Maven is a package manager for Java and is used within CMR to deal with dependency management.

`brew install maven`

### Docker
Docker needs to be installed and running in order to run CMR.

[https://docs.docker.com/engine/install/](https://docs.docker.com/engine/install/)

## [Building and Running the CMR](#building-and-running-the-cmr)

The CMR is a system consisting of many services. The services can run individually or in a single process. Running in a single process makes local development easier because it avoids having to start many different processes. The sections below contain instructions for running the CMR as a single process or as many processes.

### Using the `cmr` CLI Tool

This project has its own tool that offers shortcuts and some additional functionality that help reduce the number of steps involved in many processes. To use the tool as we do below, be sure to run the following from the top-level CMR directory:

```sh
export PATH=$PATH:`pwd`/bin
source resources/shell/cmr-bash-autocomplete
```

(If you use a system shell not compatible with Bash, we accept Pull Requests for
new shells with auto-complete.)

To make this change permanent:

```sh
echo "export PATH=\$PATH:`pwd`/bin" >> ~/.profile
echo "source `pwd`/resources/shell/cmr-bash-autocomplete" >> ~/.profile
```

**If you wish to not use the tool, simply replace all the commands that start with `cmr` with `./bin/cmr` to run the scripts directly.**

### Environment Setup

1. `./bin/cmr setup profile` and then update the new `./dev-system/profiles.clj` file, it will look something like this:

When running CMR locally, the values in this file do not need to be changed, CMR just expects that they exist.

   ``` clojure
   {:dev-config {:env {:cmr-metadata-db-password "<YOUR PASSWORD HERE>"
                       :cmr-sys-dba-password "<YOUR PASSWORD HERE>"
                       :cmr-bootstrap-password "<YOUR PASSWORD HERE>"
                       :cmr-ingest-password "<YOUR PASSWORD HERE>"
                       :cmr-urs-password "<YOUR PASSWORD HERE>"}}}
   ```

2. `./bin/cmr setup dev`

This process will take quite a long time, be patient and monitor progress.

#### Running CMR
You can run CMR two different ways, everything in one or as separate.

##### All Services At Once

###### Via REPL

This method offers hot-reloading which is a more common method when actively developing against CMR.

1. `./bin/cmr start repl`
2. Once given a Clojure prompt, run `(reset)`

Note that the `reset` action may take a while, not only due to the code reloading for a large number of namespaces, but for bootstrapping services as well as starting up worker threads.

###### Via JAR

Running CMR with this method requires reloading your environment after code changes, it is not recommended to use this method if actively developing.

##### Running All Services At Once

1. `cmr build uberjars`
2. `cmr build all`
3. `cmr start uberjar dev-system` will run the dev-system as a background task

##### Running Individual Services

The following will build every application but will put each JAR into the appropriate `target` directory for each application. The command shown in step 3 is an example. For the proper command to start up each application, see the `Applications` section below. Note: You only need to complete steps 1 and 2 once.

1. `cmr build uberjar APP`
2. `cmr run uberjar APP`

Where `APP` is any supported CMR app. You can double-tap the `TAB` key on
your keyboard to get the `cmr` tool to show you the list of available apps
after entering `uberjar` in each step above.

Note: building uberjars will interfere with your repl. If you want to use your repl post-build you will need to,
`rm -f ./dev-system/target/`

## Using VS Code Calva extenstion
VS Code has an extension that allows for easier development of clojure applications.
Developers can get setup via: [Getting Started with Calva](https://calva.io/getting-started/)

## Testing CMR

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

There are two ways to run unit tests, serially and in parallel.

For serial execution use:
```
> lein modules utest
```
Alternatively unit tests can be run in parallel using the python script in
[run_unit_tests.py][ut-script]. This script is meant to be called by a build system such as Bamboo.

```
> lein ci-utest
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

Throughout the modules, in the `project.clj` files there are additional `lein` aliases for
executing the tests in the CI/CD environment. They are
* ci-itest
* ci-utest

These run the integration and unit tests, respectively, in the CI environment. The difference
between `itest` and `ci-itest` or `utest` and `ci-utest` are the settings passed to the
kaocha test runner.

In the CI environment, color is omitted, and certain tests that require an internal memory
database are excluded. The aliases may be used locally as well.

To see the difference in detail, inspect the `tests.edn` files for each module to see the
profile in use in the CI environment. Kaocha supports the use of profiles so more may
be added as necessary.

### Test Development

CMR uses the [Kaocha](https://github.com/lambdaisland/kaocha) test library.
It provides plugins and grouping capabilities. Tests are organized in each module
with the standard being `:unit` and `:integration`.

Not all modules will contain `:integration` tests.

### The Microservices

Each microservice has a `README` file in its root directory, which provides a
short overview of the service's functionality. There are many main
applications, as well as several libraries and support applications.

#### Applications

- [access-control-app](access-control-app/README.md)
  - The mechanism which grants users access to perform different
    operations in the CMR. It also maintains groups and access control rules.
    Note that ECHO and URS provide user access as an external dependency.
    The mock-echo application implements both of the necessary interfaces
    for local testing.
  - Main method: `cmr.access_control.runner`

- [bootstrap-app](bootstrap-app/README.md)
  - Contains APIs for performing various bulk actions in the CMR
  - Main method: cmr.bootstrap.runner

- [dev-system](dev-system/README.md)
  - An app that combines the separate microservices of the CMR into a single
  application. We use this to simplify development
  - Main method: `cmr.dev_system.runner`

- [indexer-app](indexer-app/README.md)
  - This handles indexing collections, granules, and tags in Elasticsearch
  - Maintains the set of indexes in Elasticsearch for each concept
  - Main method: `cmr.indexer.runner`

- [ingest-app](ingest-app/README.md)
  - The Ingest app handles collaborating with metadata db and indexer systems.
  This maintains the lifecycle of concepts coming into the CMR
  - Main method: `cmr.ingest.runner`

- [search-app](search-app/README.md)
  - Provides a public search API for concepts in the CMR
  - Main method: `cmr.search.runner`

- [search-relevancy-test](search-relevancy-test/README.md)
  - Tests to measure and report the effectiveness of CMR's search relevancy algorithm

- [system-int-test](system-int-test/README.md)
  - Black-box, system-level tests to ensure functionality of the CMR

- [virtual-product-app](virtual-product-app/README.md)
  - Adds the concept of Virtual Products to the CMR. Virtual Products represent
  products that a provider generates on demand from users. This takes place when
   a user places an order or downloads a product through a URL
  - Main method: cmr.virtual_product.runner

- [metadata-db-app](metadata-db-app/README.md)
  - A database that maintains revisioned copies of metadata for the CMR
  - Main method: cmr.metadata_db.runner

- [mock-echo-app](mock-echo-app/README.md)
  - This mocks out the ECHO REST API and the URS API as well. Its purpose is to
  make it easier to integration test the CMR system without having to run a full
   instance of ECHO. It will only provide the parts necessary to enable
   integration testing. You should not expect a perfect or complete
   implementation of ECHO.
  - Main method: cmr.mock_echo.runner

## Contributors

We appreciate all contributors to the CMR project:

- **Ervin Remus** - Contributing to open source development and improvements

## License

> Copyright © 2007-2024 United States Government as represented by the Administrator of the National Aeronautics and Space Administration. All Rights Reserved.
>
> Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
> You may obtain a copy of the License at
>
>    http://www.apache.org/licenses/LICENSE-2.0
>
>Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
>WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
