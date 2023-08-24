### <a name="collection-draft"></a> Collection Draft

Collection Drafts are draft records that describe a data set. Collection metadata is stored in the JSON format [UMM-Collection Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/unified-metadata-model/browse/collection).

#### <a name="searching-for-collection-drafts"></a> Searching for Collection Drafts

Collection Drafts can be searched for by sending a request to `%CMR-ENDPOINT%/collection-drafts`. XML reference, JSON and UMM JSON response formats are supported for Collection Draft searches.

Collection Draft search results are paged. See [Paging Details](#paging-details) for more information on how to page through Collection Draft search results.

##### <a name="collection-draft-search-params"></a> Collection Draft Search Parameters

The following parameters are supported when searching for Collection Drafts.

##### Standard Parameters

* page\_size
* page\_num
* pretty

##### Collection Draft Matching Parameters

These parameters will match fields within an Collection Draft. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are bitwise `OR`ed together.

* name - options: pattern, ignore\_case
* provider - options: pattern, ignore\_case
* native\_id - options: pattern, ignore\_case
* concept\_id

```
    curl "%CMR-ENDPOINT%/collection-drafts?concept_id=CD1200000000-PROV1"
```

##### <a name="collection-draft-search-response"></a> Collection Draft Search Response

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
| name        | the value of the Name field in Collection Draft metadata.      |
| id          | the CMR identifier for the result                                  |
| location    | the URL at which the full metadata for the result can be retrieved |
| revision-id | the internal CMR version number for the result                     |

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/collection-drafts.xml?name=collection-name"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>1</hits>
        <took>13</took>
        <references>
            <reference>
                <name>collection-name</name>
                <id>CD1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/CD1200000000-PROV1/4</location>
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
        "%CMR-ENDPOINT%/collection-drafts.json?name=collection-name"
```

__Sample response__

```
    {
        "hits": 1,
        "took": 10,
        "items": [
            {
                "concept_id": "CD1200000000-PROV1",
                "revision\_id": 4,
                "provider\_id": "PROV-1",
                "native\_id": "sampleNative-Id",
                "name": "collection-name"
            }
        ]
    }
```

##### UMM JSON

The UMM JSON response contains meta-metadata of the Collection Draft, the UMM fields and the associations field if applicable. [To search over specific versions of UMM](#umm-json). 

__Example__

```
    curl -H "pretty=true" \
        "%CMR-ENDPOINT%/collection-drafts.umm_json?name=collection-name"
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
                    "concept-id": "CD1200000000-PROV1",
                    "revision-date": "2022-10-26T19:17:27.021Z",
                    "concept-type": "collection-draft"
                },
                "umm": {
                    "ShortName": "Mapping Short Name 1.17.0 CMR-8220",
                    "Version": "1",
                    "EntryTitle": "Mapping Example for UMM-C 1.17.0 CMR-8220",
                    "DOI": {
                        "DOI": "10.1234/DOIID",
                        "Authority": "https://doi.org/"
                    },
                    ...
                    "MetadataSpecification": {
                        "URL": "https://cdn.earthdata.nasa.gov/umm/collection/v1.17.0",
                        "Name": "UMM-C",
                        "Version": "1.17.0"
                    }
                }
            }
        ]
    }
```

#### <a name="sorting-collection-draft-results"></a> Sorting Collection Draft Results

By default, Collection Draft results are sorted by name, then by provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Collection Draft Sort Keys

* name
* provider
* revision_date

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

```
    curl "%CMR-ENDPOINT%/collection-drafts?sort_key\[\]=-name"
    curl "%CMR-ENDPOINT%/collection-drafts?sort_key\[\]=%2Bname"
```
