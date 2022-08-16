## Implementation
When a repl is created and the command `(reset)` is run, the entire schemas directory will be copied
into the resources folders of the following applications:
* indexer-app
* ingest-app
* metadata-db-app
* search-app
* system-int-test

Each time `(reset)` is called, if a schemas folder already exists inside one of the above application's
resources folder, that folder will be replaced by the source schemas folder and its contents.
This will help each indiviudal application's resources stay up to date and reduce errors.
