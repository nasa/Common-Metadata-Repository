# cmr-search-app

Provides a public search API for concepts in the CMR.

## API Documentation

API Docs are located in api_docs.md. They can be generated into the static site by running `lein generate-docs` This takes a while so we will commit the generated file for now so building the uberjar does not take a very long time. You can also generate the documents with a pre-started REPL by running `(load-file "./support/generate_docs.clj")` If you're running the REPL from dev-system you need to correct the path to generate_docs.clj.

## License

Copyright © 2014 NASA
