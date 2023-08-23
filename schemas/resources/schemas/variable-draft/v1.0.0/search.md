### <a name="variable-draft"></a> Variable Draft

Variable Drafts are draft records that inform users about the variables that are available in a collection when working with data files. Variable metadata is stored in the JSON format [UMM-Service Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/unified-metadata-model/browse/variable).

#### <a name="searching-for-variable-drafts"></a> Searching for Variable Drafts

Variable Drafts can be searched for by sending a request to `%CMR-ENDPOINT%/variable-drafts`. XML reference, JSON and UMM JSON response formats are supported for Variable Draft searches.

Variable Draft search results are paged. See [Paging Details](#paging-details) for more information on how to page through Variable Draft search results.

##### <a name="variable-draft-search-params"></a> Variable Draft Search Parameters

The following parameters are supported when searching for Variable Drafts.

##### Standard Parameters

* page\_size
* page\_num
* pretty

##### Variable Draft Matching Parameters

These parameters will match fields within a Variable Draft. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are bitwise `OR`ed together.

* name - options: pattern, ignore\_case
* provider - options: pattern, ignore\_case
* native\_id - options: pattern, ignore\_case
* concept\_id

```
    curl "%CMR-ENDPOINT%/variable-drafts?concept_id=VD1200000000-PROV1"
```

##### <a name="variable-draft-search-response"></a> Variable Draft Search Response

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
        "%CMR-ENDPOINT%/variable-drafts.xml?name=variable-name"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>1</hits>
        <took>13</took>
        <references>
            <reference>
                <name>variable-name</name>
                <id>VD1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/VD1200000000-PROV1/4</location>
                <revision-id>4</revision-id>
            </reference>
        </references>
    </results>
```

##### JSON

The JSON response includes the following fields.

* hits - How many total records were found.
* took - How long the search took in milliseconds
* items - a list of the current page of records with the following fields
  * concept\_id
  * revision\_id
  * provider\_id
  * native\_id
  * name

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/variable-drafts.json?name=variable-name"
```

__Sample response__

```
    {
        "hits": 1,
        "took": 10,
        "items": [
            {
                "concept_id": "VD1200000000-PROV1",
                "revision\_id": 4,
                "provider\_id": "PROV-1",
                "native\_id": "sampleNative-Id",
                "name": "variable-name"
            }
        ]
    }
```

##### UMM JSON

The UMM JSON response contains meta-metadata of the Variable Draft, the UMM fields and the associations field if applicable. [To search over specific versions of UMM](#umm-json). 

__Example__

```
    curl -H "pretty=true" \
        "%CMR-ENDPOINT%/variable-drafts.umm_json?name=variable-name"
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
                    "concept-id": "VD1200000000-PROV1",
                    "revision-date": "2022-10-26T19:17:27.021Z",
                    "concept-type": "variable-draft"
                },
                "umm": {
                    "VariableType" : "SCIENCE_VARIABLE",
                    "DataType" : "float32",
                    "Offset" : 0,
                    "Scale" : 1,
                    "Sets" : [ {
                        "Name" : "data",
                        "Type" : "data",
                        "Size" : 4,
                        "Index" : 4
                    }],
                    "Dimensions" : [ {
                        "Name" : "latitude",
                        "Size" : 2166,
                        "Type" : "LATITUDE_DIMENSION"
                     }, {
                        "Name" : "longitude",
                        "Size" : 4061,
                        "Type" : "LONGITUDE_DIMENSION"
                    }],
                    "Definition" : "2D Amplitude of IFG",
                    "Name" : "science/grids/data/amplitude",
                    "AcquisitionSourceName" : "SENTINEL-1 C-SAR",
                    "ValidRanges" : [{"Min" : 0}],
                    "Units" : "watt",
                    "LongName" : "Amplitude"
                }
            }
        ]
    }
```

#### <a name="sorting-variable-draft-results"></a> Sorting Variable Draft Results

By default, Variable Draft results are sorted by name, then by provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Variable Sort Keys

* name
* provider
* revision_date

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

```
    curl "%CMR-ENDPOINT%/variable-drafts?sort_key\[\]=-name"
    curl "%CMR-ENDPOINT%/variable-drafts?sort_key\[\]=%2Bname"
```
