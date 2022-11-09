# Generic Documents

Generic documents are documents which conform to the "Generic" API in CMR. These are JSON documents validated against a known [schema][schema] file.


## Configuration

If adding a new document, you will need to update the defconf variable by either setting an ENV for global change, or by updating the default value in [/common-lib/src/cmr/common/config.clj](/common-lib/src/cmr/common/config.clj). The format for this value is either JSON for an ENV variable or a clojure map if setting directly in the default attribute of the defconfig like this:

	(defconfig approved-pipeline-documents
  		{:default {:grid ["0.0.1"]
             :data-quality-summary ["1.0.0"]
             :order-option ["1.0.0"]
             :service-entry ["1.0.0"]
             :service-option ["1.0.0"]}
   		:parser #(json/parse-string % true)})

When setting in an ENV or in AWS, use the JSON format:

	"{\"grid\": [\"0.0.1\"],
    \"data-quality-summary\": [\"1.0.0\"],
    \"order-option\": [\"1.0.0\"],
    \"service-entry\": [\"1.0.0\"],
    \"service-option\": [\"1.0.0\"]}"

Each setting consists of a key, which is the name for the Generic which must be unique, and a list of version numbers. These values *must* match parts of a file system path under "schemas". For example, the order-option value must resolve to:

	./{CMR-Root}/schemas/order-option/v0.0.1/
		README.md
		index.json
		metadata.json
		schema.json

CMR will search for Generic definitions using the lower case value of the key (name) and the version number prefixed with a "v". Inside the directory there must be 4 files, three of which are directly read by CMR:

* README.md - for humans
* index.json - Search/Index configuration settings, must comply with [Index Schema][schema-index].
* metadata.json - sample record, may be called by system-int-tests
* schema.json - A schema document conforming to [JSON Schema][schema].

### Running CMR

Run CMR as normal, however if you wish to confirm which schemas are configured, look for the following in the logs:

	Generic documents pipeline supports:

Followed by a list of configured Generic Documents and the supported versions.

## Adding/Updating New Documents

Creating:

1. Create a new directory in the [EMFD Generics][schema-other] repository.
2. In the directory create a README.md file
3. Create a `CHANGELOG.MD` file and populate like other formats do
4. Create a directory with the semantic version number prefixed with the letter `v`
	1. Version 1.0.0 would be `v1.0.0`.
	2. Information on [Semantic Versioning][semver]
5. Create at least a metadata.json and schema.json file inside the version number.
6. Commit, Get approved, Merge
7. Copy all files to the `schemas` directory under CMR
8. call `cmr setup <action>`:
	* `cmr setup dev` will update schemas
	* `cmr setup schemas` to force copy to all projects and do no other action
	* `(user/reset)` within the repl will also trigger a copy

Updating is much the same as start, create a new version folder, populate it.

DON'T forget to update the change log!

Commit and distribute to CMR as done in the addition steps.

## Generic Document Pipeline API Endpoints

The API endpoints are generated from a pre-configured list of document types. The type is singular in
ingest and plural in search. The examples below use the "order-option"/"order-options" type, with a
native ID of "order-option-1".

#### ingest
	curl -v -XPOST -H "$TOKEN" -H "Content-Type:application/vnd.nasa.cmr.umm+json" "https://cmr.earthdata.nasa.gov/ingest/order-option/order-option-1?provider=PROV1" -d @order-option-1.json
	curl -v -XPUT -H "$TOKEN" -H "Content-Type:application/vnd.nasa.cmr.umm+json" "https://cmr.earthdata.nasa.gov/ingest/order-option/order-option-1?provider=PROV1" -d @order-option-1.json
	curl -v -XGET -H "$TOKEN" -H "Content-Type:application/vnd.nasa.cmr.umm+json" "https://cmr.earthdata.nasa.gov/ingest/order-option/order-option-1?provider=PROV1"
	curl -v -XDELETE -H "$TOKEN" "https://cmr.earthdata.nasa.gov/ingest/order-option/order-option-1?provider=PROV1"

#### search
	curl -v -H "$TOKEN" https://cmr.earthdata.nasa.gov/search/concepts/OO1200000002-PROV1
	curl -v -H "$TOKEN" https://cmr.earthdata.nasa.gov/search/order-options?name="With%20Browse"
	curl -v -H "$TOKEN" https://cmr.earthdata.nasa.gov/search/order-options.json?name="With%20Browse"
	curl -v -H "$TOKEN" https://cmr.earthdata.nasa.gov/search/order-options.json?provider="PROV1"
	curl -v -H "$TOKEN" https://cmr.earthdata.nasa.gov/search/order-options.json?concept_id="OO1200000002-PROV1"

Note also that concept IDs begin with a prefix unique to that document type. (Above see "OO" for order
option)

#### re-index through bootstrap-app
	curl -v -H "$TOKEN" https://cmr.earthdata.nasa.gov/bootstrap/bulk_index/grids/
	curl -v -H "$TOKEN" https://cmr.earthdata.nasa.gov/bootstrap/bulk_index/grids/PROV1

----

Copyright © 2014-2022 United States Government as represented by the Administrator of the National Aeronautics and Space Administration. All Rights Reserved.


[schema]: https://json-schema.org "JSON Schema definition"
[schema-other]: https://git.earthdata.nasa.gov/scm/emfd/otherschemas.git "Generic Schema Repository"
[schema-index]: https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas/browse/Index "Index configuration definition"
[semver]: https://semver.org "Information on semantic versioning"
