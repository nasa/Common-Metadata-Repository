# cmr-index-set-app

FIXME

## Design

An Index Set is a logical set of indexes in Elasticsearch for indexing and searching for concepts. Multiple index sets would be needed to allow for reindexing or migrations.

An Index Set consists of the following:

  * name - A descriptive human readable name.
  * id - A positive integer to uniquely identify an index set. The id is used as a prefix for the indexes in elasticsearch.
  * concept-type-index-config - A map of concept types to Index configs


## Running

To start a web server for the application, run:

    lein run

## Add curl statement after test somemore testing. Mean while see docs attached to issue CMR-152 for design intent.

## License

Copyright © 2014 NASA
