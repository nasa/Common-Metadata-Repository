### <a name="data-quality-summary"></a> Data Quality Summary

Data Quality Summaries inform users about the data quality of 1 or more collection(s) or dataset(s) and their granules. Data Quality Summary metadata is stored in the JSON format [UMM-Data-Quality-Summary Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas/browse/data-quality-summary).

#### <a name="searching-for-data-quality-summaries"></a> Searching for Data Quality Summaries

Data Quality Summaries can be searched for by sending a request to `%CMR-ENDPOINT%/data-quality-summaries`. XML reference, JSON and UMM JSON response formats are supported for Data Quality Summary search.

Data Quality Summary search results are paged. See [Paging Details](#paging-details) for more information on how to page through Data Quality Summary search results.

##### <a name="data-quality-summary-search-params"></a> Data Quality Summary Search Parameters

The following parameters are supported when searching for Data Quality Summaries.

##### Standard Parameters

* page\_size
* page\_num
* pretty

##### Data Quality Summary Matching Parameters

These parameters will match fields within a Data Quality Summary. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are bitwise `OR`ed together.

* name - options: pattern, ignore\_case
* provider - options: pattern, ignore\_case
* native\_id - options: pattern, ignore\_case
* concept\_id
* id

```
    curl "%CMR-ENDPOINT%/data-quality-summaries?concept_id=DQ1200000000-PROV1"
```

##### <a name="data-quality-summary-search-response"></a> Data Quality Summary Search Response

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
| name        | the value of the Name field in Data Quality Summary metadata.      |
| id          | the CMR identifier for the result                                  |
| location    | the URL at which the full metadata for the result can be retrieved |
| revision-id | the internal CMR version number for the result                     |

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/data-quality-summaries.xml?name=data-quality-summary1"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>1</hits>
        <took>13</took>
        <references>
            <reference>
                <name>data-quality-summary-name-v1</name>
                <id>DQ1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/DQ1200000000-PROV1/4</location>
                <revision-id>4</revision-id>
            </reference>
        </references>
    </results>
```

##### JSON

The JSON response includes the following fields.

* hits - How many total Data Quality Summaries were found.
* took - How long the search took in milliseconds
* items - a list of the current page of Data Quality Summaries with the following fields
  * concept\_id
  * revision\_id
  * provider\_id
  * native\_id
  * name

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/data-quality-summaries.json?name="data-quality-summary-name"
```

__Sample response__

```
    {
        "hits": 1,
        "took": 10,
        "items": [
            {
                "concept_id": "DQ1200000000-PROV1",
                "revision\_id": 4,
                "provider\_id": "PROV-1",
                "native\_id": "sampleNative-Id",
                "name": "data-quality-summary-name"
            }
        ]
    }
```

##### UMM JSON

The UMM JSON response contains meta-metadata of the Data Quality Summary, the UMM fields and the associations field if applicable. The associations field only applies when there are collections associated with the Data Quality Summary and will list the collections that are associated with the Data Quality Summary.

__Example__

```
    curl -H "pretty=true" \
        "%CMR-ENDPOINT%/data-quality-summaries.umm_json?name=data-quality-summary1234"
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
                    "concept-id": "DQS1200000000-PROV1",
                    "revision-date": "2022-10-26T19:17:27.021Z",
                    "concept-type": "data-quality-summary"
                },
                "umm": {
                    "Id": "8EA5CA1F-E339-8065-26D7-53B64074D7CC",
                    "Name": "CER-BDS_Terra",
                    "Summary": "Summary",
                    "MetadataSpecification": {
                        "Name": "Data Quality Summary",
                        "Version": "1.0.0",
                        "URL": "https://cdn.earthdata.nasa.gov/generics/data-quality-summary/v1.0.0"
                    }
                }
            }
        ]
    }
```

#### <a name="retrieving-all-revisions-of-a-data-quality-summary"></a> Retrieving All Revisions of a Data Quality Summary

In addition to retrieving the latest revision for a Data Quality Summary parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON, and UMM JSON response formats are supported for all revision searches merely change to 'umm_json' and 'json' repecitvely. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true". Data Quality Summaries with only one revision will of course, return only one result.

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/data-quality-summaries.xml?concept_id=SO1200000000-PROV1&all_revisions=true"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>4</hits>
        <took>80</took>
        <references>
            <reference>
                <name>data-quality-summary-name-v1</name>
                <id>DQ1200000000-PROV1</id>
                <deleted>true</deleted>
                <revision-id>1</revision-id>
            </reference>
            <reference>
                <name>data-quality-summary-name-v2</name>
                <id>DQ1200000000-PROV1V</id>
                <location>%CMR-ENDPOINT%/concepts/DQ1200000000-PROV1/3</location>
                <revision-id>2</revision-id>
            </reference>
            <reference>
                <name>data-quality-summary-name-v3</name>
                <id>SO1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/DQ1200000000-PROV1/4</location>
                <revision-id>3</revision-id>
            </reference>
        </references>
    </results>
```

#### <a name="sorting-data-quality-summary-results"></a> Sorting Data Quality Summary Results

By default, Data Quality Summary results are sorted by name, then by provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Data Quality Summary Sort Keys

* name
* provider
* revision_date

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

```
    curl "%CMR-ENDPOINT%/data-quality-summaries?sort_key\[\]=-name"
    curl "%CMR-ENDPOINT%/data-quality-summaries?sort_key\[\]=%2Bname"
```
