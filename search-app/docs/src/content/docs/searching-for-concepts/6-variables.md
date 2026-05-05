---
title: Variable
description: Provides information on the definition and use of variables.
---

## <a name="variable"></a> Variable

Variables are measurement variables belonging to collections/granules that are processable by services. Variable metadata is stored in the JSON format and conforms to [UMM-Var Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/unified-metadata-model/browse/variable) schema.

### <a name="searching-for-variables"></a> Searching for Variables

Variables can be searched for by sending a request to `%CMR-ENDPOINT%/variables`. XML reference, JSON and UMM JSON response formats are supported for variables search.

Variable search results are paged. See [Paging Details](#paging-details) for more information on how to page through variable search results.

#### <a name="variable-search-params"></a> Variable Search Parameters

The following parameters are supported when searching for variables.

#### Standard Parameters
* page_size
* page_num
* pretty

#### Variable Matching Parameters

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

#### <a name="variable-search-response"></a> Variable Search Response

#### XML Reference
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
#### JSON
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

#### UMM JSON
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

#### <a name="retrieving-all-revisions-of-a-variable"></a> Retrieving All Revisions of a Variable

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

#### <a name="sorting-variable-results"></a> Sorting Variable Results

By default, variable results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Variable Sort Keys
  * `name`
  * `long_name`
  * `provider`
  * `revision_date`

Examples of sorting by long_name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/variables?sort_key\[\]=-long_name"
    curl "%CMR-ENDPOINT%/variables?sort_key\[\]=%2Blong_name"

### <a name="variable-access-control"></a> Variable Access Control

Access to variable and variable association is granted through the provider via the INGEST_MANAGEMENT_ACL. Users can only create, update, or delete a variable if they are granted the appropriate permission. Associating and dissociating collections with a variable is considered an update.