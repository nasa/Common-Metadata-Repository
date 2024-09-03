### <a name="visualization"></a> Visualization

Visualizations inform users about different kinds of visualizations, whether they come from GIBS, GIOVANNI, WorldView, or other sources. Visualization metadata is stored in the JSON format [Visualization Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas/browse/visualization).

#### <a name="searching-for-visualizations"></a> Searching for Visualizations

Visualizations can be searched for by sending a request to `%CMR-ENDPOINT%/visualizations`. XML reference, JSON and UMM JSON response formats are supported.

Visualization search results are paged. See [Paging Details](#paging-details) for more information on how to page through search results.

##### <a name="visualization-search-params"></a> Visualization Search Parameters

The following parameters are supported when searching for Visualizations.

##### Standard Parameters

* page\_size
* page\_num
* pretty

##### Visualization Matching Parameters

These parameters will match fields within a Visualization. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are bitwise `OR`ed together.

* name - options: pattern, ignore\_case
* provider - options: pattern, ignore\_case
* native\_id - options: pattern, ignore\_case
* concept\_id
* id or identifier
* title
* visualization-type
* concept-ids

```
    curl "%CMR-ENDPOINT%/visualizations?concept_id=VIS1200000000-PROV1"
```

##### <a name="visualization-search-response"></a> Visualization Search Response

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
| name        | the value of the Name field in Visualization metadata.      |
| id          | the CMR identifier for the result                                  |
| location    | the URL at which the full metadata for the result can be retrieved |
| revision-id | the internal CMR version number for the result                     |

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/visualizations.xml?name=visualization-name-v1"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>1</hits>
        <took>13</took>
        <references>
            <reference>
                <name>visualization-name-v1</name>
                <id>VIS1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/VIS1200000000-PROV1/4</location>
                <revision-id>4</revision-id>
            </reference>
        </references>
    </results>
```

##### JSON

The JSON response includes the following fields.

* hits - How many total Visualizations were found.
* took - How long the search took in milliseconds
* items - a list of the current page of Visualizations with the following fields
  * concept\_id
  * revision\_id
  * provider\_id
  * native\_id
  * name
  * id

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/visualizations.json?name="visualization-name-v1"
```

__Sample response__

```
    {
        "hits": 1,
        "took": 10,
        "items": [
            {
                "concept_id": "VIS1200000000-PROV1",
                "revision\_id": 4,
                "provider\_id": "PROV-1",
                "native\_id": "sampleNative-Id",
                "name": "visualization-name-v1",
                "id": "visualization-id"
            }
        ]
    }
```

##### UMM JSON

The UMM JSON response contains meta-metadata of the Visualization, the UMM fields and the associations field if applicable. [To search over specific versions of UMM](#umm-json). 

__Example__

```
    curl -H "pretty=true" \
        "%CMR-ENDPOINT%/visualizations.umm_json?name=visualization-name-v1"
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
                    "concept-id": "VIS1200000000-PROV1",
                    "revision-date": "2022-10-26T19:17:27.021Z",
                    "concept-type": "visualization"
                },
                "umm": {
                    "Id": "visualization-id",
                    "Name": "visualization-name-v1",
                    "Title": "Title_1",
                    "MetadataSpecification": {
                        "Name": "Visualization",
                        "Version": "1.0.0",
                        "URL": "https://cdn.earthdata.nasa.gov/generics/visualization/1.0.0"
                    }
                }
            }
        ]
    }
```

#### <a name="retrieving-all-revisions-of-a-visualization"></a> Retrieving All Revisions of a Visualization

In addition to retrieving the latest revision for a Visualization parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON, and UMM JSON response formats are supported for all revision searches merely change to 'umm_json' and 'json' respectively. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true". Visualizations with only one revision will of course, return only one result.

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/visualizations.xml?concept_id=VIS1200000000-PROV1&all_revisions=true"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>4</hits>
        <took>80</took>
        <references>
            <reference>
                <name>visualization-name-v1</name>
                <id>VIS1200000000-PROV1</id>
                <deleted>true</deleted>
                <revision-id>1</revision-id>
            </reference>
            <reference>
                <name>visualization-name-v2</name>
                <id>VIS1200000000-PROV1V</id>
                <location>%CMR-ENDPOINT%/concepts/VIS1200000000-PROV1/3</location>
                <revision-id>2</revision-id>
            </reference>
            <reference>
                <name>visualization-name-v3</name>
                <id>VIS1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/VIS1200000000-PROV1/4</location>
                <revision-id>3</revision-id>
            </reference>
        </references>
    </results>
```

#### <a name="sorting-visualization-results"></a> Sorting Visualization Results

By default, Visualization results are sorted by name, then by provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Visualization Sort Keys

* name
* provider
* revision_date

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

```
    curl "%CMR-ENDPOINT%/visualizations?sort_key\[\]=-name"
    curl "%CMR-ENDPOINT%/visualizations?sort_key\[\]=%2Bname"
```
