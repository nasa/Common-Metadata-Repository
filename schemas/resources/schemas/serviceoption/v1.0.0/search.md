### <a name="serviceoption"></a> Service Option

Serviceoptions describe options that are available within the ordering service and are linked to service entries defined by a provider. Serviceoption metadata is stored in the JSON format[UMM-Serviceoption Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas/browse/serviceoption) schema.

#### <a name="searching-for-serviceoptions"></a> Searching for Service Options

serviceoptions can be searched for by sending a request to `%CMR-ENDPOINT%/serviceoptions`. XML reference, JSON and UMM JSON response formats are supported for serviceoptions search.

serviceoption search results are paged. See [Paging Details](#paging-details) for more information on how to page through serviceoption search results.

##### <a name="serviceoption-search-params"></a> Service Option Search Parameters

The following parameters are supported when searching for serviceoptions.

##### Standard Parameters
* page_size
* page_num
* pretty

##### Service Option Matching Parameters

These parameters will match fields within a serviceoption. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are bitwise ORed together.

* name
  * options: pattern, ignore_case
* provider
  * options: pattern, ignore_case
* native_id
  * options: pattern, ignore_case
* concept_id
* id


````
curl -g "%CMR-ENDPOINT%/serviceoptions?concept_id=SO1200000000-PROV1"
````
##### <a name="serviceoption-search-response"></a> Service Option Search Response

##### XML Reference
The XML reference response format is used for returning references to search results. It consists of the following fields:

|   Field    |                    Description                     |
| ---------- | -------------------------------------------------- |
| hits       | the number of results matching the search query    |
| took       | time in milliseconds it took to perform the search |
| references | identifying information about each search result   |

The `references` field may contain multiple `reference` entries, each consisting of the following fields:

|    Field    |                                                   Description                                                   |
| ----------- | --------------------------------------------------------------------------------------------------------------- |
| name        | the value of the Name field in serviceoption metadata.                                                               |
| id          | the CMR identifier for the result                                                                               |
| location    | the URL at which the full metadata for the result can be retrieved                                              |
| revision-id | the internal CMR version number for the result                                                                  |

__Example__

```
curl -i "%CMR-ENDPOINT%/serviceoptions.xml?pretty=true&name=Serviceoption1"

HTTP/1.1 200 OK
Content-Type: application/xml; charset=UTF-8
<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>13</took>
    <references>
        <reference>
            <name>Serviceoption-name-v1</name>
            <id>SE1200443587-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/SO1200000000-PROV1/4</location>
            <revision-id>4</revision-id>
        </reference>
    </references>
</results>
```
##### JSON
The JSON response includes the following fields.

* hits - How many total serviceoptions were found.
* took - How long the search took in milliseconds
* items - a list of the current page of serviceoptions with the following fields
  * concept_id
  * revision_id
  * provider_id
  * native_id
  * name

__Example__

```
curl -g -i "%CMR-ENDPOINT%/serviceoptions.json?pretty=true&name="serviceoption-name"

HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8

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
The UMM JSON response contains meta-metadata of the serviceoption, the UMM fields and the associations field if applicable. The associations field only applies when there are collections associated with the serviceoption and will list the collections that are associated with the serviceoption.

__Example__

curl -g -i "%CMR-ENDPOINT%/serviceoptions.umm_json?name=Serviceoption1234"
HTTP/1.1 200 OK
Content-Type: application/vnd.nasa.cmr.umm_results+json;version=1.0.0; charset=utf-8
Content-Length: 555

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
                "concept-type": "serviceoption"
            },
            "umm": {
                "Id": "1B41335E-82DD-8AAB-B8A9-546CC6DE6CBD",
                "Name": "RangeSliderTest",
                "Description": "Testing range slider - updated DEMO_PROV",
                "Form": "<form></form>",
                "MetadataSpecification": {
                    "Name": "ServiceOption",
                    "Version": "1.0.0",
                    "URL": "https://cdn.earthdata.nasa.gov/generics/serviceoption/v1.0.0"
                }
            }
        }
    ]
}
```

##### <a name="retrieving-all-revisions-of-a-serviceoption"></a> Retrieving All Revisions of a Service Option

In addition to retrieving the latest revision for a service option parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON and UMM JSON response formats are supported for all revision searches merely change to 'umm_json' and 'json' repecitvely. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true". serviceoptions with only 1 revision will of course, return only one result.

    curl "%CMR-ENDPOINT%/serviceoptions.xml?concept_id=SO1200000000-PROV1&all_revisions=true"

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
            <name>Serviceoption-amazing-v3</name>
            <id>SO1200000000-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/SO1200000000-PROV1/4</location>
            <revision-id>3</revision-id>
        </reference>
        <reference>
            <name>Serviceoption-name-v4</name>
            <id>SO1200000001-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/SO1200000001-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```

#### <a name="sorting-serviceoption-results"></a> Sorting Service Option Results

By default, serviceoption results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

###### Valid Service Option Sort Keys
  * `name`
  * `long_name`
  * `provider`
  * `revision_date`

Examples of sorting by long_name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/serviceoptions?sort_key\[\]=-long_name"
    curl "%CMR-ENDPOINT%/serviceoptions?sort_key\[\]=%2Blong_name"
