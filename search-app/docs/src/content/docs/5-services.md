---
title: Service
description: Provides information on the definition and use of services.
---

## <a name="service"></a> Service

A service enables data to be accessed via a universal resource locator, and has options to enable a variety of transformations to be performed on the data, e.g. spatial, temporal, variable subsetting, reprojection or reformatting. Service metadata is in JSON format and conforms to [UMM-S Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/unified-metadata-model/browse/service).

### <a name="searching-for-services"></a> Searching for Services

Services can be searched for by sending a request to `%CMR-ENDPOINT%/services`. XML reference, JSON, and UMM JSON response formats are supported for services search.

Service search results are paged. See [Paging Details](#paging-details) for more information on how to page through service search results.

#### <a name="service-search-params"></a> Service Search Parameters

The following parameters are supported when searching for services.

#### Standard Parameters
* page_size
* page_num
* pretty

#### Service Matching Parameters

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

#### <a name="service-search-response"></a> Service Search Response

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
#### JSON
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
#### UMM JSON
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

#### <a name="retrieving-all-revisions-of-a-service"></a> Retrieving All Revisions of a Service

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

#### <a name="sorting-service-results"></a> Sorting Service Results

By default, service results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Service Sort Keys
  * `name`
  * `long_name`
  * `provider`
  * `revision_date`

Examples of sorting by provider id in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/services?sort_key\[\]=-provider"
    curl "%CMR-ENDPOINT%/services?sort_key\[\]=%2Bprovider"

### <a name="service-access-control"></a> Service Access Control

Access to service and service association is granted through the provider via the INGEST_MANAGEMENT_ACL. Associating and dissociating collections with a service is considered an update.

### <a name="service-association"></a> Service Association

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

### <a name="service-dissociation"></a> Service Dissociation

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