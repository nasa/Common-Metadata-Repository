# cmr-es-spatial-plugin

A Elastic Search plugin that enables spatial search entirely within elastic.

To package es dependencies:

`lein with-profile es-deps,provided uberjar`

or

`lein install-es-deps`

This will package dependencies in es-deps/. These deps must be included in the
elasticsearch classpath.

To create the spatial script uberjar for use in elasticsearch:

`lein with-profile es-plugin,provided uberjar`

or

`lein install-es-plugin`

To package the spatial script for use in elastic:

`lein package-es-plugin`

This will create a zip in target/ ready for installation in elasticsearch.

## License

Copyright © 2021 NASA
