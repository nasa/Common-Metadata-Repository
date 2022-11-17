### <a name="service-entry"></a> Service Entry

Service entries describe services provided by a data provider. Service Entry metadata is stored in the JSON format[UMM-Service-entry Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas/browse/service-entry).

#### <a name="searching-for-service-entries"></a> Searching for Service Entries

Service Entries can be searched for by sending a request to `%CMR-ENDPOINT%/service-entries`. XML reference, JSON and UMM JSON response formats are supported for Service Entries search.

Service Entry search results are paged. See [Paging Details](#paging-details) for more information on how to page through Service Entry search results.

##### <a name="service-entry-search-params"></a> Service Entry Search Parameters

The following parameters are supported when searching for Service Entries.

##### Standard Parameters

* page\_size
* page\_num
* pretty

##### Service Entry Matching Parameters

These parameters will match fields within a Service Entry. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are bitwise ORed together.

* name - options: pattern, ignore\_case
* provider - options: pattern, ignore\_case
* native\_id - options: pattern, ignore\_case
* concept\_id
* id

```
    curl "%CMR-ENDPOINT%/service-entries?concept_id=SE1200000000-PROV1"
```

##### <a name="service-entry-search-response"></a> Service Entry Search Response

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
| name        | the value of the Name field in the Service Entry metadata.         |
| id          | the CMR identifier for the result                                  |
| location    | the URL at which the full metadata for the result can be retrieved |
| revision-id | the internal CMR version number for the result                     |

__Example__

```
    curl -H "Cmr-Pretty: true" "%CMR-ENDPOINT%/service-entries.xml"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>1</hits>
        <took>13</took>
        <references>
            <reference>
                <name>Serviceentry-name-v1</name>
                <id>SE1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SE1200000000-PROV1/4</location>
                <revision-id>1</revision-id>
            </reference>
        </references>
    </results>
```

##### JSON

The JSON response includes the following fields.

* hits - How many total Service Entries were found.
* took - How long the search took in milliseconds
* items - a list of the current page of Service Entries with the following fields
  * concept\_id
  * revision\_id
  * provider\_id
  * native\_id
  * name

__Example__

```
    curl -H "Cmr-Pretty: true" "%CMR-ENDPOINT%/service-entries.json?name="service-entry-name"
```

__Sample response__

```
    {
        "hits": 1,
        "took": 10,
        "items": [
            {
                "concept\_id": "SE1200000000-PROV1",
                "revision\_id": 4,
                "provider\_id": "PROV1",
                "native\_id": "sampleNative-Id",
                "name": "Serviceentry-name-v1"
            }
        ]
    }
```

##### UMM JSON

The UMM JSON response contains meta-metadata of the Service Entry, the UMM fields and the associations field if applicable.

__Example__

```
    curl -H "Cmr-Pretty: true" "%CMR-ENDPOINT%/service-entries.umm_json?name=Serviceentry1234"
```

__Sample response__

```
    {
      "hits" : 1,
      "took" : 31,
      "items" : [ {
        "meta" : {
          "revision-id" : 1,
          "deleted" : false,
          "provider-id" : "PROV1",
          "user-id" : "ECHO_SYS",
          "native-id" : "serviceEntryNativeid",
          "concept-id" : "SE1200000007-PROV1",
          "revision-date" : "2022-10-27T20:44:34.249Z",
          "concept-type" : "service-entry"
        },
        "umm" : {
          "Id" : "0AF0BB4E-7455-FBB2-15C7-B5B7DE43AA6D",
          "Name" : "Test Service",
          "Description" : "Testing Service",
          "Type" : "SERVICE_IMPLEMENTATION",
          "InterfaceName" : "EOSIDS Service Interface",
          "URL" : "www.example.com",
          "MetadataSpecification" : {
            "Name" : "Service Entry",
            "Version" : "1.0.0",
            "URL" : "https://cdn.earthdata.nasa.gov/generics/service-entry/v1.0.0"
          }
        }
      } ]
    }
```

#### <a name="retrieving-all-revisions-of-a-service-entry"></a> Retrieving All Revisions of a Service Entry

In addition to retrieving the latest revision for a Service Entry parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON, and UMM JSON response formats are supported for all revision searches merely change to 'umm_json' and 'json' repecitvely. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true". Service Entries with only 1 revision will of course, return only one result.

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/service-entries.xml?concept_id=SE1200000006-PROV1&all_revisions=true"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>3</hits>
        <took>67</took>
        <references>
            <reference>
                <name>Test Service</name>
                <id>SE1200000006-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SE1200000006-PROV1/1</location>
                <revision-id>1</revision-id>
            </reference>
            <reference>
                <name>Test Service-v2</name>
                <id>SE1200000006-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SE1200000006-PROV1/2</location>
                <revision-id>2</revision-id>
            </reference>
            <reference>
                <name>Test Service-v3</name>
                <id>SE1200000006-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SE1200000006-PROV1/3</location>
                <revision-id>3</revision-id>
            </reference>
        </references>
    </results>
```

#### <a name="sorting-service-entry-results"></a> Sorting Service Entry Results

By default, Service Entry results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Service Entry Sort Keys

* name
* provider
* revision_date

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

```
    curl "%CMR-ENDPOINT%/service-entries?sort_key\[\]=-name"
    curl "%CMR-ENDPOINT%/service-entries?sort_key\[\]=%2Bname"
```
