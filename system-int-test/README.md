# cmr-system-int-test

System integration test for the CMR projects. It will ingest concepts using CMR Ingest Application and search/retrieve concepts using CMR Search Application. This is an end to end system integration test that verifies all CMR components working together.

## Usage

lein test

## Usage in the REPL. If you would like to see feedback on each assertion use the following command.

(ltest/run-test #'test.name.space/name-of-deftest)

## Usage in the REPL. If you just want to see if the test passes or not, you can simply call it as a function.

(test.name.space/name-of-deftest)

## License

Copyright © 2014 NASA
