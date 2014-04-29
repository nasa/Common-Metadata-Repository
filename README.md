# cmr-search-app

Provides a public search API for concepts in the CMR.

## TODO

  * Draw informal design for this application
  * Test against indexed collections as indexed by catalog-rest
    * This will be a way to make it work before other components are complete.
    * Use curl requests to send searches.


## Example CURL requests

### Common GET parameters

 * page_size - number of results per page - default is 10, max is 2000
 * pretty - return formatted results


### Find all collections
    curl -H "Accept: application/json" -i "http://localhost:3003/collections"

### Find all collections with a bad parameter
    curl -H "Accept: application/json" -i "http://localhost:3003/collections?foo=5"

### Find all collections with an entry title
    curl -H "Accept: application/json" -i "http://localhost:3003/collections?entry_title\[\]=DatasetId%204"

### Find all collections with a dataset id (alias for entry title)
    curl -H "Accept: application/json" -i "http://localhost:3003/collections?dataset_id\[\]=DatasetId%204"

### Find all collections with a entry title case insensitively
    curl -H "Accept: application/json" -i "http://localhost:3003/collections?entry_title\[\]=datasetId%204&options\[entry_title\]\[ignore_case\]=true"

### Find all collections with a entry title pattern
    curl -H "Accept: application/json" -i "http://localhost:3003/collections?entry_title\[\]=DatasetId*&options\[entry_title\]\[pattern\]=true"

### Find all collections with multiple dataset ids
    curl -H "Accept: application/json" -i "http://localhost:3003/collections?entry_title\[\]=DatasetId%204&entry_title\[\]=DatasetId%205"

### Find all collections with multiple temporal, the temporal datetime has to be in yyyy-MM-ddTHH:mm:ssZ format.
    curl -H "Accept: application/json" -i "http://localhost:3003/collections?temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z,30,60&temporal\[\]=2000-01-01T10:00:00Z,,30&temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"

### Find all granules
    curl -H "Accept: application/json" -i "http://localhost:3003/granules"

#### Find all granules with a granule-ur
    curl -H "Accept: application/json" -i "http://localhost:3003/granules?granule_ur\[\]=DummyGranuleUR"

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



### Find as XML
TODO implement support for retrieving in XML.
Also make sure enough information is returned that Catalog-REST can work.

    curl -H "Accept: application/xml" -i "http://localhost:3003/collections"


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
