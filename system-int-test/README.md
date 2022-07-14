# cmr-system-int-test

System integration test for the CMR projects. It will ingest concepts using CMR Ingest Application and search/retrieve concepts using CMR Search Application. This is an end to end system integration test that verifies all CMR components working together.

## Usage

To run the system integration test suite, ensure all required services of CMR are running.
This may be through the dev-system using the repl or a compiled jar.

Once CMR is running, run one of the following commands.

``` sh
lein itest --skip-meta :in-memory-db

#or if using the in memory database

lein itest --skip-meta :oracle
```

The skips will exclude tests that will not have any assertions

_Kaocha will complain if a test is excuted with no assertions._

## Test Groups

In the CI environment, system integration tests are broken into two groups.
The split was determined to keep allow the tests to run in approximately 
equivalent lengths of time.

In local development, the groupings are not typically used. In the CI environment
the testing phase is broken into multiple groups to allow for faster build times.

If a new suite of tests needs to be created that does not fit within the existing
groups, it will be necessary to update the `tests.groupN.edn` files with the path
containing the new tests.

Additionally there are two special cases, `tests.all.edn` and `tests.none.edn`. 
`tests.all.edn` is the default that is loaded if no other group is specified. 
This is for local development.

`tests.none.edn` is for the CI environment to exclude system-int-tests when appropriate.

The groupings may be selected by setting the `CMR_TEST_GROUP` enviroment variable, 
e.g. `EXPORT CMR_TEST_GROUP="tests.group1.edn"` where the value is the name of the file to use.

## License

Copyright © 2022 NASA
