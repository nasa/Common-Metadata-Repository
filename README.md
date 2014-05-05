# cmr-search-app

Provides a public search API for concepts in the CMR.

## Example CURL requests

### Common GET parameters/headers

#### Parameters

 * page_size - number of results per page - default is 10, max is 2000
 * pretty - return formatted results

#### Headers

  * Accept - specifies the format to return references in. Default is json.
    * `curl -H "Accept: application/xml" -i "http://localhost:3003/collections"`

### Search for Collections

#### Find all collections

    curl "http://localhost:3003/collections"

#### Find collections by concept id

A CMR concept id is in the format `<concept-type-prefix> <unique-number> "-" <provider-id>`

  * `concept-type-prefix` is a single capital letter prefix indicating the concept type. "C" is used for collections
  * `unique-number` is a single number assigned by the CMR during ingest.
  * `provider-id` is the short name for the provider. i.e. "LPDAAC_ECS"

Example: `C123456-LPDAAC_ECS`

    curl "http://localhost:3003/collections?concept_id\[\]=C123456-LPDAAC_ECS"

#### Find collections by entry title

One entry title

    curl "http://localhost:3003/collections?entry_title\[\]=DatasetId%204"

a dataset id (alias for entry title)

    curl "http://localhost:3003/collections?dataset_id\[\]=DatasetId%204"

with multiple dataset ids

    curl "http://localhost:3003/collections?entry_title\[\]=DatasetId%204&entry_title\[\]=DatasetId%205"

with a entry title case insensitively

    curl "http://localhost:3003/collections?entry_title\[\]=datasetId%204&options\[entry_title\]\[ignore_case\]=true"

with a entry title pattern

    curl "http://localhost:3003/collections?entry_title\[\]=DatasetId*&options\[entry_title\]\[pattern\]=true"

#### Find collections by entry id

One entry id

    curl "http://localhost:3003/collections?entry_id\[\]=SHORT_V5"

One dif\_entry\_id (alias for entry id)

    curl "http://localhost:3003/collections?dif_entry_id\[\]=SHORT_V5"

#### Find collections by archive center

    curl "http://localhost:3003/collections?archive_center\[\]=LARC"


#### Find collections with multiple temporal

The temporal datetime has to be in yyyy-MM-ddTHH:mm:ssZ format.

    curl "http://localhost:3003/collections?temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z,30,60&temporal\[\]=2000-01-01T10:00:00Z,,30&temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"

### Search for Granules

#### Find all granules

    curl "http://localhost:3003/granules"

#### Find granules with a granule-ur

    curl "http://localhost:3003/granules?granule_ur\[\]=DummyGranuleUR"

#### Find granules with a producer granule id

    curl "http://localhost:3003/granules?producer_granule_id\[\]=DummyID"

#### Find granules matching either granule ur or producer granule id

    curl "http://localhost:3003/granules?readable_granule_name\[\]=DummyID"

#### Find granules by additional attribute

Find an attribute attribute with name "PERCENTAGE" of type float with value 25.5

    curl "http://localhost:3003/granules?attribute\[\]=float,PERCENTAGE,25.5"

Find an attribute attribute with name "PERCENTAGE" of type float in range 25.5 - 30.

    curl "http://localhost:3003/granules?attribute\[\]=float,PERCENTAGE,25.5,30"

Find an attribute attribute with name "PERCENTAGE" of type float with min value 25.5.

    curl "http://localhost:3003/granules?attribute\[\]=float,PERCENTAGE,25.5,"

Find an attribute attribute with name "PERCENTAGE" of type float with max value 30.

    curl "http://localhost:3003/granules?attribute\[\]=float,PERCENTAGE,,30"

Find an additional attribute with name "X,Y,Z" with value 7.

    curl "http://localhost:3003/granules?attribute\[\]=float,X\,Y\,Z,7"

Find an additional attribute with name "X\Y\Z" with value 7.

    curl "http://localhost:3003/granules?attribute\[\]=float,X\Y\Z,7"

Multiple attributes can be provided. The default is for granules to match all the attribute parameters. This can be changed by specifying `or` option with `option[attribute][or]=true`.

### Retrieve concept with a given cmr-concept-id
    curl -i "http://localhost:3003/concepts/G100000-PROV1"

## Search Flow

### Stage 1: Convert to query model

/granules?provider=PROV1&dataset_id=foo&cloud_cover=50

  * Query
    * type: granule
    * condition:
      * AND
        * collection_query_condition:
          * condition:
            * provider=PROV1
        * collection_query_condition:
          * condition
            * dataset_id=foo
        * NumericRange
          * cloud_cover=50

### Stage 2: Add Acls to query


In a future sprint lookup acls and convert to query conditions then add on to the query.




### Stage 3: Resolve Dataset Query Conditions

#### A: Merge dataset query conditiosn

query = Search::Simplification::DatasetQueryConditionSimplifier.simplify(query)

  * Query
    * type: granule
    * condition:
      * AND
        * collection_query_condition:
          * condition:
            * AND
              * provider=PROV1
              * dataset_id=foo
        * NumericRange
          * cloud_cover=50

#### B: Resolve dataset query conditiosn

query = ElasticSearch::DatasetQueryResolver.resolve_collection_query_conditions(query)

Executes this query for collections
  * Query
    * type: collection
    * condition:
      * AND
        * provider=PROV1
        * dataset_id=foo

  * Query
    * type: granule
    * condition:
      * AND
        * String
          * echo_collection_id=C5-PROV1
        * NumericRange
          * cloud_cover=50


### Normal path...



## Prerequisites

You will need [Leiningen][1] 1.7.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein ring server

## License

Copyright © 2014 NASA
