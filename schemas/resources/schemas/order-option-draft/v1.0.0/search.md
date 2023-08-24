### <a name="order-option-draft"></a> Order Option Draft

Order Option Drafts are draft records that inform users about the options that are available to a collection when ordering data files. Order Option metadata is stored in the JSON format [UMM-Order-Option Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas/browse/order-option).

#### <a name="searching-for-order-option-drafts"></a> Searching for Order Option Drafts

Order Option Drafts can be searched for by sending a request to `%CMR-ENDPOINT%/order-option-drafts`. XML reference, JSON and UMM JSON response formats are supported for Order Option Draft searches.

Order Option Draft search results are paged. See [Paging Details](#paging-details) for more information on how to page through Order Option Draft search results.

##### <a name="order-option-draft-search-params"></a> Order Option Draft Search Parameters

The following parameters are supported when searching for Order Option Drafts.

##### Standard Parameters

* page\_size
* page\_num
* pretty

##### Order Option Draft Matching Parameters

These parameters will match fields within an Order Option Draft. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are bitwise `OR`ed together.

* name - options: pattern, ignore\_case
* provider - options: pattern, ignore\_case
* native\_id - options: pattern, ignore\_case
* concept\_id

```
    curl "%CMR-ENDPOINT%/order-option-drafts?concept_id=OOD1200000000-PROV1"
```

##### <a name="order-option-draft-search-response"></a> Order Option Draft Search Response

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
| name        | the value of the Name field in Order Option Draft metadata.      |
| id          | the CMR identifier for the result                                  |
| location    | the URL at which the full metadata for the result can be retrieved |
| revision-id | the internal CMR version number for the result                     |

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/order-option-drafts.xml?name=order-option-name"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>1</hits>
        <took>13</took>
        <references>
            <reference>
                <name>order-option-name</name>
                <id>OOD1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/OOD1200000000-PROV1/4</location>
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
        "%CMR-ENDPOINT%/order-option-drafts.json?name=order-option-name"
```

__Sample response__

```
    {
        "hits": 1,
        "took": 10,
        "items": [
            {
                "concept_id": "OOD1200000000-PROV1",
                "revision\_id": 4,
                "provider\_id": "PROV-1",
                "native\_id": "sampleNative-Id",
                "name": "order-option-name"
            }
        ]
    }
```

##### UMM JSON

The UMM JSON response contains meta-metadata of the Order Option Draft, the UMM fields and the associations field if applicable. [To search over specific versions of UMM](#umm-json). 

__Example__

```
    curl -H "pretty=true" \
        "%CMR-ENDPOINT%/order-option-drafts.umm_json?name=order-option-name"
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
                    "concept-id": "OOD1200000000-PROV1",
                    "revision-date": "2022-10-26T19:17:27.021Z",
                    "concept-type": "order-option-draft"
                },
                "umm": {
                    "Id": "0AF0BB4E",
                    "Name": "With Browse",
                    "Description": "",
                    "Form": "<form xmlns=\"http://echo.nasa.gov/v9/echoforms\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"> <model> <instance> <ecs:options xmlns:ecs=\"http://ecs.nasa.gov/options\"> <!-- ECS distribution options example --> <ecs:distribution> <ecs:mediatype> <ecs:value>FtpPull</ecs:value> </ecs:mediatype> <ecs:mediaformat> <ecs:ftppull-format> <ecs:value>FILEFORMAT</ecs:value> </ecs:ftppull-format> </ecs:mediaformat> </ecs:distribution> <ecs:do-ancillaryprocessing>true</ecs:do-ancillaryprocessing> <ecs:ancillary> <ecs:orderBrowse/> </ecs:ancillary> </ecs:options> </instance> </model> <ui> <group id=\"mediaOptionsGroup\" label=\"Media Options\" ref=\"ecs:distribution\"> <output id=\"MediaTypeOutput\" label=\"Media Type:\" relevant=\"ecs:mediatype/ecs:value ='FtpPull'\" type=\"xsd:string\" value=\"'HTTPS Pull'\"/> <output id=\"FtpPullMediaFormatOutput\" label=\"Media Format:\" relevant=\"ecs:mediaformat/ecs:ftppull-format/ecs:value='FILEFORMAT'\" type=\"xsd:string\" value=\"'File'\"/> </group> <group id=\"checkancillaryoptions\" label=\"Additional file options:\" ref=\"ecs:ancillary\" relevant=\"//ecs:do-ancillaryprocessing = 'true'\"> <input label=\"Include associated Browse file in order\" ref=\"ecs:orderBrowse\" type=\"xsd:boolean\"/> </group> </ui> </form>",
                    "Scope":"PROVIDER",
                    "SortKey": "Name",
                    "Deprecated": false,
                    "MetadataSpecification": {
                        "Name": "Order Option",
                        "Version": "1.0.0",
                        "URL": "https://cdn.earthdata.nasa.gov/generics/order-option/v1.0.0"
                    }
                }
            }
        ]
    }
```

#### <a name="sorting-order-option-draft-results"></a> Sorting Order Option Draft Results

By default, Order Option Draft results are sorted by name, then by provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Order Option Sort Keys

* name
* provider
* revision_date

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

```
    curl "%CMR-ENDPOINT%/order-option-drafts?sort_key\[\]=-name"
    curl "%CMR-ENDPOINT%/order-option-drafts?sort_key\[\]=%2Bname"
```
