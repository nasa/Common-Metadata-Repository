### <a name="service-option"></a> Service Option

Service Options describe options that are available within the ordering service and are linked to service entries defined by a provider. Service Option metadata is stored in the JSON format[UMM-Service-option Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas/browse/service-option).

#### <a name="searching-for-service-options"></a> Searching for Service Options

Service Options can be searched for by sending a request to `%CMR-ENDPOINT%/service-options`. XML reference, JSON and UMM JSON response formats are supported for Service Options search.

Service option search results are paged. See [Paging Details](#paging-details) for more information on how to page through service-option search results.

##### <a name="service-option-search-params"></a> Service Option Search Parameters

The following parameters are supported when searching for Service Options.

##### Standard Parameters

* page\_size
* page\_num
* pretty

##### Service Option Matching Parameters

These parameters will match fields within a Service Option. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are bitwise ORed together.

* name - options: pattern, ignore\_case
* provider - options: pattern, ignore\_case
* native\_id - options: pattern, ignore\_case
* concept\_id
* id

```
    curl "%CMR-ENDPOINT%/service-options?concept_id=SO1200000000-PROV1"
```

##### <a name="service-option-search-response"></a> Service Option Search Response

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
| name        | the value of the Name field in the Service Option metadata.        |
| id          | the CMR identifier for the result                                  |
| location    | the URL at which the full metadata for the result can be retrieved |
| revision-id | the internal CMR version number for the result                     |

__Example__

```
    curl -H "Cmr-Pretty: true" "%CMR-ENDPOINT%/service-options.xml?name=Serviceoption1"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>1</hits>
        <took>13</took>
        <references>
            <reference>
                <name>Serviceoption1</name>
                <id>SO1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SO1200000000-PROV1/4</location>
                <revision-id>4</revision-id>
            </reference>
        </references>
    </results>
```

##### JSON

The JSON response includes the following fields.

* hits - How many total Service Options were found.
* took - How long the search took in milliseconds
* items - a list of the current page of Service Options with the following fields
    * concept\_id
    * revision\_id
    * provider\_id
    * native\_id
    * name

__Example__

```
    curl -H "Cmr-Pretty: true" "%CMR-ENDPOINT%/service-options.json"
```

__Sample response__

```
    {
        "hits": 1,
        "took": 10,
        "items": [
            {
                "concept_id": "SO1200000000-PROV1",
                "revision_id": 4,
                "provider_id": "PROV-1",
                "native_id": "sampleNative-Id",
                "name": "Serviceoption-name-v1"
            }
        ]
    }
```

##### UMM JSON

The UMM JSON response contains meta-metadata of the Service Option, the UMM fields and the associations field if applicable.

__Example__

```
    curl -H "Cmr-Pretty: true" "%CMR-ENDPOINT%/service-options.umm_json?name=Serviceoption1234"
```

__Sample response__

```
    {
        "hits": 2,
        "took": 17,
        "items": [
            {
                "meta": {
                    "revision-id": 1,
                    "deleted": false,
                    "provider-id": "PROV1",
                    "user-id": "exampleuser",
                    "native-id": "sampleNative-Id",
                    "concept-id": "SO1200000000-PROV1",
                    "revision-date": "2022-09-19T15:05:30.755Z",
                    "concept-type": "service-option"
                },
                "umm": {
                    "Id": "1B41335E-82DD-8AAB-B8A9-546CC6DE6CBD",
                    "Name": "RangeSliderTest",
                    "Description": "Testing range slider",
                    "Form": "<form></form>",
                    "MetadataSpecification": {
                        "Name": "Service Option",
                        "Version": "1.0.0",
                        "URL": "https://cdn.earthdata.nasa.gov/generics/service-option/v1.0.0"
                    }
                }
            }
        ]
    }
```

#### <a name="retrieving-all-revisions-of-a-service-option"></a> Retrieving All Revisions of a Service Option

In addition to retrieving the latest revision for a Service Option parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON, and UMM JSON response formats are supported for all revision searches merely change to 'umm_json' and 'json' repecitvely. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true". Service Option with only 1 revision will of course, return only one result.

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/service-options.xml?concept_id=SO1200000000-PROV1&all_revisions=true"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>4</hits>
        <took>80</took>
        <references>
            <reference>
                <name>Serviceoption-name-v1</name>
                <id>SO1200000000-PROV1</id>
                <deleted>true</deleted>
                <revision-id>1</revision-id>
            </reference>
            <reference>
                <name>Serviceoption-name-v2</name>
                <id>SO1200000000-PROV1V</id>
                <location>%CMR-ENDPOINT%/concepts/SO1200000000-PROV1/3</location>
                <revision-id>2</revision-id>
            </reference>
            <reference>
                <name>Serviceoption-name-v3</name>
                <id>SO1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SO1200000000-PROV1/4</location>
                <revision-id>3</revision-id>
            </reference>
        </references>
    </results>
```

#### <a name="sorting-service-option-results"></a> Sorting Service Option Results

By default, Service Option results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Service Option Sort Keys

* name
* provider
* revision_date

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

```
    curl "%CMR-ENDPOINT%/service-options?sort_key\[\]=-name"
    curl "%CMR-ENDPOINT%/service-options?sort_key\[\]=%2Bname"
```
