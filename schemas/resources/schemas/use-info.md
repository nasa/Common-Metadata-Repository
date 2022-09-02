# Usage under CMR

These files, hosted on [bitbucket][origin] are in support of CMR which can be
found on [github][cmr].

## Implementation
These schemas are copied into a CMR project folder called `schemas` which are then
packaged into a JAR file and shared with the following projects:

* indexer-app
* ingest-app
* metadata-db-app
* search-app (pending)
* system-int-test

No other action is needed by CMR microservices to use these applications. To add
the jar to another service, add `[nasa-cmr/cmr-schemas "0.0.1-SNAPSHOT"]` to the
list of dependencies in the appropriate `project.clj` file.

## Creating new schemas

When adding a new schema, create a directory where the directory title is the
same name that is intended to be used in the `Name` field inside the
`MetadataSpecification` field. This Name field is also the name that will be
used on CMR urls so avoid characters which are difficult to represent in a file
system or URL. Stick to lower case letters. Each schema directory contains a
`CHANGELOG.md`, `README.md` and a set of version directories. Version
directories start with "v" and use [Semantic Versioning][semver].
Each version directory then contains at the minimal a `metadata.json` and
`schema.json` file. These must be valid together when validated. Optionally
there can be an `index.json` file or other documents needed to support CMR.

Example:

* grid/
    * CHANGELOG.md
    * README.md
    * v0.0.1/
        * index.json
        * metadata.json
        * schema.json

All cases must be set as shown above. CMR will then build paths using the
`MetadataSpecification` field in the Generic to build a path such as this:

    ./resources/schemas/grid/v0.0.1/schema.json

## License

Copyright © 2022-2022 United States Government as represented by the
Administrator of the National Aeronautics and Space Administration. All Rights
Reserved.

[origin]: https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas "The repository for Generic Documents"
[cmr]: https://github.com/nasa/Common-Metadata-Repository "CMR Git Repository"
[semver]: https://semver.org "Semantic Versioning description"
