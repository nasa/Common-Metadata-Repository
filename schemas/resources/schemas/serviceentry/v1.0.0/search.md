### <a name="serviceentry"></a> Serviceentry

Serviceentrys provide metadata support to perform reprojection in the reprojection services [UMM-Serviceentry Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas/browse/serviceentry) schema.

#### <a name="searching-for-serviceentrys"></a> Searching for serviceentrys

Serviceentrys can be searched for by sending a request to `%CMR-ENDPOINT%/serviceentrys`. XML reference, JSON and UMM JSON response formats are supported for serviceentrys search.

serviceentry search results are paged. See [Paging Details](#paging-details) for more information on how to page through serviceentry search results.

##### <a name="serviceentry-search-params"></a> Serviceentry Search Parameters

The following parameters are supported when searching for serviceentrys.

##### Standard Parameters
* page_size
* page_num
* pretty


##### Serviceentry Matching Parameters

These parameters will match fields within a serviceentry. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are ORed together.

* name
  * options: pattern, ignore_case
* provider
  * options: pattern, ignore_case
* native_id
  * options: pattern, ignore_case
* concept_id
* id


````
curl -g "%CMR-ENDPOINT%/serviceentrys?concept_id=GRD1200442373-DEMO_PROV"
````
//TODO: I don't know how which keywords are being used here
* keyword (free text)
  * keyword search is case insensitive and supports wild cards ? and *. There is a limit of 30 wild cards allowed in keyword searches. Within 30 wild cards, there's also limit on the max keyword string length. The longer the max keyword string length, the less number of keywords with wild cards allowed.

The following fields are indexed for keyword (free text) search:

* name
* long name
* provider
* concept-id
* Associated collection concept ids
* Associated collection concept ids

##### <a name="serviceentry-search-response"></a> Serviceentry Search Response

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
| name        | the value of the Name field in serviceentry metadata.                                                               |
| id          | the CMR identifier for the result                                                                               |
| location    | the URL at which the full metadata for the result can be retrieved                                              |
| revision-id | the internal CMR version number for the result                                                                  |

__Example__

```
curl -i "%CMR-ENDPOINT%/serviceentrys.xml?pretty=true&name=Serviceentry1"

HTTP/1.1 200 OK
Content-Type: application/xml; charset=UTF-8
Content-Length: 393

<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>13</took>
    <references>
        <reference>
            <name>Serviceentry-name-v1</name>
            <id>GRD1200442373-DEMO_PROV</id>
            <location>%CMR-ENDPOINT%/concepts/GRD1200442373-DEMO_PROV/4</location>
            <revision-id>4</revision-id>
        </reference>
    </references>
</results>
```
##### JSON
The JSON response includes the following fields.

* hits - How many total serviceentrys were found.
* took - How long the search took in milliseconds
* items - a list of the current page of serviceentrys with the following fields
  * concept_id
  * revision_id
  * provider_id
  * native_id
  * name
  * long_name

__Example__

```
curl -g -i "%CMR-ENDPOINT%/serviceentrys.json?pretty=true&name=Var*&name="serviceentry-name"

HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Content-Length: 292

{
    "hits": 1,
    "took": 10,
    "items": [
        {
            "concept_id": "GRD1200442373-DEMO_PROV",
            "revision_id": 4,
            "provider_id": "DEMO_PROV",
            "native_id": "sampleNative-Id",
            "name": "Serviceentry-name-v1"
        }
    ]
}
```
##### UMM JSON
The UMM JSON response contains meta-metadata of the serviceentry, the UMM fields and the associations field if applicable. The associations field only applies when there are collections associated with the serviceentry and will list the collections that are associated with the serviceentry.
TODO: this this version num correct?
__Example__

```
curl -g -i "%CMR-ENDPOINT%/serviceentrys.umm_json?name=Serviceentry1234"
HTTP/1.1 200 OK
Content-Type: application/vnd.nasa.cmr.umm_results+json;version=0.0.1; charset=utf-8
Content-Length: 1177

{
    "hits": 1,
    "took": 23,
    "items": [
        {
            "meta": {
                "revision-id": 4,
                "deleted": false,
                "provider-id": "DEMO_PROV",
                "user-id": "user-id-example",
                "native-id": "example-native-id",
                "concept-id": "GRD1200442373-DEMO_PROV",
                "revision-date": "2022-10-10T01:18:33.568Z",
                "concept-type": "serviceentry"
            },
            "umm": {
                "RelatedURLs": [
                    {
                        "URL": "https://example.gov/",
                        "URLContentType": "C-Type",
                        "Type": "Type"
                    },
                    {
                        "URL": "https://example.gov/two",
                        "Description": "Details about the URL or page",
                        "URLContentType": "C-Type",
                        "Type": "Type"
                    }
                ],
                "Organization": {
                    "ShortName": "NASA/GSFC/SED/ESD/GCDC/GESDISC",
                    "LongName": "Goddard Earth Sciences Data and Information Services Center (formerly Goddard DAAC), Global Change Data Center, Earth Sciences Division, Science and Exploration Directorate, Goddard Space Flight Center, NASA",
                    "RelatedURLs": [
                        {
                            "URL": "https://example.gov",
                            "URLContentType": "C-Type",
                            "Type": "Type"
                        }
                    ],
                    "ContactMechanisms": [
                        {
                            "Type": "Email",
                            "Value": "who@example.gov"
                        },
                        {
                            "Type": "Email",
                            "Value": "you@example.gov"
                        }
                    ]
                },
                "AdditionalAttribute": {
                    "Name": "attribute-1",
                    "Description": "Sample",
                    "DataType": "STRING"
                },
                "Description": "A sample serviceentry that Ed just made",
                "ServiceentryDefinition": {
                    "CoordinateReferenceSystemID": {
                        "Type": "EPSG",
                        "Code": "EPSG:4326",
                        "Title": "WGS84 - World Geodetic System 1984, used in GPS - EPSG:4326",
                        "URL": "https://epsg.io/4326"
                    },
                    "DimensionSize": {
                        "Height": 3.14,
                        "Width": 3.14,
                        "Time": "12:00:00Z",
                        "Other": {
                            "Name": "Other Dimension Size",
                            "Value": "42",
                            "Description": "Details about the other dimension size."
                        }
                    },
                    "Resolution": {
                        "Unit": "Meter",
                        "LongitudeResolution": 64,
                        "LatitudeResolution": 32
                    },
                    "SpatialExtent": {
                        "0_360_DegreeProjection": false,
                        "NorthBoundingCoordinate": -90.0,
                        "EastBoundingCoordinate": 180.0,
                        "SouthBoundingCoordinate": 90.0,
                        "WestBoundingCoordinate": -180.0
                    },
                    "ScaleExtent": {
                        "ScaleDimensions": [
                            {
                                "Unit": "Meter",
                                "0_360_DegreeProjection": true,
                                "Y-Dimension": 0,
                                "X-Dimension": 30
                            },
                            {
                                "Unit": "Meter",
                                "0_360_DegreeProjection": true,
                                "Y-Dimension": 0,
                                "X-Dimension": 360
                            },
                            {
                                "Unit": "Meter",
                                "0_360_DegreeProjection": true,
                                "Y-Dimension": 0,
                                "X-Dimension": 180
                            }
                        ]
                    },
                    "Distortion": {
                        "Description": "Distortion around the serviceentry edge",
                        "Percent": 31
                    },
                    "Uniform-Serviceentry": true,
                    "Bounded-Serviceentry": true
                },
                "Version": "v1.0",
                "MetadataDate": {
                    "Create": "2022-04-20T08:00:00Z"
                },
                "Name": "Serviceentry-amazing-v1",
                "MetadataSpecification": {
                    "URL": "https://cdn.earthdata.nasa.gov/generic/serviceentry/v0.0.1",
                    "Name": "Serviceentry",
                    "Version": "0.0.1"
                },
                "LongName": "Serviceentry A-7 version 1.0"
            }
        }
    ]
}
```

##### <a name="retrieving-all-revisions-of-a-serviceentry"></a> Retrieving All Revisions of a Serviceentry

In addition to retrieving the latest revision for a serviceentry parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON and UMM JSON response formats are supported for all revision searches. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true".

    curl "%CMR-ENDPOINT%/serviceentrys.xml?concept_id=GRD1200000010-PROV1&all_revisions=true"

__Sample response__

```
<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>4</hits>
    <took>80</took>
    <references>
        <reference>
            <name>Serviceentry-name-v1</name>
            <id>GRD1200442373-DEMO_PROV</id>
            <deleted>true</deleted>
            <revision-id>2</revision-id>
        </reference>
        <reference>
            <name>Serviceentry-name-v2</name>
            <id>GRD1200442373-DEMO_PROV</id>
            <location>%cmr-endpoint%/concepts/GRD1200442373-DEMO_PROV/3</location>
            <revision-id>3</revision-id>
        </reference>
        <reference>
            <name>Serviceentry-amazing-v3</name>
            <id>GRD1200442373-DEMO_PROV</id>
            <location>%cmr-endpoint%/concepts/GRD1200442373-DEMO_PROV/4</location>
            <revision-id>4</revision-id>
        </reference>
        <reference>
            <name>Serviceentry-name-v4</name>
            <id>GRD1200442373-DEMO_PROV</id>
            <location>%CMR-ENDPOINT%/concepts/GRD1200442373-DEMO_PROV/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```

#### <a name="sorting-serviceentry-results"></a> Sorting Serviceentry Results

By default, serviceentry results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

###### Valid Serviceentry Sort Keys
  * `name`
  * `long_name`
  * `provider`
  * `revision_date`

Examples of sorting by long_name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/serviceentrys?sort_key\[\]=-long_name"
    curl "%CMR-ENDPOINT%/serviceentrys?sort_key\[\]=%2Blong_name"
