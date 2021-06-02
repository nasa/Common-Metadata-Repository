# search-relevancy-test

Used to test the performance of CMR's search relevancy algorithm.

## Usage

There are two types of relevancy tests supported in this project:

* X before Y - Dataset X should be returned before dataset Y when I search for search term Z.
* Top N - Dataset D should be returned in the top N of the results when I search for search term Z.

The X before Y tests are executed locally by ingesting the collections needed for the tests. As a result one can quickly change CMR code to determine what effect it will have for all of the X before Y tests.

The top N tests always run against the production version of the CMR. Changes to relevancy are therefore not controlled and a change to production metadata such as adding, removing, or updating collections or CMR deployments of new code can cause changes to the results

### X Before Y tests

X before Y relevancy tests are run from the anomaly_tests.csv. Each line in the CSV file is a separate test. The anomaly number corresponds to this wiki page: https://wiki.earthdata.nasa.gov/display/CMR/Relevancy+Ranking+Suggestion+Box. The test number is for indicating multiple tests relating to the same anomaly. The concept-ids should be listed in the order they are expected to return from the given search.

To add a test, add a line to the CSV. By running download-collections, the concept-ids in the CSV will be downloaded and saved to the repository.

The results report the expected position of the concept in the results and the actual position of the concept in the results, if they differ.

The tasks supported by this project for X before Y tests are:
* download-collections
* relevancy-tests
* boost-tests
* analyze-test

### Top N tests

The top N tests are run from top_n_tests.csv. Each line in the CSV file is a separate test just like the X before Y tests. The position column is used to specify where in the results the collection is expected to be returned. To add a test, add a line to the CSV.

The task supported by this project for Top N tests is
* top-n-tests

### Download collections

`lein run download-collections`

This task will download the metadata files for every concept-id included in the anomaly_tests.csv file. If the metadata has already been downloaded it will skip the download for that concept-id.

### Relevancy-tests

`lein run relevancy-tests`

This task will ingest all of the locally saved metadata files into a local CMR. In order to use this task you must have the CMR dev-system running locally. It will execute every test case listed in the anomaly_tests.csv file and print out a report.

The report will also be logged to local_test_runs.csv to allow a comparison between runs for the local user and a historical record during debugging.

An optional argument -log-run-description can be specified with a run description (i.e. "Increase entry title boost").

Usage: `lein run relevancy-tests -log-run-description "Base Run"`

An additional optional boolean argument, -log-history, is available if you want the log be written to test_run_history.csv, which should be committed to the repository. By default, this option is false. Note: -log-history and -log-run-description are independent of each other. They can be used together, or separately.

Usage: `lein run relevancy-tests -log-run-description "Base Run" -log-history true`

### boost-tests

`lein run boost-tests -field entry-title`

This task will run the relevancy tests with the keyword score boost value for a particular field configured within a certain range. These tests are for finding the lowest boost value that yields maximum average discounted cumulative gain, i.e. the highest rate of success. All other boosts will maintain their default CMR boost value.

For example, if the boost field is entry-title and the boost range is 1.4 to 1.6, all of the relevancy tests will run 3 times with boosts of 1.4, 1.5, and 1.6 respectively. The output of each run will be saved to the local_test_runs.csv log and the boost test will output the lowest value that yielded the highest average discounted cumulative gain for the run.

Arguments:
* -field - (required) the field for which to configure the boost
* -min-value (optional) - the smallest boost value, defaults to 1.0
* -max-value (optional) - the highest boost value, defaults to 4.0

### analyze-test

`lein run analyze-test <anomaly-number> <anomaly-filename>`

This task will help analyze the failure of an X before Y test with the anomaly-number in the anomaly file with the anomaly-filename.
If anomaly-filename is not provided, anomaly_tests.csv will be used.

For example, if you run `lein run analyze-test 5`, it will analyze test number 5 in anomaly_tests.csv and give you the
following information, which helps explain why C1237114139-GES_DISC appears above C1239966794-GES_DISC:
Filtered search result is:  ({:id C1237114193-GES_DISC, :score 1.3104, :version_id 008, :short_name TOMSEPL2, :processing_level_id 2} {:id C1239966794-GES_DISC, :score 1.008, :version_id 003, :short_name OMDOAO3, :processing_level_id 2})

### top-n-tests

`lein run top-n-tests`

This task will run the relevancy tests of the form "I expect collection X to be in the top N collections returned when searching for Z." For each test it performs the search against the operational CMR and compares the position the result is returned in to the expected position to determine if the test passed or failed.

## License

Copyright © 2017-2021 NASA
