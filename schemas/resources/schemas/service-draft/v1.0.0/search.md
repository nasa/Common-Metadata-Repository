### <a name="service-draft"></a> Service Draft

Service Drafts are draft records that inform users about the services that are available to a collection when ordering data files. Service metadata is stored in the JSON format [UMM-Service Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/unified-metadata-model/browse/service).

#### <a name="searching-for-service-drafts"></a> Searching for Service Drafts

Service Drafts can be searched for by sending a request to `%CMR-ENDPOINT%/service-drafts`. XML reference, JSON and UMM JSON response formats are supported for Service Draft searches.

Service Draft search results are paged. See [Paging Details](#paging-details) for more information on how to page through Service Draft search results.

##### <a name="service-draft-search-params"></a> Service Draft Search Parameters

The following parameters are supported when searching for Service Drafts.

##### Standard Parameters

* page\_size
* page\_num
* pretty

##### Service Draft Matching Parameters

These parameters will match fields within a Service Draft. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are bitwise `OR`ed together.

* name - options: pattern, ignore\_case
* provider - options: pattern, ignore\_case
* native\_id - options: pattern, ignore\_case
* concept\_id

```
    curl "%CMR-ENDPOINT%/service-drafts?concept_id=SD1200000000-PROV1"
```

##### <a name="service-draft-search-response"></a> Service Draft Search Response

##### XML Reference

The XML reference response format is used for returning references to search results. It consists of the following fields:

| Field      | Description                                        |
| ---------- | -------------------------------------------------- |
| hits       | the number of results matching the search query    |
| took       | time in milliseconds it took to perform the search |
| references | identifying information about each search result   |

The `references` field may contain multiple `reference` entries, each consisting of the following fields:

| Field       | Description                                                        |
| ----------- | ------------------------------------------------------------------ |
| name        | the value of the Name field in Service Draft metadata.      |
| id          | the CMR identifier for the result                                  |
| location    | the URL at which the full metadata for the result can be retrieved |
| revision-id | the internal CMR version number for the result                     |

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/service-drafts.xml?name=service-name"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>1</hits>
        <took>13</took>
        <references>
            <reference>
                <name>service-name</name>
                <id>SD1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SD1200000000-PROV1/4</location>
                <revision-id>4</revision-id>
            </reference>
        </references>
    </results>
```

##### JSON

The JSON response includes the following fields.

* hits - How many total Order Options were found.
* took - How long the search took in milliseconds
* items - a list of the current page of Order Options with the following fields
  * concept\_id
  * revision\_id
  * provider\_id
  * native\_id
  * name

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/service-drafts.json?name=service-name"
```

__Sample response__

```
    {
        "hits": 1,
        "took": 10,
        "items": [
            {
                "concept_id": "SD1200000000-PROV1",
                "revision\_id": 4,
                "provider\_id": "PROV-1",
                "native\_id": "sampleNative-Id",
                "name": "service-name"
            }
        ]
    }
```

##### UMM JSON

The UMM JSON response contains meta-metadata of the Service Draft, the UMM fields and the associations field if applicable. [To search over specific versions of UMM](#umm-json). 

__Example__

```
    curl -H "pretty=true" \
        "%CMR-ENDPOINT%/service-drafts.umm_json?name=service-name"
```

__Sample response__

```
    {
        "hits": 1,
        "took": 17,
        "items": [
            {
                "meta": {
                    "revision-id": 1,
                    "deleted": false,
                    "provider-id": "PROV1",
                    "user-id": "exampleuser",
                    "native-id": "samplenativeid12",
                    "concept-id": "SD1200000000-PROV1",
                    "revision-date": "2022-10-26T19:17:27.021Z",
                    "concept-type": "service-draft"
                },
                "umm": {
                    "Name" : "Service-1.5.2",
                    "Aggregation": {
                        "Concatenate": {
                            "ConcatenateDefault": true
                        }
                    },
                    "SupportedInputProjections" : [ {
                        "ProjectionName" : "Geographic",
                        "ProjectionAuthority" : "4326"
                    } ],
                    "MetadataSpecification" : {
                        "URL" : "https://cdn.earthdata.nasa.gov/umm/service/v1.5.2",
                        "Name" : "UMM-S",
                        "Version" : "1.5.2"
                    },
                    "LongName" : "AIRS/Aqua L3 Daily Standard Physical Retrieval (AIRS+AMSU) 1 degree x 1 degree V006."
                }
            }
        ]
    }
```

#### <a name="sorting-service-draft-results"></a> Sorting Service Draft Results

By default, Service Draft results are sorted by name, then by provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Order Option Sort Keys

* name
* provider
* revision_date

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

```
    curl "%CMR-ENDPOINT%/service-drafts?sort_key\[\]=-name"
    curl "%CMR-ENDPOINT%/service-drafts?sort_key\[\]=%2Bname"
```
