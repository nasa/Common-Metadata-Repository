# search-relevancy-test

Used to test the performance of CMR's search relevancy algorithm.

## Usage

Relevancy tests are run from the anomaly_tests.csv. Each line in the CSV file is a separate test. The anomaly number corresponds to this wiki page: https://wiki.earthdata.nasa.gov/display/CMR/Relevancy+Ranking+Suggestion+Box. The test number is for indicating multiple tests relating to the same anomaly. The concept-ids should be listed in the order they are expected to return from the given search.

To add a test, add a line to the CSV. By running download-collections, the concept-ids in the CSV will be downloaded and saved to the repository.

The results report the expected position of the concept in the results and the actual position of the concept in the results, if they differ.

The tasks supported by this project are:
* download-collections
* relevancy-tests
* boost-tests

### Download collections

`lein run download-collections`

This task will download the metadata files for every concept-id included in the anomaly_tests.csv file. If the metadata has already been downloaded it will skip the download for that concept-id.

### Relevancy-tests

`lein run relevancy-tests`

This task will ingest all of the locally saved metadata files into a local CMR. In order to use this task you must have the CMR dev-system running locally. It will execute every test case listed in the anomaly_tests.csv file and print out a report.

The report will also be logged to local_test_runs.csv to allow a comparison between runs for the local user and a historical record during debugging.

If an optional argument -log-run-description is specified with a run description (i.e. "Increase entry title boost") by default the log will also be written to test_run_history.csv, which should be committed to the repository.

Usage: `lein run relevancy-tests -log-run-description "Base Run"`

An additional optional boolean argument, -log-history, is available if you want to specify a run description, but do not want to log to the test_run_history.csv file. By default, this option is true when -log-run-description is set.

Usage: `lein run relevancy-tests -log-run-description "Base Run" -log-history false`

### boost-tests

`lein run boost-tests -field entry-title`

This task will run the relevancy tests with the keyword score boost value for a particular field configured within a certain range. These tests are for finding the lowest boost value that yields maximum average discounted cumulative gain, i.e. the highest rate of success. All other boosts will maintain their default CMR boost value.

For example, if the boost field is entry-title and the boost range is 1.4 to 1.6, all of the relevancy tests will run 3 times with boosts of 1.4, 1.5, and 1.6 respectively. The output of each run will be saved to the local_test_runs.csv log and the boost test will output the lowest value that yielded the highest average discounted cumulative gain for the run.

Arguments:
* -field - (required) the field for which to configure the boost
* -min-value (optional) - the smallest boost value, defaults to 1.0
* -max-value (optional) - the highest boost value, defaults to 4.0

## License

Copyright © 2017 NASA
