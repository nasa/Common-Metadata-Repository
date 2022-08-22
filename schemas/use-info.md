# Usage under CMR

## Implementation
When a repl is created and the command `(user/reset)` is run, the entire schemas
directory will be copied into the resources folders of the following applications:

* indexer-app
* ingest-app
* metadata-db-app
* search-app
* system-int-test

Each time `(user/reset)` is called, if a schemas folder already exists inside
one of the above application resources folders, that folder will be replaced by
the source schemas folder with content. This will help each individual
application resource directories stay up to date and reduce errors.

## License

Copyright © 2022-2022 United States Government as represented by the
Administrator of the National Aeronautics and Space Administration. All Rights
Reserved.
