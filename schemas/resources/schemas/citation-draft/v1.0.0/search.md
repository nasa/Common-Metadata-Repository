### <a name="visualization-draft"></a> Visualization Draft

Visualization Drafts are draft records that inform users about the visualizations that are available in a collection when working with data files. Visualization metadata is stored in the JSON format [UMM-Visualization Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas/browse/visualization).

#### <a name="searching-for-visualization-drafts"></a> Searching for Visualization Drafts

Visualization Drafts can be searched for by sending a request to `%CMR-ENDPOINT%/visualization-drafts`. XML reference, JSON and UMM JSON response formats are supported for Visualization Draft searches.

Visualization Draft search results are paged. See [Paging Details](#paging-details) for more information on how to page through Visualization Draft search results.

##### <a name="visualization-draft-search-params"></a> Visualization Draft Search Parameters

The following parameters are supported when searching for Visualization Drafts.

##### Standard Parameters

* page\_size
* page\_num
* pretty

##### Visualization Draft Matching Parameters

These parameters will match fields within a Visualization Draft. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are bitwise `OR`ed together.

* name - options: pattern, ignore\_case
* provider - options: pattern, ignore\_case
* native\_id - options: pattern, ignore\_case
* concept\_id

```
    curl "%CMR-ENDPOINT%/visualization-drafts?concept_id=VID1200000000-PROV1"
```

##### <a name="visualization-draft-search-response"></a> Visualization Draft Search Response

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
| name        | the value of the Name field in Visualization Draft metadata.      |
| id          | the CMR identifier for the result                                  |
| location    | the URL at which the full metadata for the result can be retrieved |
| revision-id | the internal CMR version number for the result                     |

__Example__

```
    curl -H "Cmr-Pretty: true" \
        "%CMR-ENDPOINT%/visualization-drafts.xml?name=visualization-name"
```

__Sample response__

```
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>1</hits>
        <took>13</took>
        <references>
            <reference>
                <name>visualization-name</name>
                <id>VID1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/VID1200000000-PROV1/4</location>
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
        "%CMR-ENDPOINT%/visualization-drafts.json?name=visualization-name"
```

__Sample response__

```
    {
        "hits": 1,
        "took": 10,
        "items": [
            {
                "concept_id": "VID1200000000-PROV1",
                "revision\_id": 4,
                "provider\_id": "PROV-1",
                "native\_id": "sampleNative-Id",
                "name": "visualization-name"
            }
        ]
    }
```

##### UMM JSON

The UMM JSON response contains meta-metadata of the Visualization Draft, the UMM fields and the associations field if applicable. [To search over specific versions of UMM](#umm-json). 

__Example__

```
    curl -H "pretty=true" \
        "%CMR-ENDPOINT%/visualization-drafts.umm_json?name=visualization-name"
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
                    "concept-id": "VID1200000000-PROV1",
                    "revision-date": "2022-10-26T19:17:27.021Z",
                    "concept-type": "visualization-draft"
                },
                "umm": {
                    "Identifier": "OPERA_L3_DSWX-HLS_LL_v1_STD",
                    "Name": "OPERA_L3_Dynamic_Surface_Water_Extent-HLS_v1_STD",
                    "Title": "OPERA Dynamic Surface Water Extent (L3)",
                    "Subtitle": "DSWx-HLS",
                    "Description": "YET_TO_SUPPLY",
                    "VisualizationType": "tiles",
                    "ScienceKeywords": [{
                        "Category": "EARTH SCIENCE",
                        "Topic": "SPECTRAL/ENGINEERING",
                        "Term": "tbd",
                        "VariableLevel1": "tbd",
                        "DetailedVariable": "tbd"
                    }],
                    "ConceptIds": [{
                        "Type": "STD",
                        "Value": "C2617126679-POCLOUD",
                        "ShortName": "OPERA_L3_DSWX-HLS_V1",
                        "Title": "OPERA Dynamic Surface Water Extent from Harmonized Landsat Sentinel-2 product (Version 1)",
                        "Version": "1.0",
                        "DataCenter": "POCLOUD"
                    }],
                    "Specification": {
                    "ProductIdentification": {
                        "InternalIdentifier": "OPERA_L3_DSWX-HLS_LL_v1_STD",
                        "StandardOrNRTExternalIdentifier": "OPERA_L3_Dynamic_Surface_Water_Extent-HLS_v1_STD",
                        "BestAvailableExternalIdentifier": "OPERA_L3_Dynamic_Surface_Water_Extent-HLS",
                        "GIBSTitle": "OPERA Dynamic Surface Water Extent (L3, DSWx-HLS)",
                        "WorldviewTitle": "OPERA Dynamic Surface Water Extent (L3)",
                        "WorldviewSubtitle": "DSWx-HLS"
                    },
                    "ProductMetadata": {
                        "InternalIdentifier": "OPERA_L3_DSWX-HLS_LL_v1_STD",
                        "SourceDatasets": ["C2617126679-POCLOUD"],
                        "RepresentingDatasets": ["C2617126679-POCLOUD"],
                        "ScienceParameters": [ "dswx_hls"],
                        "ParameterUnits": [null],
                        "Measurement": "Surface Water Extent",
                        "GranuleOrComposite": "Granule",
                        "DataDayBreak": "00:00:00Z",
                        "VisualizationLatency": "1 day",
                        "UpdateInterval": 1440,
                        "TemporalCoverage": "2023-04-10/P1D",
                        "WGS84SpatialCoverage": [
                            -84.0,
                            -180.0,
                            84.0,
                            180.0],
                        "NativeSpatialCoverage": [
                            -84.0,
                            -180.0,
                            84.0,
                            180.0],
                        "AscendingOrDescending": "N/A",
                        "ColorMap": "colormap is embedded in the tif file",
                        "Ongoing": true,
                        "RetentionPeriod": -1,
                        "Daynight": ["day"],
                        "OrbitTracks": [
                            "OrbitTracks_Landsat-8_Descending",
                            "OrbitTracks_Sentinel-2A_Descending",
                            "OrbitTracks_Sentinel-2B_Descending"],
                        "OrbitDirection": ["descending"],
                        "LayerPeriod": "Daily",
                        "ows:Identifier": "OPERA_L3_Dynamic_Surface_Water_Extent-HLS_v1_STD",
                        "wmts:TileMatrixSetLink": {"TileMatrixSet": "31.25m"},
                        "wmts:Dimension": {
                            "Identifier": "Time",
                            "UOM": "ISO8601",
                            "Default": "2024-11-19",
                            "Current": false,
                            "Value": [
                                "2024-05-11/2024-06-02/P1D",
                                "2024-06-04/2024-11-19/P1D"]},
                        "wmts:Format": ["image/png"],
                        "ows:Metadata": [{
                            "xlink:Href": "https://gitc.earthdata.nasa.gov/colormaps/v1.0/OPERA_Dynamic_Surface_Water_Extent.xml",
                            "xlink:Role": "http://earthdata.nasa.gov/gibs/metadata-type/colormap/1.0",
                            "xlink:Title": "GIBS Color Map: Data - RGB Mapping",
                            "xlink:Type": "simple"
                        }]
                    }
                },
                "Generation": {
                    "SourceProjection": "EPSG:4326",
                    "SourceResolution": "Native",
                    "SourceFormat": "GeoTIFF",
                    "SourceColorModel": "Indexed RGBA",
                    "SourceCoverage": "Tiled",
                    "OutputProjection": "EPSG:4326",
                    "OutputResolution": "31.25m",
                    "OutputFormat": "PPNG"
                },
                "MetadataSpecification": {
                    "URL": "https://cdn.earthdata.nasa.gov/umm/visualization/v1.1.0",
                    "Name": "Visualization",
                    "Version": "1.1.0"
                }
            }
        ]
    }
```

#### <a name="sorting-visualization-draft-results"></a> Sorting Visualization Draft Results

By default, Visualization Draft results are sorted by name, then by provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Visualization Sort Keys

* name
* provider
* revision_date

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

```
    curl "%CMR-ENDPOINT%/visualization-drafts?sort_key\[\]=-name"
    curl "%CMR-ENDPOINT%/visualization-drafts?sort_key\[\]=%2Bname"
```