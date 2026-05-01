---
title: Tool
description: Provides information on the definition and use of a tool.
---

## <a name="tool"></a> Tool

UMM-T provides metadata to support the User Interface/User Experience (UI/UX)-driven approach to Tools. Specifically, when a user wants to know the tools available for a specific collection and makes selections via the Earthdata Search UI, options are presented showing what operating systems or languages are supported.
The UMM-T model in MMT enables the population of the tool options, either web user interface or downloadable tool options are surfaced in the UI to support these selections. Each UMM-T record contains metadata for tools and other information such as contact groups or contact persons, tool keywords, and supported inputs and outputs.

### <a name="searching-for-tools"></a> Searching for Tools

Tools can be searched for by sending a request to `%CMR-ENDPOINT%/tools`. XML reference, JSON, and UMM JSON response formats are supported for tools search.

Tool search results are paged. See [Paging Details](#paging-details) for more information on how to page through tool search results.

#### <a name="tool-search-params"></a> Tool Search Parameters

The following parameters are supported when searching for tools.

#### Standard Parameters
* page_size
* page_num
* pretty

#### Tool Matching Parameters

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

#### <a name="tool-search-response"></a> Tool Search Response

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
#### JSON
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
#### UMM JSON
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

#### <a name="retrieving-all-revisions-of-a-tool"></a> Retrieving All Revisions of a Tool

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

#### <a name="sorting-tool-results"></a> Sorting Tool Results

By default, tool results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Tool Sort Keys
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
### <a name="tool-access-control"></a> Tool Access Control

Access to tool is granted through the provider via the INGEST_MANAGEMENT_ACL.

### <a name="tool-association"></a> Tool Association

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

### <a name="tool-dissociation"></a> Tool Dissociation

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