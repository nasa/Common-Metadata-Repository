# cmr-search-app

Provides a public search API for concepts in the CMR.

## API Documentation

API docs for the search-app project are located in `api_docs.md`. They can be
generated into the static site by running the following:

```
$ lein generate-docs
```

Due to the fact that this generally takes a while on most systems, we have
generated it and committed the resulting file in order to bring the total
build time (e.g, creating an uberjar) down.

Additionally, you can also generate the documents with a pre-started REPL by
running:

```clj
(load-file "./support/generate_docs.clj")
```

Note that if you're running the REPL from `dev-system` you need to correct the
path to `generate_docs.clj`.

## License

Copyright © 2014, 2017 NASA
