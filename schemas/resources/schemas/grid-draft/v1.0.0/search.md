### <a name="grid-draft"></a> Grid Draft

Grid Drafts are draft records that inform users about the types of Grids that are available when reprojecting data using services. Grid metadata is stored in the JSON format [UMM-Grid Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas/browse/grid).

#### <a name="searching-for-grid-drafts"></a> Searching for Grid Drafts

Grid Drafts can be searched for by sending a request to `%CMR-ENDPOINT%/grid-drafts`. XML reference, JSON and UMM JSON response formats are supported for Grid Draft searches.

Grid Draft search results are paged. See [Paging Details](#paging-details) for more information on how to page through Grid Draft search results.

##### <a name="grid-draft-search-params"></a> Grid Draft Search Parameters

The following parameters are supported when searching for Grid Drafts.

##### Standard Parameters

* page\_size
* page\_num
* pretty

##### Grid Draft Matching Parameters

These parameters will match fields within a Grid Draft. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are bitwise `OR`ed together.

* name - options: pattern, ignore\_case
* provider - options: pattern, ignore\_case
* native\_id - options: pattern, ignore\_case
* concept\_id

```
    curl "%CMR-ENDPOINT%/grid-drafts?concept_id=GD1200000000-PROV1"
```

##### <a name="grid-draft-search-response"></a> Grid Draft Search Response

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
| name        | the value of the Name field in the draft metadata.      |
| id          | the CMR identifier for the result                                  |
| location    | the URL at which the full metadata for the result can be retrieved |
| revision-id | the internal CMR version number for the result                     |

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/grid-drafts.xml?name=grid-name"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>1</hits>
        <took>13</took>
        <references>
            <reference>
                <name>grid-name</name>
                <id>GD1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/GD1200000000-PROV1/4</location>
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
        "%CMR-ENDPOINT%/grid-drafts.json?name=grid-name"
```

__Sample response__

```
    {
        "hits": 1,
        "took": 10,
        "items": [
            {
                "concept_id": "GD1200000000-PROV1",
                "revision\_id": 4,
                "provider\_id": "PROV-1",
                "native\_id": "sampleNative-Id",
                "name": "grid-name"
            }
        ]
    }
```

##### UMM JSON

The UMM JSON response contains meta-metadata of the Grid Draft, the UMM fields and the associations field if applicable. [To search over specific versions of UMM](#umm-json).

__Example__

```
    curl -H "pretty=true" \
        "%CMR-ENDPOINT%/grid-drafts.umm_json?name=grid-name"
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
                    "concept-id": "GD1200000000-PROV1",
                    "revision-date": "2022-10-26T19:17:27.021Z",
                    "concept-type": "grid-draft"
                },
                "umm": {
                    "MetadataSpecification": {
                        "URL": "https://cdn.earthdata.nasa.gov/generic/grid/v0.0.1",
                        "Name": "Grid",
                        "Version": "0.0.1"
                    },
                    "Name": "Grid-A7-v1",
                    "LongName": "Grid A-7 version 1.0",
                    "Version": "v1.0",
                    "Description": "A sample grid",
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
                            "ScaleDimensions": [{
                                "Unit": "Meter",
                                "0_360_DegreeProjection": true,
                                "Y-Dimension": 0,
                                "X-Dimension": 30
                            }]
                        }
                    }
                }
            }
        ]
    }
```

#### <a name="sorting-grid-draft-results"></a> Sorting Grid Draft Results

By default, Grid Draft results are sorted by name, then by provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Grid Sort Keys

* name
* provider
* revision_date

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

```
    curl "%CMR-ENDPOINT%/grid-drafts?sort_key\[\]=-name"
    curl "%CMR-ENDPOINT%/grid-drafts?sort_key\[\]=%2Bname"
```
