# search-relevancy-test

Used to test the performance of CMR's search relevancy algorithm.

## Usage

Tests are run from the anomaly_tests.csv. Each line in the CSV file is a separate test. The anomaly number corresponds to this wiki page: https://wiki.earthdata.nasa.gov/display/CMR/Relevancy+Ranking+Suggestion+Box. The test number is for indicating multiple tests relating to the same anomaly. The concept-ids should be listed in the order they are expected to return from the given search.

To add a test, add a line to the CSV.

Currently the test is run via the relevancy-test function in core.clj and results are reported in the REPL.

The results report the expected position of the concept in the results and the actual position of the concept in the results, if they differ.

## License

Copyright © 2017 NASA
