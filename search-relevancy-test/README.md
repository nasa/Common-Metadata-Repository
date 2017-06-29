# search-relevancy-test

Used to test the performance of CMR's search relevancy algorithm.

## Usage

Tests are run from the anomaly_tests.csv. Each line in the CSV file is a separate test. The anomaly number corresponds to this wiki page: https://wiki.earthdata.nasa.gov/display/CMR/Relevancy+Ranking+Suggestion+Box. The test number is for indicating multiple tests relating to the same anomaly. The concept-ids should be listed in the order they are expected to return from the given search.

To add a test, add a line to the CSV.

The results report the expected position of the concept in the results and the actual position of the concept in the results, if they differ.

There are two main tasks supported by this project - download-collections and relevancy-tests.

### Download collections

`lein run download-collections`

This task will download the metadata files for every concept-id included in the anomaly_tests.csv file. If the metadata has already been downloaded it will skip the download for that concept-id.

### Relevancy-tests

`lein run relevancy-tests`

This task will ingest all of the locally saved metadata files into a local CMR. In order to use this task you must have the CMR dev-system running locally. It will execute every test case listed in the anomaly_tests.csv file and print out a report.

## License

Copyright © 2017 NASA
