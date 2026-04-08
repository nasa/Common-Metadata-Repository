---
title: Searchable Components
description: Provides information on additional types that are searchable by the CMR API.
---

### <a name="service"></a> Service

A service enables data to be accessed via a universal resource locator, and has options to enable a variety of transformations to be performed on the data, e.g. spatial, temporal, variable subsetting, reprojection or reformatting. Service metadata is in JSON format and conforms to [UMM-S Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/unified-metadata-model/browse/service).

#### <a name="searching-for-services"></a> Searching for Services

Services can be searched for by sending a request to `%CMR-ENDPOINT%/services`. XML reference, JSON, and UMM JSON response formats are supported for services search.

Service search results are paged. See [Paging Details](#paging-details) for more information on how to page through service search results.

##### <a name="service-search-params"></a> Service Search Parameters

The following parameters are supported when searching for services.

##### Standard Parameters
* page_size
* page_num
* pretty

##### Service Matching Parameters

These parameters will match fields within a service. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are ORed together.

* name
  * options: pattern, ignore_case
* type
  * options: pattern, ignore_case
* provider
  * options: pattern, ignore_case
* native_id
  * options: pattern, ignore_case
* concept_id
* keyword (free text)
  * keyword search is case insensitive and supports wild cards ? and *. There is a limit of 30 wild cards allowed in keyword searches. Within 30 wild cards, there's also limit on the max keyword string length. The longer the max keyword string length, the less number of keywords with wild cards allowed.

The following fields are indexed for keyword (free text) search:

* Service name
* Service long name
* Service type
* Service version
* Service keywords (category, term specific term, topic)
* Service organizations (short and long names, roles)
* Contact persons (first names, contacts last names, roles)
* Contact groups (group name, roles)
* URL (description, URL value)
* Ancillary keywords

##### <a name="service-search-response"></a> Service Search Response

##### XML Reference
The XML reference response format is used for returning references to search results. It consists of the following fields:

|   Field    |                    Description                     |
| ---------- | -------------------------------------------------- |
| hits       | the number of results matching the search query    |
| took       | time in milliseconds it took to perform the search |
| references | identifying information about each search result   |

The `references` field may contain multiple `reference` entries, each consisting of the following fields:

|    Field    |                                                   Description                                                   |
| ----------- | --------------------------------------------------------------------------------------------------------------- |
| name        | the value of the Name field in service metadata.                                                                |
| id          | the CMR identifier for the result                                                                               |
| location    | the URL at which the full metadata for the result can be retrieved                                              |
| revision-id | the internal CMR version number for the result                                                                  |

__Example__

```
curl -i "%CMR-ENDPOINT%/services?name=Service1&pretty=true"

HTTP/1.1 200 OK
Content-Type: application/xml; charset=UTF-8
Content-Length: 393

<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>17</took>
    <references>
        <reference>
            <name>Service1</name>
            <id>S1200000007-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/S1200000007-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
##### JSON
The JSON response includes the following fields.

* hits - How many total variables were found.
* took - How long the search took in milliseconds
* items - a list of the current page of services with the following fields
  * concept_id
  * revision_id
  * provider_id
  * native_id
  * name
  * long_name
  * associations - a map of the concept ids of concepts that are associated with the service.
  * association-details - a map of the concept ids, optional revision ids, and optional data of concepts that are associated with the service.

__Example__

```
curl -g -i "%CMR-ENDPOINT%/services.json?pretty=true"

HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Content-Length: 944

{
  "hits" : 4,
  "took" : 2,
  "items" : [ {
    "concept_id" : "S1200000012-PROV2",
    "revision_id" : 1,
    "provider_id" : "PROV2",
    "native_id" : "svc3",
    "name" : "a sub for service2",
    "long_name" : "OPeNDAP Service for AIRS Level-3 retrieval products"
  }, {
    "concept_id" : "S1200000013-PROV2",
    "revision_id" : 1,
    "provider_id" : "PROV2",
    "native_id" : "serv4",
    "name" : "s.other",
    "long_name" : "OPeNDAP Service for AIRS Level-3 retrieval products"
  }, {
    "concept_id" : "S1200000010-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "SVC1",
    "name" : "Service1",
    "long_name" : "OPeNDAP Service for AIRS Level-3 retrieval products"
  }, {
    "concept_id" : "S1200000011-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "svc2",
    "name" : "Service2",
    "long_name" : "OPeNDAP Service for AIRS Level-3 retrieval products",
    "associations": {
      "collections": ["C1200000007-PROV1"],
      "tools": ["TL1200000011-PROV1"]
    },
    "association-details": {
      "collections": [{"concept-id": "C1200000007-PROV1",
                       "data": {"formatting-type": "zarr",
                                "regridding-type": {"xyz": "zyx"}}}],
      "tools": [{"concept-id": "TL1200000011-PROV1"}]
    }
  } ]
}
```
##### UMM JSON
The UMM JSON response contains meta-metadata of the service and the UMM fields.

__Example__

```
curl -g -i "%CMR-ENDPOINT%/services.umm_json?name=NSIDC_AtlasNorth&pretty=true"
HTTP/1.1 200 OK
Content-Type: application/vnd.nasa.cmr.umm_results+json;version=1.1; charset=utf-8

{
  "hits": 1,
  "took": 14,
  "items": [
    {
      "meta": {
        "revision-id": 2,
        "deleted": false,
        "format": "application/vnd.nasa.cmr.umm+json",
        "provider-id": "PROV1",
        "native-id": "svc1",
        "concept-id": "S1200000009-PROV1",
        "revision-date": "2017-08-14T20:12:43Z",
        "concept-type": "service"
      },
      "umm": {
        "Name": "NSIDC_AtlasNorth",
        "Type": "WCS",
        "Version": "1.1.1",
        "Description": "Atlas of the Cryosphere: Northern Hemisphere",
        "OnlineResource": {
          "Linkage": "https://nsidc.org/cgi-bin/atlas_north",
          "Name": "NSIDC WCS Service for the Northern Hemisphere",
          "Description": "NSIDC WCS Service, atlas of the Cryosphere: Northern Hemisphere"
        },
        "ServiceOptions": {
          "SubsetType": [
            "Spatial",
            "Temporal",
            "Variable"
          ],
          "SupportedProjections": [
            "WGS 84 / UPS North (N,E)",
            "WGS84 - World Geodetic System 1984",
            "NSIDC EASE-Grid North",
            "Google Maps Global Mercator -- Spherical Mercator"
          ],
          "InterpolationType": [
            "Nearest Neighbor",
            "Bilinear Interpolation"
          ],
          "SupportedFormats": [
            "image/png",
            "image/vnd.wap.wbmp"
          ]
        },
        "Layer": [
          {
            "Name": "sea_ice_concentration_01"
          },
          {
            "Name": "sea_ice_concentration_02"
          },
          {
            "Name": "greenland_elevation"
          }
        ]
      }
    }
  ]
}
```

##### <a name="retrieving-all-revisions-of-a-service"></a> Retrieving All Revisions of a Service

In addition to retrieving the latest revision for a service parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON and UMM JSON response formats are supported for all revision searches. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true".

    curl "%CMR-ENDPOINT%/services?concept_id=S1200000010-PROV1&all_revisions=true&pretty=true"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>3</hits>
        <took>3</took>
        <references>
            <reference>
                <name>Service1</name>
                <id>S1200000010-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/S1200000010-PROV1/3</location>
                <revision-id>3</revision-id>
            </reference>
            <reference>
                <name>Service1</name>
                <id>S1200000010-PROV1</id>
                <deleted>true</deleted>
                <revision-id>2</revision-id>
            </reference>
            <reference>
                <name>Service1</name>
                <id>S1200000010-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/S1200000010-PROV1/1</location>
                <revision-id>1</revision-id>
            </reference>
        </references>
    </results>
```

##### <a name="sorting-service-results"></a> Sorting Service Results

By default, service results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

###### Valid Service Sort Keys
  * `name`
  * `long_name`
  * `provider`
  * `revision_date`

Examples of sorting by provider id in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/services?sort_key\[\]=-provider"
    curl "%CMR-ENDPOINT%/services?sort_key\[\]=%2Bprovider"

#### <a name="service-access-control"></a> Service Access Control

Access to service and service association is granted through the provider via the INGEST_MANAGEMENT_ACL. Associating and dissociating collections with a service is considered an update.

#### <a name="service-association"></a> Service Association

A service identified by its concept id can be associated with collections through a list of collection concept revisions and an optional data payload in JSON format.
The service association request normally returns status code 200 with a response that consists of a list of individual service association responses, one for each service association attempted to create.

Expected Response Status:
<ul>
    <li>200 OK -- if all associations succeeded</li>
    <li>207 MULTI-STATUS -- if some associations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all associations failed due to user error</li>
</ul>

Expected Response Body:

The response body will consist of a list of service association objects
Each association object will have:
<ul>
    <li>An `associated_item` field</li>
        <ul>
            <li>The `associated_item` field value has the collection concept id and the optional revision id that is used to identify the collection during service association.</li>
        </ul>
    <li>Either a `service_association` field with the service association concept id and revision id when the service association succeeded or an `errors` field with detailed error message when the service association failed.</li>
</ul>

IMPORTANT: Service association requires that user has update permission on INGEST_MANAGEMENT_ACL for the collection's provider.

Here is a sample service association request and its response when collection C1200000005-PROV1 exists and C1200000006-PROV1 does not:
```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/services/S1200000008-PROV1/associations -d \
'[{"concept_id": "C1200000005-PROV1", "data": {"order_option": "OO1200445588-PROV1"}},
  {"concept_id": "C1200000006-PROV1"}]'

HTTP/1.1 207 MULTI-STATUS
Content-Type: application/json; charset=UTF-8
Content-Length: 168

[
  {
    "status": 200,
    "service_association":{
      "concept_id":"SA1200000009-CMR",
      "revision_id":1
    },
    "associated_item":{
      "concept_id":"C1200000005-PROV1"
    }
  },
  {
    "status": 207,
    "errors":[
      "Collection [C1200000006-PROV1] does not exist or is not visible."
    ],
    "associated_item":{
      "concept_id":"C1200000006-PROV1"
    }
  }
]
```

#### <a name="service-dissociation"></a> Service Dissociation

A service identified by its concept id can be dissociated from collections through a list of collection concept revisions similar to service association requests.

Expected Response Status:
<ul>
    <li>200 OK -- if all dissociations succeeded</li>
    <li>207 MULTI-STATUS -- if some dissociations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all dissociations failed due to user error</li>
</ul>

IMPORTANT: Service dissociation requires that user has update permission on INGEST_MANAGEMENT_ACL for either the collection's provider, or the service's provider.

Service dissociation example where some dissociations succeed and others failed due to user error
```
curl -XDELETE -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/services/S1200000008-PROV1/associations -d \
'[{"concept_id": "C1200000005-PROV1"},
  {"concept_id": "C1200000006-PROV1"},
  {"concept_id": "C1200000007-PROV1"}]'

HTTP/1.1 207 MULTI-STATUS
Content-Type: application/json; charset=UTF-8
Content-Length: 168

[
  {
    "status": 200,
    "service_association":{
      "concept_id":"SA1200000009-CMR",
      "revision_id":2
    },
    "associated_item":{
      "concept_id":"C1200000005-PROV1"
    }
  },
  {
    "status": 200,
    "warnings":[
      "Service [S1200000008-PROV1] is not associated with collection [C1200000006-PROV1]."
    ],
    "associated_item":{
      "concept_id":"C1200000006-PROV1"
    }
  },
  {
    "status": 400,
    "errors":[
      "Collection [C1200000007-PROV1] does not exist or is not visible."
    ],
    "associated_item":{
      "concept_id":"C1200000007-PROV1"
    }
  },
  {
    "status": 400,
    "errors":[
      "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of collection [C1200000008-PROV2] or provider of service/tool to delete the association."
    ],
    "associated_item":{
      "concept_id":"C1200000008-PROV2"
    }
  }
]
```

### <a name="variable"></a> Variable

Variables are measurement variables belonging to collections/granules that are processable by services. Variable metadata is stored in the JSON format and conforms to [UMM-Var Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/unified-metadata-model/browse/variable) schema.

#### <a name="searching-for-variables"></a> Searching for Variables

Variables can be searched for by sending a request to `%CMR-ENDPOINT%/variables`. XML reference, JSON and UMM JSON response formats are supported for variables search.

Variable search results are paged. See [Paging Details](#paging-details) for more information on how to page through variable search results.

##### <a name="variable-search-params"></a> Variable Search Parameters

The following parameters are supported when searching for variables.

##### Standard Parameters
* page_size
* page_num
* pretty

##### Variable Matching Parameters

These parameters will match fields within a variable. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are ORed together.

* name
  options: pattern, ignore_case
* provider
  options: pattern, ignore_case
* native_id
  options: pattern, ignore_case
* concept_id
* measurement_identifiers
  options: ignore_case, or
* instance_format
  options: pattern
measurement_identifiers parameter is a nested parameter with subfields: contextmedium, object and quantity. Multiple measurement_identifiers can be specified via different indexes to search variables. The following example searches for variables that have at least one measurement_identifier with contextmedium of Med1, object of Object1 and quantity of Q1, and another measurement_identifier with contextmedium of Med2 and object of Obj2.

__Example__

````
curl -g "%CMR-ENDPOINT%/variables?measurement_identifiers\[0\]\[contextmedium\]=Med1&measurement_identifiers\[0\]\[object\]=Object1&measurement_identifiers\[0\]\[quantity\]=Q1&measurement_identifiers\[1\]\[contextmedium\]=med2&measurement_identifiers\[2\]\[object\]=Obj2"
````

The multiple measurement_identifiers are ANDed by default. User can specify `options[measurement-identifiers][or]=true` to make the measurement_identifiers ORed together.

* keyword (free text)
  keyword search is case insensitive and supports wild cards ? and *. There is a limit of 30 wild cards allowed in keyword searches. Within 30 wild cards, there's also limit on the max keyword string length. The longer the max keyword string length, the less number of keywords with wild cards allowed.

The following fields are indexed for keyword (free text) search:

* Variable name
* Variable long name
* Science keywords (category, detailed variable, term, topic, variables 1-3)
* Variable set names
* Associated collection concept ids
* Variable concept id
* InstanceInformation Format

##### <a name="variable-search-response"></a> Variable Search Response

##### XML Reference
The XML reference response format is used for returning references to search results. It consists of the following fields:

|   Field    |                    Description                     |
| ---------- | -------------------------------------------------- |
| hits       | the number of results matching the search query    |
| took       | time in milliseconds it took to perform the search |
| references | identifying information about each search result   |

The `references` field may contain multiple `reference` entries, each consisting of the following fields:

|    Field    |                                                   Description                                                   |
| ----------- | --------------------------------------------------------------------------------------------------------------- |
| name        | the value of the Name field in variable metadata.                                                               |
| id          | the CMR identifier for the result                                                                               |
| location    | the URL at which the full metadata for the result can be retrieved                                              |
| revision-id | the internal CMR version number for the result                                                                  |

__Example__

```
curl -i "%CMR-ENDPOINT%/variables?pretty=true&name=Variable1"
```

__Example Response__

```
HTTP/1.1 200 OK
Content-Type: application/xml; charset=UTF-8
Content-Length: 393

<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>17</took>
    <references>
        <reference>
            <name>Variable1</name>
            <id>V1200000007-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/V1200000007-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
##### JSON
The JSON response includes the following fields.

* hits - How many total variables were found.
* took - How long the search took in milliseconds
* items - a list of the current page of variables with the following fields
  * concept_id
  * definition
  * revision_id
  * provider_id
  * native_id
  * name
  * long_name
  * science_keywords
  * associations (if applicable)
  * association_details(if applicable)
  * instance_information (if applicable)

__Example__

```
curl -g -i "%CMR-ENDPOINT%/variables.json?pretty=true&name=Var*&options[name][pattern]=true"
```

__Example Response__

```
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Content-Length: 292

{
  "hits" : 2,
  "took" : 2,
  "items" : [ {
    "concept_id" : "V1200000007-PROV1",
    "revision_id" : 3,
    "provider_id" : "PROV1",
    "native_id" : "var1",
    "name" : "Variable1",
    "long_name" : "A long UMM-Var name",
    "definition": "A definition for the variable",
    "science_keywords": [
                {
                    "Category": "sk-A",
                    "Topic": "sk-B",
                    "Term": "sk-C"
                }
            ]
  }, {
    "concept_id" : "V1200000008-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "var2",
    "name" : "Variable2",
    "long_name" : "A long UMM-Var name",
    "definition": "A definition for the variable",
    "science_keywords": [
                {
                    "Category": "sk-A",
                    "Topic": "sk-B",
                    "Term": "sk-C"
                }
            ]
  } ]
}
```
__Example Response With Associations__

```
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Content-Length: 512

{
  "hits" : 1,
  "took" : 41,
  "items" : [ {
    "concept_id" : "V1200000022-PROV1",
    "revision_id" : 2,
    "provider_id" : "PROV1",
    "native_id" : "sample-variable",
    "name" : "methaneVar",
    "long_name" : "Total Methane",
    "definition": "A definition for the variable",
    "science_keywords": [
                {
                    "Category": "sk-A",
                    "Topic": "sk-B",
                    "Term": "sk-C"
                }
            ],
    "associations" : {
      "collections" : [ "C1200000021-PROV1" ]
    },
    "association_details" : {
      "collections" : [ {
        "data" : "\"This is some sample data association\"",
        "concept_id" : "C1200000021-PROV1"
      } ]
    }
  } ]
}
```

##### UMM JSON
The UMM JSON response contains meta-metadata of the variable, the UMM fields and the associations field if applicable. The associations field only applies when there are collections associated to the variable or other concepts generically associated to the variable.

__Example__

```
curl -g -i "%CMR-ENDPOINT%/variables.umm_json?name=Variable1234&pretty=true"
HTTP/1.1 200 OK
Content-Type: application/vnd.nasa.cmr.umm_results+json;version=1.1; charset=utf-8
Content-Length: 1177

{
  "hits" : 1,
  "took" : 14,
  "items" : [ {
    "meta" : {
      "revision-id" : 2,
      "deleted" : false,
      "format" : "application/vnd.nasa.cmr.umm+json",
      "provider-id" : "PROV1",
      "native-id" : "var1",
      "concept-id" : "V1200000009-PROV1",
      "revision-date" : "2017-08-14T20:12:43Z",
      "concept-type" : "variable"
    },
    "umm" : {
      "VariableType" : "SCIENCE_VARIABLE",
      "DataType" : "float32",
      "Offset" : 0.0,
      "ScienceKeywords" : [ {
        "Category" : "sk-A",
        "Topic" : "sk-B",
        "Term" : "sk-C"
      } ],
      "Scale" : 1.0,
      "FillValues" : [ {
        "Value" : -9999.0,
        "Type" : "Science"
      } ],
      "Sets" : [ {
        "Name" : "Data_Fields",
        "Type" : "Science",
        "Size" : 2,
        "Index" : 2
      } ],
      "Dimensions" : [ {
        "Name" : "Solution_3_Land",
        "Size" : 3
      } ],
      "Definition" : "Defines the variable",
      "Name" : "Variable1234",
      "Units" : "m",
      "LongName" : "A long UMM-Var name"
    },
    "associations" : {
      "collections" : [ {
        "concept-id" : "C1200000007-PROV1"
      } ]
    }
  } ]
}
```

##### <a name="retrieving-all-revisions-of-a-variable"></a> Retrieving All Revisions of a Variable

In addition to retrieving the latest revision for a variable parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON and UMM JSON response formats are supported for all revision searches. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true".

    curl "%CMR-ENDPOINT%/variables?concept_id=V1200000010-PROV1&all_revisions=true&pretty=true"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
      <hits>3</hits>
      <took>3</took>
      <references>
          <reference>
              <name>Variable1</name>
              <id>V1200000010-PROV1</id>
              <location>%CMR-ENDPOINT%/concepts/V1200000010-PROV1/3</location>
              <revision-id>3</revision-id>
          </reference>
          <reference>
              <name>Variable1</name>
              <id>V1200000010-PROV1</id>
              <deleted>true</deleted>
              <revision-id>2</revision-id>
          </reference>
          <reference>
              <name>Variable1</name>
              <id>V1200000010-PROV1</id>
              <location>%CMR-ENDPOINT%/concepts/V1200000010-PROV1/1</location>
              <revision-id>1</revision-id>
          </reference>
      </references>
  </results>
```

##### <a name="sorting-variable-results"></a> Sorting Variable Results

By default, variable results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

###### Valid Variable Sort Keys
  * `name`
  * `long_name`
  * `provider`
  * `revision_date`

Examples of sorting by long_name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/variables?sort_key\[\]=-long_name"
    curl "%CMR-ENDPOINT%/variables?sort_key\[\]=%2Blong_name"

#### <a name="variable-access-control"></a> Variable Access Control

Access to variable and variable association is granted through the provider via the INGEST_MANAGEMENT_ACL. Users can only create, update, or delete a variable if they are granted the appropriate permission. Associating and dissociating collections with a variable is considered an update.

### <a name="tool"></a> Tool

UMM-T provides metadata to support the User Interface/User Experience (UI/UX)-driven approach to Tools. Specifically, when a user wants to know the tools available for a specific collection and makes selections via the Earthdata Search UI, options are presented showing what operating systems or languages are supported.
The UMM-T model in MMT enables the population of the tool options, either web user interface or downloadable tool options are surfaced in the UI to support these selections. Each UMM-T record contains metadata for tools and other information such as contact groups or contact persons, tool keywords, and supported inputs and outputs.

#### <a name="searching-for-tools"></a> Searching for Tools

Tools can be searched for by sending a request to `%CMR-ENDPOINT%/tools`. XML reference, JSON, and UMM JSON response formats are supported for tools search.

Tool search results are paged. See [Paging Details](#paging-details) for more information on how to page through tool search results.

##### <a name="tool-search-params"></a> Tool Search Parameters

The following parameters are supported when searching for tools.

##### Standard Parameters
* page_size
* page_num
* pretty

##### Tool Matching Parameters

These parameters will match fields within a tool. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are ORed together.

* name
  * options: pattern, ignore_case
* provider
  * options: pattern, ignore_case
* native_id
  * options: pattern, ignore_case
* concept_id
* keyword (free text)
  * keyword search is case insensitive and supports wild cards ? and *. There is a limit of 30 wild cards allowed in keyword searches. Within 30 wild cards, there's also limit on the max keyword string length. The longer the max keyword string length, the less number of keywords with wild cards allowed.

The following fields are indexed for keyword (free text) search:

* Tool name
* Tool long name
* Tool version
* Tool keywords (category, term, specific term, topic)
* Tool organizations (short and long names, roles, url-value)
* Contact persons (first names, contacts last names, roles)
* Contact groups (group name, roles)
* RelatedURL (description, subtype, type, URL, URL content type)
* URL
* Ancillary keywords

##### <a name="tool-search-response"></a> Tool Search Response

##### XML Reference
The XML reference response format is used for returning references to search results. It consists of the following fields:

|   Field    |                    Description                     |
| ---------- | -------------------------------------------------- |
| hits       | the number of results matching the search query    |
| took       | time in milliseconds it took to perform the search |
| references | identifying information about each search result   |

The `references` field may contain multiple `reference` entries, each consisting of the following fields:

|    Field    |                                                   Description                                                   |
| ----------- | --------------------------------------------------------------------------------------------------------------- |
| name        | the value of the Name field in tool metadata.                                                                |
| id          | the CMR identifier for the result                                                                               |
| location    | the URL at which the full metadata for the result can be retrieved                                              |
| revision-id | the internal CMR version number for the result                                                                  |

__Example__

```
curl -i "%CMR-ENDPOINT%/tools?name=someTool1&pretty=true"

HTTP/1.1 200 OK
Content-Type: application/xml; charset=UTF-8
Content-Length: 393

<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>5</took>
    <references>
        <reference>
            <name>someTool1</name>
            <id>TL1200000005-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/TL1200000005-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
##### JSON
The JSON response includes the following fields.

* hits - How many total variables were found.
* took - How long the search took in milliseconds
* items - a list of the current page of tools with the following fields
  * concept_id
  * revision_id
  * provider_id
  * native_id
  * name
  * long_name

__Example__

```
curl -g -i "%CMR-ENDPOINT%/tools.json?pretty=true"

HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Content-Length: 944

{
  "hits" : 4,
  "took" : 2,
  "items" : [ {
    "concept_id" : "TL1200000005-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "tool-1",
    "name" : "someToolName1",
    "long_name" : "someToolLongName1",
  }, {
    "concept_id" : "TL1200000006-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "tool-2",
    "name" : "someToolName2",
    "long_name" : "someToolLongName2",
  } ]
}
```
##### UMM JSON
The UMM JSON response contains meta-metadata of the tool and the UMM fields.

__Example__

```
curl -g -i "%CMR-ENDPOINT%/tools.umm_json?name=NSIDC_AtlasNorth&pretty=true"
HTTP/1.1 200 OK
Content-Type: application/vnd.nasa.cmr.umm_results+json;version=1.0; charset=utf-8

{
  "hits" : 1,
  "took" : 21,
  "items" : [ {
    "meta" : {
      "native-id" : "tool-1",
      "provider-id" : "PROV1",
      "concept-type" : "tool",
      "concept-id" : "TL1200000005-PROV1",
      "revision-date" : "2020-04-01T19:52:44Z",
      "user-id" : "ECHO_SYS",
      "deleted" : false,
      "revision-id" : 1,
      "format" : "application/vnd.nasa.cmr.umm+json"
    },
    "umm" : {
      "Name": "USGS_TOOLS_LATLONG",
      "LongName": "WRS-2 Path/Row to Latitude/Longitude Converter",
      "Type":  "Downloadable Tool",
      "Version": "1.0"<
      "Description": "The USGS WRS-2 Path/Row to Latitude/Longitude Converter allows users to enter any Landsat path and row to get the nearest scene center latitude and longitude coordinates.",
      "URL": {"URLContentType": "DistributionURL"
              "Type": "DOWNLOAD SOFTWARE",
              "Description": "Access the WRS-2 Path/Row to Latitude/Longitude Converter.",
              "URLValue": "http://www.scp.byu.edu/software/slice_response/Xshape_temp.html"},
      "ToolKeywords": [{"ToolCategory": "EARTH SCIENCE SERVICES"<
                        "ToolTopic": "DATA MANAGEMENT/DATA HANDLING",
                        "ToolTerm": "DATA INTEROPERABILITY",
                        "ToolSpecificTerm": "DATA REFORMATTING"}],
      "Organizations": [{"Roles": ["SERVICE PROVIDER"],
                         "ShortName": "USGS/EROS",
                         "LongName": "US GEOLOGICAL SURVEY EARTH RESOURCE OBSERVATION AND SCIENCE (EROS) LANDSAT CUSTOMER SERVICES",
                         "URLValue": "http://www.usgs.gov"}],
      "MetadataSpecification": {"URL": "https://cdn.earthdata.nasa.gov/umm/tool/v1.0",
                                "Name": "UMM-T",
                                "Version": "1.0"}
    }
  } ]
}
```

##### <a name="retrieving-all-revisions-of-a-tool"></a> Retrieving All Revisions of a Tool

In addition to retrieving the latest revision for a tool parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON and UMM JSON response formats are supported for all revision searches. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true".

    curl "%CMR-ENDPOINT%/tools?concept_id=TL1200000005-PROV1&all_revisions=true&pretty=true"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>3</hits>
        <took>9</took>
        <references>
            <reference>
                <name>someTool1</name>
                <id>TL1200000005-PROV1</id>
                <deleted>true</deleted>
                <revision-id>3</revision-id>
            </reference>
            <reference>
                <name>someTool1</name>
                <id>TL1200000005-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/TL1200000005-PROV1/2</location>
                <revision-id>2</revision-id>
            </reference>
            <reference>
                <name>someTool1</name>
                <id>TL1200000005-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/TL1200000005-PROV1/1</location>
                <revision-id>1</revision-id>
            </reference>
        </references>
    </results>
```

##### <a name="sorting-tool-results"></a> Sorting Tool Results

By default, tool results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

###### Valid Tool Sort Keys
  * `name`
  * `long_name`
  * `provider`
  * `revision_date`

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/tools?sort_key\[\]=-name"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>2</hits>
        <took>6</took>
        <references>
            <reference>
                <name>someTool2</name>
                <id>TL1200000006-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/TL1200000006-PROV1/1</location>
                <revision-id>1</revision-id>
        </reference>
        <reference>
            <name>someTool1</name>
            <id>TL1200000005-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/TL1200000005-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
    curl "%CMR-ENDPOINT%/tools?sort_key\[\]=%2Bname"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>2</hits>
        <took>6</took>
        <references>
            <reference>
                <name>someTool1</name>
                <id>TL1200000005-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/TL1200000005-PROV1/1</location>
                <revision-id>1</revision-id>
        </reference>
        <reference>
            <name>someTool2</name>
            <id>TL1200000006-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/TL1200000006-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
#### <a name="tool-access-control"></a> Tool Access Control

Access to tool is granted through the provider via the INGEST_MANAGEMENT_ACL.

#### <a name="tool-association"></a> Tool Association

A tool identified by its concept id can be associated with collections through a list of collection concept revisions.

The tool association request normally returns status code 200 with a response that consists of a list of individual tool
association responses, one for each tool association attempted to create.

Expected Response Status:
<ul>
    <li>200 OK -- if all associations succeeded</li>
    <li>207 MULTI-STATUS -- if some associations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all associations failed due to user error</li>
</ul>

Expected Response Body:

The response body will consist of a list of tool association objects
Each association object will have:
<ul>
    <li>An `associated_item` field</li>
        <ul>
            <li>The `associated_item` field value has the collection concept id and the optional revision id that is used to identify
            the collection during tool association.</li>
        </ul>
    <li>Either a `tool_association` field with the tool association concept id and revision id when the tool association succeeded OR an `errors` field with detailed error message when the tool association failed. </li>
</ul>

IMPORTANT: Tool association requires that user has update permission on INGEST_MANAGEMENT_ACL
for the collection's provider.

Here is an example of a tool association request and its response when collection C1200000005-PROV1 exists and C1200000006-PROV1 does not:

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tools/TL1200000008-PROV1/associations -d \
'[{"concept_id": "C1200000005-PROV1"},
  {"concept_id": "C1200000006-PROV1"}]'

HTTP/1.1 207 MULTI-STATUS
Content-Type: application/json; charset=UTF-8
Content-Length: 168

[
  {
    "tool_association":{
      "concept_id":"TLA1200000009-CMR",
      "revision_id":1
    },
    "associated_item":{
      "concept_id":"C1200000005-PROV1"
    },
    "status": 200
  },
  {
    "errors":[
      "Collection [C1200000006-PROV1] does not exist or is not visible."
    ],
    "associated_item":{
      "concept_id":"C1200000006-PROV1"
    },
    "status": 400
  },
  {
    "errors":[
      "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of collection [C1200000007-PROV2] to make the association."
    ],
    "associated_item":{
      "concept_id":"C1200000007-PROV2"
    },
    "status": 400
  }
]
```

#### <a name="tool-dissociation"></a> Tool Dissociation

A tool identified by its concept id can be dissociated from collections through a list of collection concept revisions similar to tool association requests.

Expected Response Status:
<ul>
    <li>200 OK -- if all dissociations succeeded</li>
    <li>207 MULTI-STATUS -- if some dissociations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all dissociations failed due to user error</li>
</ul>

IMPORTANT: Tool dissociation requires that user has update permission on INGEST_MANAGEMENT_ACL for either the collection's provider, or the service's provider.

```
curl -XDELETE -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tools/TL1200000008-PROV1/associations -d \
'[{"concept_id": "C1200000005-PROV1"},
  {"concept_id": "C1200000006-PROV1"},
  {"concept_id": "C1200000007-PROV1"}]'

HTTP/1.1 207 MULTI-STATUS
Content-Type: application/json; charset=UTF-8
Content-Length: 168

[
  {
    "tool_association":{
      "concept_id":"TLA1200000009-CMR",
      "revision_id":2
    },
    "associated_item":{
      "concept_id":"C1200000005-PROV1"
    },
    "status": 200
  },
  {
    "warnings":[
      "Tool [TL1200000008-PROV1] is not associated with collection [C1200000006-PROV1]."
    ],
    "associated_item":{
      "concept_id":"C1200000006-PROV1"
    },
    "status": 400
  },
  {
    "errors":[
      "Collection [C1200000007-PROV1] does not exist or is not visible."
    ],
    "associated_item":{
      "concept_id":"C1200000007-PROV1"
    },
    "status": 400
  },
  {
    "errors":[
      "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of collection [C1200000008-PROV2] or provider of service/tool to delete the association."
    ],
    "associated_item":{
      "concept_id":"C1200000008-PROV2"
    },
    "status": 400
  }
]
```

### <a name="subscription"></a> Subscription

A subscription allows a user to be notified when specific collections/granules are created, updated, or deleted. The collections/granules specified can be filtered by query conditions.

There are two kinds of subscriptions: Batch Notification and Near-Real-Time Notification

<ul>
    <li>Batch Notification subscription notification processing is executed periodically, to see if there are any collections/granules that are created/updated since the last time the subscription has been processed and will notify the subscription user with any matches. Notification of updates is via the email address associated with the SubscriberId's EarthData Login (URS). </li>
    <ul>
        <li>There are two types of batch process subscriptions (identified by the "Type" field of the subscription):</li>
        <ul>
            <li>collection subscription for users to be notified when collections are created/updated, or </li>
            <li>granule subscription for users to be notified when granules are created/update</li>
        </ul>
    </ul>
    <li>Near-Real-Time (NRT) Notification subscriptions are processed on ingest and are only for granules. When a user subscribes, notifications are sent out via the provided notification endpoint, such as an AWS SQS messaging queue.
</ul>

#### <a name="searching-for-subscriptions"></a> Searching for Subscriptions

Subscriptions can be searched for by sending a request to `%CMR-ENDPOINT%/subscriptions`. XML reference, JSON, and UMM JSON response formats are supported for subscriptions search.

Subscription search results are paged. See [Paging Details](#paging-details) for more information on how to page through subscription search results.

##### <a name="subscription-search-params"></a> Subscription Search Parameters

The following parameters are supported when searching for subscriptions.

##### Standard Parameters
* page_size
* page_num
* pretty

##### Subscription Matching Parameters

These parameters will match fields within a subscription. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are ORed together.


  * name
    * options: pattern, ignore_case
  * provider
    * options: pattern, ignore_case
  * native_id
    * options: pattern, ignore_case
  * concept_id
  * subscriber_id
  * collection_concept_id
  * type
##### <a name="subscription-search-response"></a> Subscription Search Response

##### XML Reference
The XML reference response format is used for returning references to search results. It consists of the following fields:

|   Field    |                    Description                     |
| ---------- | -------------------------------------------------- |
| hits       | the number of results matching the search query    |
| took       | time in milliseconds it took to perform the search |
| references | identifying information about each search result   |

The `references` field may contain multiple `reference` entries, each consisting of the following fields:

|    Field    |                                                   Description                                                   |
| ----------- | --------------------------------------------------------------------------------------------------------------- |
| name        | the value of the Name field in subscription metadata.                                                                |
| id          | the CMR identifier for the result                                                                               |
| location    | the URL at which the full metadata for the result can be retrieved                                              |
| revision-id | the internal CMR version number for the result                                                                  |

__Example__

```
curl -i "%CMR-ENDPOINT%/subscriptions?name=someSub1&pretty=true"

HTTP/1.1 200 OK
Content-Type: application/xml; charset=UTF-8
Content-Length: 393

<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>5</took>
    <references>
        <reference>
            <name>someSub1</name>
            <id>SUB1200000005-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/SUB1200000005-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
##### JSON
The JSON response includes the following fields.

* hits - How many total variables were found.
* took - How long the search took in milliseconds
* items - a list of the current page of subscriptions with the following fields
  * concept_id
  * revision_id
  * provider_id
  * native_id
  * type
  * name
  * subscriber_id
  * collection_concept_id

__Example__

```
curl -g -i "%CMR-ENDPOINT%/subscriptions.json?pretty=true"

HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Content-Length: 944

{
  "hits" : 4,
  "took" : 2,
  "items" : [ {
    "concept_id" : "SUB1200000005-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "subscription-1",
    "type" : "granule",
    "name" : "someSub1",
    "subscriber-id" : "someSubId1",
    "collection-concept-id" : "C1200000001-PROV1"
  }, {
    "concept_id" : "SUB1200000006-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "subscription-2",
    "type" : "collection",
    "name" : "someSub2",
    "subscriber-id" : "someSubId2",
  } ]
}
```
##### UMM JSON
The UMM JSON response contains meta-metadata of the subscription and the UMM fields.

__Example__

```
curl -g -i "%CMR-ENDPOINT%/subscriptions.umm_json?name=NSIDC_AtlasNorth&pretty=true"
HTTP/1.1 200 OK
Content-Type: application/vnd.nasa.cmr.umm_results+json;version=1.0; charset=utf-8

{
  "hits" : 1,
  "took" : 21,
  "items" : [ {
    "meta" : {
      "native-id" : "subscription-1",
      "provider-id" : "PROV1",
      "concept-type" : "subscription",
      "concept-id" : "SUB1200000005-PROV1",
      "creation-date" : "2020-04-01T19:52:44Z",
      "revision-date" : "2020-04-01T19:52:44Z",
      "user-id" : "ECHO_SYS",
      "deleted" : false,
      "revision-id" : 1,
      "format" : "application/vnd.nasa.cmr.umm+json"
    },
    "umm" : {
      "Name" : "NSIDC_AtlasNorth",
      "SubscriberId" : "someSubId1",
      "EmailAddress" : "sxu@gmail.com",
      "CollectionConceptId" : "C1200000001-PROV1",
      "Query" : "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78"
    }
  } ]
}
```

##### <a name="retrieving-all-revisions-of-a-subscription"></a> Retrieving All Revisions of a Subscription

In addition to retrieving the latest revision for a subscription parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON and UMM JSON response formats are supported for all revision searches. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true".

    curl "%CMR-ENDPOINT%/subscriptions?concept_id=SUB1200000005-PROV1&all_revisions=true&pretty=true"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>3</hits>
        <took>9</took>
        <references>
            <reference>
                <name>someSub1</name>
                <id>SUB1200000005-PROV1</id>
                <deleted>true</deleted>
                <revision-id>3</revision-id>
            </reference>
            <reference>
                <name>someSub1</name>
                <id>SUB1200000005-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SUB1200000005-PROV1/2</location>
                <revision-id>2</revision-id>
            </reference>
            <reference>
                <name>someSub1</name>
                <id>SUB1200000005-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SUB1200000005-PROV1/1</location>
                <revision-id>1</revision-id>
            </reference>
        </references>
    </results>
```

##### <a name="sorting-subscription-results"></a> Sorting Subscription Results

By default, subscription results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

###### Valid Subscription Sort Keys
  * `name`
  * `provider`
  * `collection_concept_id`

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/subscriptions?sort_key\[\]=-name"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>2</hits>
        <took>6</took>
        <references>
            <reference>
                <name>someSub2</name>
                <id>SUB1200000006-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SUB1200000006-PROV1/1</location>
                <revision-id>1</revision-id>
        </reference>
        <reference>
            <name>someSub1</name>
            <id>SUB1200000005-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/SUB1200000005-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
    curl "%CMR-ENDPOINT%/subscriptions?sort_key\[\]=%2Bname"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>2</hits>
        <took>6</took>
        <references>
            <reference>
                <name>someSub1</name>
                <id>SUB1200000005-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SUB1200000005-PROV1/1</location>
                <revision-id>1</revision-id>
        </reference>
        <reference>
            <name>someSub2</name>
            <id>SUB1200000006-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/SUB1200000006-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
#### <a name="subscription-access-control"></a> Subscription Access Control

Search permission for subscription is granted through the provider via the SUBSCRIPTION_MANAGEMENT ACL. In order to be able to search for a subscription for a given provider, read permission has to be granted to the user through SUBSCRIPTION_MANAGEMENT ACL for the provider.