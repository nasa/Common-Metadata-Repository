### <a name="orderoption"></a> Order Option

Orderoptions are an abstract structures used to define one or more settable parameters when ordering data. Orderoption metadata is stored in the JSON format[UMM-Orderoption Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas/browse/orderoption) schema.

#### <a name="searching-for-orderoptions"></a> Searching for Order Options

Orderoptions can be searched for by sending a request to `%CMR-ENDPOINT%/orderoptions`. XML reference, JSON and UMM JSON response formats are supported for orderoptions search.

orderoption search results are paged. See [Paging Details](#paging-details) for more information on how to page through orderoption search results.

##### <a name="orderoption-search-params"></a> Order Option Search Parameters

The following parameters are supported when searching for orderoptions.

##### Standard Parameters
* page_size
* page_num
* pretty


##### Order Option Matching Parameters

These parameters will match fields within a orderoption. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are ORed together.

* name
  * options: pattern, ignore_case
* provider
  * options: pattern, ignore_case
* native_id
  * options: pattern, ignore_case
* concept_id
* id


````
curl -g "%CMR-ENDPOINT%/orderoptions?concept_id=OO1200442213-PROV1"
````
##### <a name="orderoption-search-response"></a> Order Option Search Response

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
| name        | the value of the Name field in orderoption metadata.                                                               |
| id          | the CMR identifier for the result                                                                               |
| location    | the URL at which the full metadata for the result can be retrieved                                              |
| revision-id | the internal CMR version number for the result                                                                  |

__Example__

```
curl -i "%CMR-ENDPOINT%/orderoptions.xml?pretty=true&name=Orderoption1"

HTTP/1.1 200 OK
Content-Type: application/xml; charset=UTF-8
Content-Length: 393

<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>13</took>
    <references>
        <reference>
            <name>Orderoption-name-v1</name>
            <id>OO1200442213-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/OO1200442213-PROV1/4</location>
            <revision-id>4</revision-id>
        </reference>
    </references>
</results>
```
##### JSON
The JSON response includes the following fields.

* hits - How many total orderoptions were found.
* took - How long the search took in milliseconds
* items - a list of the current page of orderoptions with the following fields
  * concept_id
  * revision_id
  * provider_id
  * native_id
  * name

__Example__

```
curl -g -i "%CMR-ENDPOINT%/orderoptions.json?pretty=true&name="orderoption-name"

HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Content-Length: 292

{
    "hits": 1,
    "took": 10,
    "items": [
        {
            "concept_id": "OO1200442213-PROV1",
            "revision_id": 4,
            "provider_id": "PROV-1",
            "native_id": "sampleNative-Id",
            "name": "Orderoption-name-v1"
        }
    ]
}
```
##### UMM JSON
The UMM JSON response contains meta-metadata of the orderoption, the UMM fields and the associations field if applicable. The associations field only applies when there are collections associated with the orderoption and will list the collections that are associated with the orderoption.
TODO: this this version num correct?
__Example__

```
curl -g -i "%CMR-ENDPOINT%/orderoptions.umm_json?name=Orderoption1234"

HTTP/1.1 200 OK
Content-Type: application/vnd.nasa.cmr.umm_results+json; charset=UTF-8
Content-Length: 2063

{
  "hits" : 1,
  "took" : 44,
  "items" : [ {
    "meta" : {
      "revision-id" : 1,
      "deleted" : false,
      "provider-id" : "PROV1",
      "user-id" : "ECHO_SYS",
      "native-id" : "orderoption-1",
      "concept-id" : "OO1200000008-PROV1",
      "revision-date" : "2022-10-27T20:55:49.141Z",
      "concept-type" : "orderoption"
    },
    "umm" : {
      "Id" : "0AF0BB4E",
      "Name" : "With Browse",
      "Description" : "",
      "Form" : "<form xmlns=\"http://echo.nasa.gov/v9/echoforms\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"> <model> <instance> <ecs:options xmlns:ecs=\"http://ecs.nasa.gov/options\"> <!-- ECS distribution options example --> <ecs:distribution> <ecs:mediatype> <ecs:value>FtpPull</ecs:value> </ecs:mediatype> <ecs:mediaformat> <ecs:ftppull-format> <ecs:value>FILEFORMAT</ecs:value> </ecs:ftppull-format> </ecs:mediaformat> </ecs:distribution> <ecs:do-ancillaryprocessing>true</ecs:do-ancillaryprocessing> <ecs:ancillary> <ecs:orderBrowse/> </ecs:ancillary> </ecs:options> </instance> </model> <ui> <group id=\"mediaOptionsGroup\" label=\"Media Options\" ref=\"ecs:distribution\"> <output id=\"MediaTypeOutput\" label=\"Media Type:\" relevant=\"ecs:mediatype/ecs:value ='FtpPull'\" type=\"xsd:string\" value=\"'HTTPS Pull'\"/> <output id=\"FtpPullMediaFormatOutput\" label=\"Media Format:\" relevant=\"ecs:mediaformat/ecs:ftppull-format/ecs:value='FILEFORMAT'\" type=\"xsd:string\" value=\"'File'\"/> </group> <group id=\"checkancillaryoptions\" label=\"Additional file options:\" ref=\"ecs:ancillary\" relevant=\"//ecs:do-ancillaryprocessing = 'true'\"> <input label=\"Include associated Browse file in order\" ref=\"ecs:orderBrowse\" type=\"xsd:boolean\"/> </group> </ui> </form>",
      "Scope" : "PROVIDER",
      "SortKey" : "Name",
      "Deprecated" : false,
      "MetadataSpecification" : {
        "Name" : "OrderOption",
        "Version" : "1.0.0",
        "URL" : "https://cdn.earthdata.nasa.gov/generics/orderoption/v1.0.0"
      }
    }
  } ]
}
```

##### <a name="retrieving-all-revisions-of-a-orderoption"></a> Retrieving All Revisions of an Order Option

In addition to retrieving the latest revision for a orderoption parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON and UMM JSON response formats are supported for all revision searches merely change to 'umm_json' and 'json' repecitvely. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true".

    curl "%CMR-ENDPOINT%/orderoptions.xml?concept_id=OO1200443608-PROV1&all_revisions=true"

__Sample response__

```
<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>4</hits>
    <took>80</took>
    <references>
        <reference>
            <name>Orderoption-name-v1</name>
            <id>OO1200000000-DEMO_PROV</id>
            <deleted>true</deleted>
            <revision-id>1</revision-id>
        </reference>
        <reference>
            <name>Orderoption-name-v2</name>
            <id>OO1200000000-DEMO_PROV</id>
            <location>%CMR-ENDPOINT%/concepts/OO1200443608-DEMO_PROV/3</location>
            <revision-id>2</revision-id>
        </reference>
        <reference>
            <name>Orderoption-amazing-v3</name>
            <id>OO1200000000-DEMO_PROV</id>
            <location>%CMR-ENDPOINT%/concepts/OO1200443608-DEMO_PROV/4</location>
            <revision-id>3</revision-id>
        </reference>
        <reference>
            <name>Orderoption-name-v4</name>
            <id>OO1200000001-DEMO_PROV</id>
            <location>%CMR-ENDPOINT%/concepts/OO1200443608-DEMO_PROV/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```

#### <a name="sorting-orderoption-results"></a> Sorting Order Option Results

By default, order option results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

###### Valid Orderoption Sort Keys
  * `name`
  * `long_name`
  * `provider`
  * `revision_date`

Examples of sorting by long_name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/orderoptions?sort_key\[\]=-long_name"
    curl "%CMR-ENDPOINT%/orderoptions?sort_key\[\]=%2Blong_name"
