### <a name="grid"></a> Grid

Grid metadata describes a set of coordinates and other supporting data that a service can use to reproject data. Grid metadata is stored in the JSON format [UMM-Grid Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas/browse/grid).

#### <a name="searching-for-grids"></a> Searching for Grids

Grids can be searched for by sending a request to `%CMR-ENDPOINT%/grids`. XML reference, JSON, and UMM JSON response formats are supported for Grids search.

Grid search results are paged. See [Paging Details](#paging-details) for more information on how to page through Grid search results.

##### <a name="grid-search-params"></a> Grid Search Parameters

The following parameters are supported when searching for Grids.

##### Standard Parameters

* page\_size
* page\_num
* pretty

##### Grid Matching Parameters

These parameters will match fields within a Grid. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are bitwise ORed together.

* name - options: pattern, ignore\_case
* provider - options: pattern, ignore\_case
* native\_id - options: pattern, ignore\_case
* concept\_id
* id

```
    curl "%CMR-ENDPOINT%/grids?concept_id=GRD1200442373-PROV1"
```

##### <a name="grid-search-response"></a> Grid Search Response

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
| name        | the value of the Name field in the Grid metadata.                  |
| id          | the CMR identifier for the result                                  |
| location    | the URL at which the full metadata for the result can be retrieved |
| revision-id | the internal CMR version number for the result                     |

__Example__

```
    curl -H "Cmr-Pretty: true" "%CMR-ENDPOINT%/grids.xml?name=Grid1"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>1</hits>
        <took>13</took>
        <references>
            <reference>
                <name>Grid-name-v1</name>
                <id>GRD1200442373-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/GRD1200442373-PROV1/4</location>
                <revision-id>4</revision-id>
            </reference>
        </references>
    </results>
```

##### JSON

The JSON response includes the following fields.

* hits - How many total Grids were found.
* took - How long the search took in milliseconds
* items - a list of the current page of Grids with the following fields
    * concept\_id
    * revision\_id
    * provider\_id
    * native\_id
    * name
    * long\_name

__Example__

```
    curl -H "Cmr-Pretty: true" "%CMR-ENDPOINT%/grids.json"
```

__Sample response__

```
    {
        "hits": 1,
        "took": 10,
        "items": [
            {
                "concept_id": "GRD1200000000-PROV1",
                "revision_id": 4,
                "provider_id": "PROV1",
                "native_id": "sampleNative-Id",
                "name": "Grid-name-v1"
            }
        ]
    }
```

##### UMM JSON

The UMM JSON response contains meta-metadata of the Grid, the UMM fields and the associations field if applicable.

__Example__

```
    curl -H "Cmr-Pretty: true" "%CMR-ENDPOINT%/grids.umm_json?name=Grid-v1"
```

__Sample response__

```
    {
        "hits": 1,
        "took": 176,
        "items": [
            {
                "meta": {
                    "revision-id": 1,
                    "deleted": false,
                    "provider-id": "PROV1",
                    "user-id": "someuser",
                    "native-id": "grid1234",
                    "concept-id": "GRD1200000000-PROV1",
                    "revision-date": "2022-10-28T15:38:07.588Z",
                    "concept-type": "grid"
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
                    "Description": "A sample grid for testing",
                    "GridDefinition": {
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
                            "Description": "Distortion around the grid edge",
                            "Percent": 31
                        },
                        "Uniform-Grid": true,
                        "Bounded-Grid": true
                    },
                    "Version": "v1.0",
                    "MetadataDate": {
                        "Create": "2022-04-20T08:00:00Z"
                    },
                    "Name": "Grid-v1",
                    "MetadataSpecification": {
                        "URL": "https://cdn.earthdata.nasa.gov/generic/grid/v0.0.1",
                        "Name": "Grid",
                        "Version": "0.0.1"
                    },
                    "LongName": "Grid A-7 version 1.0"
                }
            }
        ]
    }
```

#### <a name="retrieving-all-revisions-of-a-grid"></a> Retrieving All Revisions of a Grid

In addition to retrieving the latest revision for a Grid parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON, and UMM JSON response formats are supported for all revision searches. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true".

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/grids.xml?concept_id=GRD1200442373-PROV1&all_revisions=true"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>4</hits>
        <took>80</took>
        <references>
            <reference>
                <name>Grid-name-v2</name>
                <id>GRD1200442373-PROV1</id>
                <deleted>true</deleted>
                <revision-id>2</revision-id>
            </reference>
            <reference>
                <name>Grid-name-v3</name>
                <id>GRD1200442373-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/GRD1200442373-PROV1/3</location>
                <revision-id>3</revision-id>
            </reference>
            <reference>
                <name>Grid-name-v1</name>
                <id>GRD1200442373-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/GRD1200442373-PROV1/1</location>
                <revision-id>1</revision-id>
            </reference>
        </references>
    </results>
```

#### <a name="sorting-grid-results"></a> Sorting Grid Results

By default, Grid results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Grid Sort Keys

* name
* provider
* revision_date

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

```
    curl "%CMR-ENDPOINT%/grids?sort_key\[\]=-name"
    curl "%CMR-ENDPOINT%/grids?sort_key\[\]=%2Bname"
```
