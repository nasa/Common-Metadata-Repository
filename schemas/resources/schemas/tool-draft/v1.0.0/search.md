### <a name="tool-draft"></a> Tool Draft

Tool Drafts are draft records that inform users about the tools that are available to a collection when working with data files. Tool metadata is stored in the JSON format [UMM-Service Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/unified-metadata-model/browse/tool).

#### <a name="searching-for-tool-drafts"></a> Searching for Tool Drafts

Tool Drafts can be searched for by sending a request to `%CMR-ENDPOINT%/tool-drafts`. XML reference, JSON and UMM JSON response formats are supported for Tool Draft searches.

Tool Draft search results are paged. See [Paging Details](#paging-details) for more information on how to page through Tool Draft search results.

##### <a name="tool-draft-search-params"></a> Tool Draft Search Parameters

The following parameters are supported when searching for Tool Drafts.

##### Standard Parameters

* page\_size
* page\_num
* pretty

##### Tool Draft Matching Parameters

These parameters will match fields within a Tool Draft. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are bitwise `OR`ed together.

* name - options: pattern, ignore\_case
* provider - options: pattern, ignore\_case
* native\_id - options: pattern, ignore\_case
* concept\_id

```
    curl "%CMR-ENDPOINT%/tool-drafts?concept_id=TD1200000000-PROV1"
```

##### <a name="tool-draft-search-response"></a> Tool Draft Search Response

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
        "%CMR-ENDPOINT%/tool-drafts.xml?name=tool-name"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>1</hits>
        <took>13</took>
        <references>
            <reference>
                <name>tool-name</name>
                <id>TD1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/TD1200000000-PROV1/4</location>
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
        "%CMR-ENDPOINT%/tool-drafts.json?name=tool-name"
```

__Sample response__

```
    {
        "hits": 1,
        "took": 10,
        "items": [
            {
                "concept_id": "TD1200000000-PROV1",
                "revision\_id": 4,
                "provider\_id": "PROV-1",
                "native\_id": "sampleNative-Id",
                "name": "tool-name"
            }
        ]
    }
```

##### UMM JSON

The UMM JSON response contains meta-metadata of the Tool Draft, the UMM fields and the associations field if applicable. [To search over specific versions of UMM](#umm-json). 

__Example__

```
    curl -H "pretty=true" \
        "%CMR-ENDPOINT%/tool-drafts.umm_json?name=tool-name"
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
                    "concept-id": "TD1200000000-PROV1",
                    "revision-date": "2022-10-26T19:17:27.021Z",
                    "concept-type": "tool-draft"
                },
                "umm": {
                    "Type": "Web User Interface"
                    "Description": "SOTO ... research.",
                    "Version": "2",
                    "Name": "SOTO",
                    "LongName": "State Of The Ocean",
                    "PotentialAction": {
                        "Type": "SearchAction",
                        "Target": {
                            "Type": "EntryPoint",
                            "ResponseContentType": ["text/html"],
                            "UrlTemplate": "https://podaac-tools.jpl.nasa.gov/soto/#b=BlueMarble_ShadedRelief_Bathymetry&l={+layers}&ve={+bbox}&d={+date}",
                            "Description": "SOTO is a suite of tools ... related research.",
                            "HttpMethod": ["GET"]
                        },
                        "QueryInput": [{
                            "ValueName": "bbox",
                            "Description": "A spatial bounding box ...space character.",
                            "ValueRequired": false,
                            "ValueType": "https://schema.org/box"
                        }]
                    },
                    "MetadataSpecification": {
                        "URL": "https://cdn.earthdata.nasa.gov/umm/tool/v1.2.0",
                        "Name": "UMM-T",
                        "Version": "1.2.0"
                    }
                }
            }
        ]
    }
```

#### <a name="sorting-tool-draft-results"></a> Sorting Tool Draft Results

By default, Tool Draft results are sorted by name, then by provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Order Option Sort Keys

* name
* provider
* revision_date

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

```
    curl "%CMR-ENDPOINT%/tool-drafts?sort_key\[\]=-name"
    curl "%CMR-ENDPOINT%/tool-drafts?sort_key\[\]=%2Bname"
```
