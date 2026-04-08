---
title: Searching
description: Provides information on general methods of searching with the CMR API, as well as the formats that will be used for search results.
---

### <a name="search-with-post"></a> Search with POST

Search collections or granules with query parameters encoded form in POST request body.

    curl -i -XPOST %CMR-ENDPOINT%/collections -d "dataset_id[]=Example%20DatasetId&dataset_id[]=Dataset2"

### <a name="search-with-json-query"></a> Search with JSON Query

Search for collections or granules with JSON in a POST request body. The JSON must conform to the schema
that is defined in `%CMR-ENDPOINT%/site/CollectionJSONQueryLanguage.json` or `%CMR-ENDPOINT%/site/GranuleJSONQueryLanguage.json`
for collections and granules respectively. Only collection and granule search are supported.

    curl -XPOST -H "Content-Type: application/json" %CMR-ENDPOINT%/collections
    -d '{"condition": { "and": [{ "not": { "or": [{ "provider": "TEST" },
                                                  { "and": [{ "project": "test-project",
                                                              "platform": {"short_name": "mars-satellite"}}]}]}},
                                { "bounding_box": [-45,15,0,25],
                                  "science_keywords": { "category": "EARTH SCIENCE" }}]}}'

    curl -XPOST -H "Content-Type: application/json" %CMR-ENDPOINT%/granules
    -d '{"condition": { "and": [{ "not": { "or": [{ "provider": "TEST" },
                                                  { "and": [{ "project": "test-project",
                                                              "platform": {"short_name": "mars-satellite"}}]}]}},
                                { "bounding_box": [-45,15,0,25],
                                  "short_name": "Short_name 1"}]}}'

### <a name="search-with-aql"></a> Search with AQL

Search collections or granules with AQL in POST request body. The AQL must conform to the schema
that is defined in `%CMR-ENDPOINT%/site/IIMSAQLQueryLanguage.xsd`.

    curl -i -XPOST -H "Content-Type: application/xml" %CMR-ENDPOINT%/concepts/search -d '<?xml version="1.0" encoding="UTF-8"?>
    <query><for value="collections"/><dataCenterId><all/></dataCenterId>
    <where><collectionCondition><shortName><value>S1</value></shortName></collectionCondition></where></query>'

### <a name="temporal-range-searches"></a> Temporal Range searches

A couple of parameters used in search expect a date range as input. For example, the parameter "temporal" used in collection and granule searches and the parameter "equator_crossing_longitude" used in granule searches both accept date ranges. All these parameters expect temporal ranges in the same format. The temporal ranges can be specified as a pair of date-time values separated by comma(,). Exactly one of the two bounds of the interval can be omitted. In addition to comma separated values, one can also specify temporal ranges as [ISO 8601 time intervals](https://en.wikipedia.org/?title=ISO_8601#Time_intervals). Some examples of valid temporal range values are:

* `2000-01-01T10:00:00Z,2010-03-10T12:00:00Z` - matches data between `2000-01-01T10:00:00Z` and `2010-03-10T12:00:00Z`
* `,2010-03-10T12:00:00Z` - matches data before `2010-03-10T12:00:00Z`
* `2000-01-01T10:00:00Z,` - matches data after `2010-03-10T12:00:00Z`
* `2000-01-01T10:00:00Z/2010-03-10T12:00:00Z` - matches data between `2000-01-01T10:00:00Z` and `2010-03-10T12:00:00Z`
* `2000-01-01T10:00:00Z/` - matches data after `2010-03-10T12:00:00Z`
* `/2010-03-10T12:00:00Z` - matches data before `2010-03-10T12:00:00Z`
* `2000-01-01T10:00:00Z/P10Y2M10DT2H` - matches data between `2000-01-01T10:00:00Z` and a date 10 years 2 months 10 days and 2 hours after that or `2010-03-11T02:00:00Z`
* `P1Y2M10DT2H30M/2008-05-11T15:30:00Z` - matches data between `2008-07-11T16:30:00Z` and a date 1 year 2 months 10 days 2 hours and 30 minutes before that or `2007-05-01T14:00:00Z`.
* `2000-01-01T00:00:00.000Z,2023-01-31T23:59:59.999Z,1,31` - matches data between the Julian days `1` to `31` from `2000-01-01T00:00:00.000Z` to `2023-01-31T23:59:59.999Z`.

__Note__: ISO 8601 does not allow open-ended time intervals but the CMR API does allow specification of intervals which are open ended on one side. For example, `2000-01-01T10:00:00Z/` and `/2000-01-01T10:00:00Z` are valid ranges.

### <a name="search-for-tiles"></a> Search for Tiles

Tiles are geographic regions formed by splitting the world into rectangular regions in a projected coordinate system such as Sinusoidal Projection based off an Authalic Sphere. CMR supports searching of tiles which fall within a geographic region defined by a given input geometry. Currently, only tiles in MODIS Integerized Sinusoidal Grid(click [here](https://lpdaac.usgs.gov/products/modis_products_table/modis_overview) for more details on the grid) can be searched. The input geometry could be either a minimum bounding rectangle or one of point, line or polygon in spherical coordinates. The input coordinates are to be supplied in the same format as in granule and collection spatial searches (See under "Find granules by Spatial").

A query could consist of multiple spatial parameters, two points and a bounding box for example. All the spatial parameters are ORed in a query meaning a query will return all the tiles which intersect at-least one of the given geometries.

Here are some examples:
Find the tiles which intersect a polygon.

    curl -i "%CMR-ENDPOINT%/tiles?polygon=10,10,30,10,30,20,10,20,10,10"

Find the tiles which intersect a bounding rectangle.

    curl -i "%CMR-ENDPOINT%/tiles?bounding_box=-10,-5,10,5"

Find the tile which contains a point.

    curl -i "%CMR-ENDPOINT%/tiles?point=-84.2625,36.013"

Find all the tiles which a line intersects.

    curl -i "%CMR-ENDPOINT%/tiles?line=1,1,10,5,15,9"

The output of these requests is a list of tuples containing tile coordinates, e.g: [[16,8],[16,9],[17,8],[17,9]], in the JSON format. The first value in each tuple is the horizontal grid coordinate(h), i.e. along east-west and the second value is the vertical grid coordinate(v), i.e. along north-south.

### <a name="retrieve-controlled-keywords"></a> Retrieve Controlled Keywords

The keyword endpoint is used to retrieve the full list of keywords for each of the controlled vocabulary fields. The controlled vocabulary is cached within CMR, but the actual source is the GCMD Keyword Management System (KMS). Users of this endpoint are interested in knowing what the CMR considers as the current controlled vocabulary, since it is the cached CMR values that will eventually be enforced on CMR ingest.

The keywords are returned in a hierarchical JSON format. The response format is such that the caller does not need to know the hierarchy, but it can be inferred from the results. Keywords are not guaranteed to have values for every subfield in the hierarchy, so the response will indicate the next subfield below the current field in the hierarchy which has a value. It is possible for the keywords to have multiple potential subfields below it for different keywords with the same value for the current field in the hierarchy. When this occurs the subfields property will include each of the subfields.

Supported keywords include `platforms`, `instruments`, `projects`, `temporal_keywords`, `location_keywords`, `science_keywords`, `archive_centers`, `data_centers`, `granule-data-format`, `mime-type` and `measurement-name`. The endpoint also supports `providers` which is an alias to `data_centers` and `spatial_keywords` which is an alias to `location_keywords`.

    curl -i "%CMR-ENDPOINT%/keywords/instruments?pretty=true"

__Example Response__

```
{
  "category" : [ {
    "value" : "Earth Remote Sensing Instruments",
    "subfields" : [ "class" ],
    "class" : [ {
      "value" : "Active Remote Sensing",
      "subfields" : [ "type" ],
      "type" : [ {
        "value" : "Altimeters",
        "subfields" : [ "subtype" ],
        "subtype" : [ {
          "value" : "Lidar/Laser Altimeters",
          "subfields" : [ "short_name" ],
          "short_name" : [ {
            "value" : "ATM",
            "subfields" : [ "long_name" ],
            "long_name" : [ {
              "value" : "Airborne Topographic Mapper",
              "uuid" : "c2428a35-a87c-4ec7-aefd-13ff410b3271"
            } ]
          }, {
            "value" : "LVIS",
            "subfields" : [ "long_name" ],
            "long_name" : [ {
              "value" : "Land, Vegetation, and Ice Sensor",
              "uuid" : "aa338429-35e6-4ee2-821f-0eac81802689"
            } ]
          } ]
        } ]
      } ]
    }, {
      "value" : "Passive Remote Sensing",
      "subfields" : [ "type" ],
      "type" : [ {
        "value" : "Spectrometers/Radiometers",
        "subfields" : [ "subtype" ],
        "subtype" : [ {
          "value" : "Imaging Spectrometers/Radiometers",
          "subfields" : [ "short_name" ],
          "short_name" : [ {
            "value" : "SMAP L-BAND RADIOMETER",
            "subfields" : [ "long_name" ],
            "long_name" : [ {
              "value" : "SMAP L-Band Radiometer",
              "uuid" : "fee5e9e1-10f1-4f14-94bc-c287f8e2c209"
            } ]
          } ]
        } ]
      } ]
    } ]
  }, {
    "value" : "In Situ/Laboratory Instruments",
    "subfields" : [ "class" ],
    "class" : [ {
      "value" : "Chemical Meters/Analyzers",
      "subfields" : [ "short_name" ],
      "short_name" : [ {
        "value" : "ADS",
        "subfields" : [ "long_name" ],
        "long_name" : [ {
          "value" : "Automated DNA Sequencer",
          "uuid" : "554a3c73-3b48-43ea-bf5b-8b98bc2b11bc"
        } ]
      } ]
    } ]
  } ]
}
```

Note: Search parameter filtering are not supported - requests are rejected when there exist parameters other than pretty=true.

    curl -i "%CMR-ENDPOINT%/keywords/instruments?platform=TRIMM&pretty=true"

```
{
  "errors" : [ "Search parameter filters are not supported: [{:platform \"TRIMM\"}]" ]
}
```

### <a name="supported-result-formats"></a> Supported Result Formats

#### <a name="html"></a> HTML

The HTML response format is intended to be used only in a web browser to view a single collection record. The collection html representation can be viewed by putting the following in the web browser address bar.

```
%CMR-ENDPOINT%/concepts/<concept-id>
```
For private collection, an EDL bearer token or a Launchpad token can be used to grant permission. e.g.

```
%CMR-ENDPOINT%/concepts/<concept-id>?token=EDL-xxxxxx
```

#### <a name="atom"></a> Atom XML

See the [Atom specification](http://tools.ietf.org/html/rfc4287) for a full description of Atom.

The CMR Atom format provides search results in an XML file representing a feed of entries. The feed has the following fields:

|                         Atom Feed Level Feeds                         ||
|   Field    |                        Description                        |
| ---------- | --------------------------------------------------------- |
| id         | the URL linking to this feed                              |
| title      | Either 'ECHO dataset metadata' or 'ECHO granule metadata' |
| updated    | The date/time the search was executed                     |

 Each entry represents one search result, consisting of the following Atom standard fields and additional CMR specific fields:


|                                                           Atom Standard Fields                                                          ||
|            Field            |                                                Description                                                 |
| --------------------------- | ---------------------------------------------------------------------------------------------------------- |
| id                          | the CMR identifier for the result                                                                          |
| consortiums (collections only)             | Consortiums the collection is associated to.                                                |
| title                       | the UMM Entry Title                                                                                        |
| summary  (collections only) | the summary of intentions with which this collection was developed. - corresponds to the UMM summary field |
| updated                     | date/time of the last update to the associated metadata                                                     |

The following fields are specific to the CMR output and most correspond to ECHO10 fields of the same name.
Important to note, dataCenter corresponds to the provider id while organizations will contain the data center names:

|                                                                      CMR Specific Fields                                                                         ||
|                  Field                     |                                                     Description                                                      |
| ------------------------------------------ | -------------------------------------------------------------------------------------------------------------------- |
| echo:datasetId                             | UMM entry title of the collection                                                                                    |
| echo:shortName (collections only)          | provider defined short name of the collection                                                                        |
| echo:versionId (collections only)          | provider defined version id of the collection                                                                        |
| echo:collectionDataType (collections only) | type of the collection, e.g. Science Quality or Near Real Time                                                       |
| echo:producerGranuleId (granules only)     | producer granule id of the granule                                                                                   |
| echo:collectionConceptId (granules only)   | collection concept id of the granule                                                                                 |
| echo:granuleSizeMB (granules only)         | granule size in megabytes                                                                                            |
| echo:originalFormat                        | original metadata format                                                                                             |
| echo:dataCenter                            | provider id                                                                                   |
| echo:archiveCenter (collections only)      | archive center of the metadata                                                                                       |
| echo:organizations (collections only)      | organizations associated with the metadata, including data centers                                                                           |
| echo:processingLevelId (collections only)  | processing level id of the metadata                                                                                  |
| time:start                                 | start time of the metadata                                                                                           |
| time:end                                   | end time of the metadata                                                                                             |
| link                                       | online access and online resource urls associated with the metadata                                                  |
| echo:orbit (granules only)                 | orbit info of the metadata                                                                                           |
| echo:orbitCalSpatialDomain (granules only) | orbit calculated spatial domain info of the metadata                                                                  |
| echo:coordinateSystem                      | coordinate system info of the metadata                                                                               |
| echo:orbitParameters (collections only)    | fields related to the satellite orbit (startCircularLatitude, numberOfOrbits, inclinationAngle, period, swathWidth) |
| georss:point                               | spatial point info of the metadata                                                                                   |
| georss:line                                | spatial line info of the metadata                                                                                    |
| georss:box                                 | spatial bounding box info of the metadata                                                                            |
| georss:polygon                             | spatial polygon info of the metadata                                                                                 |
| echo:difId (collections only)              | associated dif id of the collection                                                                                  |
| echo:onlineAccessFlag                      | true if the data is available online                                                                                 |
| echo:browseFlag                            | true if the data contains browse imagery                                                                             |
| echo:hasGranules (collections only)        | true if there are granules associated with the collection                                                            |
| echo:granuleCount (collections only)       | granule count of the collection                                                                                      |
| echo:hasVariables (collections only)       | true if there are variables associated with the collection                                                           |
| echo:hasFormats (collections only)         | true if there are multiple supported formats for any services associated with the collection                         |
| echo:hasTransforms (collections only)      | true if there are transformations (subset, interpolation or projection) in any of its associated services            |
| echo:hasSpatialSubsetting (collections only)| true if any of its associated services support spatial subsetting            |
| echo:hasTemporalSubsetting (collections only)| true if any of its associated services support temporal subsetting            |
| echo:cloudHosted (collections only)        | true if the collection is hosted in the cloud            |
| relevance:score (collections only)         | relevance score of the collection to search parameters                                                               |
| echo:tag (collections only)                | tags associated with the collection. It includes sub-elements of tagKey and optional data which is in embedded JSON. |
| echo:dayNightFlag (granules only)          | day night flag of the granule                                                                                        |
| echo:cloudCover (granules only)            | cloud cover of the granule                                                                                           |

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns:os="http://a9.com/-/spec/opensearch/1.1/"
      xmlns:georss="http://www.georss.org/georss/10"
      xmlns="http://www.w3.org/2005/Atom"
      xmlns:dc="http://purl.org/dc/elements/1.1/"
      xmlns:echo="http://www.echo.nasa.gov/esip"
      xmlns:esipdiscovery="http://commons.esipfed.org/ns/discovery/1.2/"
      xmlns:gml="http://www.opengis.net/gml"
      xmlns:time="http://a9.com/-/opensearch/extensions/time/1.0/"
      esipdiscovery:version="1.2">
   <updated>2015-06-05T20:10:39.166Z</updated>
   <id>%CMR-ENDPOINT%/collections.atom?pretty=true</id>
   <title type="text">ECHO dataset metadata</title>
   <entry>
      <id>C1200000000-PROV1</id>
      <title type="text">dataset-id</title>
      <summary type="text">The AMSR-E/Aqua Level-3 5-day snow water equivalent (SWE) product includes global 5-day maximum SWE on Northern and Southern Hemisphere 25 km EASE-Grids, generated by the GSFC algorithm using Level-2A TBs.</summary>
      <updated>2010-10-06T11:45:39.530Z</updated>
      <echo:datasetId>dataset-id</echo:datasetId>
      <echo:shortName>short</echo:shortName>
      <echo:versionId>v1</echo:versionId>
      <echo:originalFormat>ECHO10</echo:originalFormat>
      <echo:dataCenter>PROV1</echo:dataCenter>
      <echo:orbitParameters/>
      <echo:onlineAccessFlag>false</echo:onlineAccessFlag>
      <echo:browseFlag>false</echo:browseFlag>
      <echo:hasVariables>true</echo:hasVariables>
      <echo:hasFormats>false</echo:hasFormats>
      <echo:hasTransforms>false</echo:hasTransforms>
      <echo:cloudHosted>true</echo:cloudHosted>
      <echo:tag>
        <echo:tagKey>tag1</echo:tagKey>
        <echo:data>{"status":"Reviewed","score":85}</echo:data>
      </echo:tag>
      <echo:tag>
        <echo:tagKey>tag2</echo:tagKey>
        <echo:data>"cloud cover &gt; 80"</echo:data>
      </echo:tag>
   </entry>
</feed>
```

#### <a name="csv"></a> CSV

The comma separated value (CSV) format is supported for collections and granules.
Version and Processing Level are strings. When importing into the spreadsheet, please specify text as their column types. This will prevent version "004" to be displayed as "4" in the spreadsheet.

__Examples__

Collection response
```csv
Data Provider,Short Name,Version,Entry Title,Processing Level,Platforms,Start Time,End Time
PROV1,short-name,V1,dataset-id,L1,platform #1,1970-01-01T12:00:00.000Z,
PROV2,very-short,v1,data-id,L3,platform2,2019-08-03T20:11:59.143Z,
```

Granule response
```csv
Granule UR,Producer Granule ID,Start Time,End Time,Online Access URLs,Browse URLs,Cloud Cover,Day/Night,Size
SC:SPL1AA.001:12345,SMAP_L1C_S0_HIRES_00016_A_20150530T160100_R03001_001.h5,,,,,,,
```

#### Metadata Responses (DIF, DIF 10, ECHO 10, ISO-SMAP, ISO-MENDS)

All of the XML Metadata formats (except the XML used in returning references only) have the same structure, differing only in the way each result is returned. These formats return a single XML document with a `<results>` XML element containing the following fields as sub-elements:

|         Field         |                    Description                     |
| --------------------- | -------------------------------------------------- |
| hits                  | the number of results matching the search query    |
| took                  | time in milliseconds it took to perform the search |
| result (zero or more) | a single search result - documented below          |

The results are returned as a sequence of `<result>` XML elements, the contents of which are documents in the specified format (DIF, ECHO 10 , etc.). If tags are included in the response a `<tags>` element will directly follow the metadata in the `<result>` element. Each `<result>` XML element contains the following attributes:

|  Attribute  |                  Description                  |
| ----------- | --------------------------------------------- |
| concept-id  | the CMR unique identifier for the concept     |
| format      | the mime-type for the returned metadata       |
| revision-id | the CMR revision number of the stored concept |

#### <a name="dif-9"></a> DIF 9

__NOTE:__ The DIF 9 format is being __deprecated__ in favor of DIF 10. While it is still supported, we strongly discourage its use for new metadata.

Mime-type application/dif+xml corresponds to the DIF 9 format. See the [specification](https://cdn.earthdata.nasa.gov/dif/9.x)

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>30</took>
    <result concept-id="C1200000000-PROV1" format="application/dif+xml" revision-id="1">
        <DIF xmlns="http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
            xmlns:dif="http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v9.9.3.xsd">
            <Entry_ID>short_v1</Entry_ID>
            <Entry_Title>dataset-id</Entry_Title>
            <Data_Set_Citation>
                <Version>v1</Version>
            </Data_Set_Citation>
            <Quality/>
            <Use_Constraints/>
            <Summary>
                <Abstract>The AMSR-E/Aqua Level-3 5-day snow water equivalent (SWE) product includes global 5-day maximum SWE on Northern and Southern Hemisphere 25 km EASE-Grids, generated by the GSFC algorithm using Level-2A TBs.</Abstract>
                <Purpose/>
            </Summary>
            <Metadata_Name>CEOS IDN DIF</Metadata_Name>
            <Metadata_Version>VERSION 9.9.3</Metadata_Version>
            <DIF_Creation_Date>2008-04-22T12:53:38.320Z</DIF_Creation_Date>
            <Last_DIF_Revision_Date>2010-10-06T11:45:39.530Z</Last_DIF_Revision_Date>
        </DIF>
        <tags>
          <tag>
            <tagKey>tag1</tagKey>
            <data>{"status":"Reviewed","score":85}</data>
          </tag>
          <tag>
            <tagKey>tag2</tagKey>
            <data>"cloud cover &gt; 80"</data>
          </tag>
        </tags>
    </result>
</results>
```

#### <a name="dif-10"></a> DIF 10

See the [specification](https://cdn.earthdata.nasa.gov/dif/10.x/) for details.

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>30</took>
    <result concept-id="C1200000000-PROV1"
        format="application/dif10+xml" revision-id="1">
        <DIF xmlns="http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/"
            xmlns:dif="http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
            <Entry_ID>short_v1</Entry_ID>
            <Version>v1</Version>
            <Entry_Title>dataset-id</Entry_Title>
            <Science_Keywords>
                <Category>Not provided</Category>
                <Topic>Not provided</Topic>
                <Term>Not provided</Term>
            </Science_Keywords>
            <Platform>
                <Type>Not provided</Type>
                <Short_Name>Not provided</Short_Name>
                <Instrument>
                    <Short_Name>Not provided</Short_Name>
                </Instrument>
            </Platform>
            <Spatial_Coverage>
                <Granule_Spatial_Representation>CARTESIAN</Granule_Spatial_Representation>
            </Spatial_Coverage>
            <Project>
                <Short_Name>Not provided</Short_Name>
            </Project>
            <Quality/>
            <Use_Constraints/>
            <Summary>
                <Abstract>The AMSR-E/Aqua Level-3 5-day snow water equivalent (SWE) product includes global 5-day maximum SWE on Northern and Southern Hemisphere 25 km EASE-Grids, generated by the GSFC algorithm using Level-2A TBs.</Abstract>
                <Purpose/>
            </Summary>
            <Related_URL>
                <URL>Not provided</URL>
            </Related_URL>
            <Metadata_Name>CEOS IDN DIF</Metadata_Name>
            <Metadata_Version>VERSION 10.1</Metadata_Version>
            <Metadata_Dates>
                <Metadata_Creation>2008-04-22T12:53:38.320Z</Metadata_Creation>
                <Metadata_Last_Revision>2010-10-06T11:45:39.530Z</Metadata_Last_Revision>
                <Data_Creation>1970-01-01T00:00:00</Data_Creation>
                <Data_Last_Revision>1970-01-01T00:00:00</Data_Last_Revision>
            </Metadata_Dates>
        </DIF>
        <tags>
          <tag>
            <tagKey>tag1</tagKey>
            <data>{"status":"Reviewed","score":85}</data>
          </tag>
          <tag>
            <tagKey>tag2</tagKey>
            <data>"cloud cover &gt; 80"</data>
          </tag>
        </tags>
    </result>
</results>
```

#### <a name="echo-10"></a> ECHO 10

See the [specification](https://cdn.earthdata.nasa.gov/echo/schemas/10.0/) for details.

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>17</took>
    <result concept-id="C1200000000-PROV1"
        format="application/echo10+xml" revision-id="1">
        <Collection>
            <ShortName>short</ShortName>
            <VersionId>v1</VersionId>
            <InsertTime>2008-04-22T12:53:38.320Z</InsertTime>
            <LastUpdate>2010-10-06T11:45:39.530Z</LastUpdate>
            <LongName>AMSR-E/Aqua 5-Day L3 Global Snow Water Equivalent EASE-Grids</LongName>
            <DataSetId>dataset-id</DataSetId>
            <Description>The AMSR-E/Aqua Level-3 5-day snow water equivalent (SWE) product includes global 5-day maximum SWE on Northern and Southern Hemisphere 25 km EASE-Grids, generated by the GSFC algorithm using Level-2A TBs.</Description>
            <Orderable>false</Orderable>
            <Visible>true</Visible>
        </Collection>
        <tags>
          <tag>
            <tagKey>tag1</tagKey>
            <data>{"status":"Reviewed","score":85}</data>
          </tag>
          <tag>
            <tagKey>tag2</tagKey>
            <data>"cloud cover &gt; 80"</data>
          </tag>
        </tags>
    </result>
</results>
```

#### <a name="iso-mends"></a> ISO-MENDS (ISO-19115-2)

See the [specification](https://cdn.earthdata.nasa.gov/iso/)

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>1373</took>
    <result concept-id="C1200000000-PROV1"
        format="application/iso19115+xml" revision-id="1">
        <gmi:MI_Metadata
            xmlns:eos="http://earthdata.nasa.gov/schema/eos"
            xmlns:gco="http://www.isotc211.org/2005/gco"
            xmlns:gmd="http://www.isotc211.org/2005/gmd"
            xmlns:gmi="http://www.isotc211.org/2005/gmi"
            xmlns:gml="http://www.opengis.net/gml/3.2"
            xmlns:gmx="http://www.isotc211.org/2005/gmx"
            xmlns:gsr="http://www.isotc211.org/2005/gsr"
            xmlns:gss="http://www.isotc211.org/2005/gss"
            xmlns:gts="http://www.isotc211.org/2005/gts"
            xmlns:srv="http://www.isotc211.org/2005/srv"
            xmlns:swe="http://schemas.opengis.net/sweCommon/2.0/"
            xmlns:xlink="http://www.w3.org/1999/xlink"
            xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
            <!--Other Properties, all:0, coi:0,ii:0,si:0,pli:0,pri:0,qi:0,gi:0,ci:0,dk:0,pcc:0,icc:0,scc:0-->
            <gmd:fileIdentifier>
                <gco:CharacterString>gov.nasa.echo:dataset-id</gco:CharacterString>
            </gmd:fileIdentifier>
            <gmd:language>
                <gco:CharacterString>eng</gco:CharacterString>
            </gmd:language>
            <gmd:characterSet>
                <gmd:MD_CharacterSetCode
                    codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode" codeListValue="utf8">utf8</gmd:MD_CharacterSetCode>
            </gmd:characterSet>
            <gmd:hierarchyLevel>
                <gmd:MD_ScopeCode
                    codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode" codeListValue="series">series</gmd:MD_ScopeCode>
            </gmd:hierarchyLevel>
            <gmd:contact gco:nilReason="missing"/>
            <gmd:dateStamp>
                <gco:DateTime>2015-06-05T16:17:30.386-04:00</gco:DateTime>
            </gmd:dateStamp>

            ...

            <gmd:dataQualityInfo>
                <gmd:DQ_DataQuality>
                    <gmd:scope>
                        <gmd:DQ_Scope>
                            <gmd:level>
                                <gmd:MD_ScopeCode
                                    codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode" codeListValue="series">series</gmd:MD_ScopeCode>
                            </gmd:level>
                        </gmd:DQ_Scope>
                    </gmd:scope>
                    <gmd:lineage>
                        <gmd:LI_Lineage>
                            <gmd:processStep>
                                <gmi:LE_ProcessStep>
                                    <gmd:description gco:nilReason="unknown"/>
                                </gmi:LE_ProcessStep>
                            </gmd:processStep>
                        </gmd:LI_Lineage>
                    </gmd:lineage>
                </gmd:DQ_DataQuality>
            </gmd:dataQualityInfo>
            <gmd:metadataMaintenance>
                <gmd:MD_MaintenanceInformation>
                    <gmd:maintenanceAndUpdateFrequency>
                        <gmd:MD_MaintenanceFrequencyCode
                            codeList="http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_MaintenanceFrequencyCode" codeListValue="irregular">irregular</gmd:MD_MaintenanceFrequencyCode>
                    </gmd:maintenanceAndUpdateFrequency>
                    <gmd:maintenanceNote>
                        <gco:CharacterString>Translated from ECHO using ECHOToISO.xsl Version: 1.31 (Nov. 3, 2014)</gco:CharacterString>
                    </gmd:maintenanceNote>
                </gmd:MD_MaintenanceInformation>
            </gmd:metadataMaintenance>
            <gmi:acquisitionInformation>
                <gmi:MI_AcquisitionInformation/>
            </gmi:acquisitionInformation>
        </gmi:MI_Metadata>
        <tags>
          <tag>
            <tagKey>tag1</tagKey>
            <data>{"status":"Reviewed","score":85}</data>
          </tag>
          <tag>
            <tagKey>tag2</tagKey>
            <data>"cloud cover &gt; 80"</data>
          </tag>
        </tags>
    </result>
</results>
```

#### <a name="iso-smap"></a> ISO-SMAP

See the [specification](https://cdn.earthdata.nasa.gov/iso/)

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>15</took>
    <result concept-id="C1200000000-PROV1"
        format="application/iso:smap+xml" revision-id="1">
        <gmd:DS_Series xmlns:gco="http://www.isotc211.org/2005/gco"
            xmlns:gmd="http://www.isotc211.org/2005/gmd"
            xmlns:gmi="http://www.isotc211.org/2005/gmi"
            xmlns:gml="http://www.opengis.net/gml/3.2"
            xmlns:gmx="http://www.isotc211.org/2005/gmx"
            xmlns:gsr="http://www.isotc211.org/2005/gsr"
            xmlns:gss="http://www.isotc211.org/2005/gss"
            xmlns:gts="http://www.isotc211.org/2005/gts"
            xmlns:xlink="http://www.w3.org/1999/xlink"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.isotc211.org/2005/gmd file:/Users/bweiss/Desktop/SMAP/Metadata/NASASchemaFolder/schema.xsd">
            <gmd:composedOf gco:nilReason="inapplicable"/>
            <gmd:seriesMetadata>
                <gmi:MI_Metadata
                    xmlns:srv="http://www.isotc211.org/2005/srv" xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <gmd:fileIdentifier>
                        <gco:CharacterString>L1A_Radar</gco:CharacterString>
                        <!-- Assume that the file Identifier for series metadata would be the identifier that denotes that a file belongs to this series. -->
                    </gmd:fileIdentifier>
                    <gmd:language>
                        <gco:CharacterString>eng</gco:CharacterString>
                    </gmd:language>
                    <gmd:characterSet>
                        <gmd:MD_CharacterSetCode
                            codeList="http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode" codeListValue="utf8">utf8</gmd:MD_CharacterSetCode>
                    </gmd:characterSet>

                    ...

                    <gmd:identificationInfo>
                        <gmd:MD_DataIdentification>
                            <gmd:citation>
                                <gmd:CI_Citation>
                                    <gmd:title>
                                    <gco:CharacterString>DIFID</gco:CharacterString>
                                    </gmd:title>
                                    <gmd:date>
                                    <gmd:CI_Date>
                                    <gmd:date>
                                    <gco:DateTime>2013-04-05T17:15:00Z</gco:DateTime>
                                    </gmd:date>
                                    <gmd:dateType>
                                    <gmd:CI_DateTypeCode
                                    codeList="http://www.isotc211.org/2005/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode" codeListValue="revision">revision</gmd:CI_DateTypeCode>
                                    </gmd:dateType>
                                    </gmd:CI_Date>
                                    </gmd:date>
                                    <gmd:identifier>
                                    <gmd:MD_Identifier>
                                    <gmd:code>
                                    <gco:CharacterString>A_DIF_ID</gco:CharacterString>
                                    </gmd:code>
                                    </gmd:MD_Identifier>
                                    </gmd:identifier>
                                </gmd:CI_Citation>
                            </gmd:citation>
                            <gmd:abstract>
                                <gco:CharacterString>DIFID</gco:CharacterString>
                            </gmd:abstract>
                            <gmd:purpose>
                                <gco:CharacterString>DIFID</gco:CharacterString>
                            </gmd:purpose>
                            <gmd:language>
                                <gco:CharacterString>eng</gco:CharacterString>
                            </gmd:language>
                        </gmd:MD_DataIdentification>
                    </gmd:identificationInfo>
                </gmi:MI_Metadata>
            </gmd:seriesMetadata>
        </gmd:DS_Series>
    </result>
</results>
```

#### <a name="json"></a> ATOM JSON

The JSON response contains all the fields in the *ATOM* response, plus the the following fields:

* associations - a map of the concept ids of concepts that are associated with the collection.
* association-details - a map of the concept ids, optional revision ids, and optional data of concepts that are associated with the collection.
* platforms - a list of platform short names of the collection.
* service_features - a map of service features for OPeNDAP, ESI and Harmony services.

__Example__

```json
{
  "feed": {
    "updated": "2015-06-05T17:52:10.316Z",
    "id": "%CMR-ENDPOINT%/collections.json?pretty=true",
    "title": "ECHO dataset metadata",
    "entry": [{
      "version_id": "v1",
      "updated": "2010-10-06T11:45:39.530Z",
      "dataset_id": "dataset-id",
      "data_center": "PROV1",
      "short_name": "short",
      "title": "dataset-id",
      "summary": "The AMSR-E/Aqua Level-3 5-day snow water equivalent (SWE) product includes global 5-day maximum SWE on Northern and Southern Hemisphere 25 km EASE-Grids, generated by the GSFC algorithm using Level-2A TBs.",
      "orbit_parameters": {},
      "id": "C1200000000-PROV1",
      "original_format": "ECHO10",
      "browse_flag": false,
      "has_variables": true,
      "has_formats": false,
      "has_transforms": false,
      "has_spatial_subsetting": false,
      "has_temporal_subsetting": false,
      "cloud_hosted": true,
      "online_access_flag": false,
      "platforms": ["Platform1"],
      "consortiums" : [ "ESA", "FEDEO" ],
      "service_features": {
        "opendap": {
          "has_formats": false,
          "has_variables": true,
          "has_transforms": true,
          "has_spatial_subsetting": true,
          "has_temporal_subsetting": true
        },
        "esi": {
          "has_formats": true,
          "has_variables": false,
          "has_transforms": true,
          "has_spatial_subsetting": true,
          "has_temporal_subsetting": true
        },
        "harmony": {
          "has_formats": true,
          "has_variables": true,
          "has_transforms": false,
          "has_spatial_subsetting": false,
          "has_temporal_subsetting": false
        }
      },
      "tags": {
        "tag1": {
          "data": {
            "score": 85,
            "status": "reviewed"
          }
        },
        "tag2": {
          "data": "cloud cover > 80"
        }
      },
      "associations": {
        "variables": ["V1200000007-PROV1"],
        "services": ["S1200000008-PROV1", "S1200000009-PROV1","S1200000010-PROV1"],
        "tools": ["TL1200000011-PROV1", "TL1200000012-PROV1","TL1200000013-PROV1"]
      },
      "association-details": {
        "variables": [{"concept-id": "V1200000007-PROV1"}],
        "services": [{"concept-id": "S1200000008-PROV1",
                      "data": {"formatting-type": "zarr",
                               "regridding-type": {"xyz": "zyx"}}},
                     {"concept-id": "S1200000009-PROV1"},
                     {"concept-id": "S1200000010-PROV1"}],
        "tools": [{"concept-id": "TL1200000011-PROV1"},
                  {"concept-id": "TL1200000012-PROV1"},
                  {"concept-id": "TL1200000013-PROV1"}]
      }
    }]
  }
}
```

#### <a name="umm-json"></a> UMM JSON

The UMM JSON response, unlike ATOM JSON, contains "meta-metadata" of the collection and the UMM fields. The UMM JSON format is applicable to collection, granule, variable, service and tool searches. The UMM-JSON response is helpful if you wish to get the native-id of a concept after ingesting it. The version of the UMM returned will be the version requested or the latest version. Clients are recommended to always specify a version to avoid breaking changes in UMM.

This format can be retrieved in a variety of methods:

* A url extension with a version: `%CMR-ENDPOINT%/collections.umm_json_v%COLLECTION_UMM_VERSION%`
  * Note extension `.umm-json` still returns the original alpha version of this response to avoid breaking clients. This will change in the future.
* An accept header: `application/vnd.nasa.cmr.umm_results+json; version=%COLLECTION-UMM-VERSION%`
   * The version is optional but recommended.

__Example__

```json
{
  "hits" : 2,
  "took" : 11,
  "items" : [ {
    "meta" : {
      "revision-id" : 3,
      "deleted" : false,
      "format" : "application/echo10+xml",
      "provider-id" : "PROV1",
      "native-id" : "et1",
      "concept-id" : "C1200000000-PROV1",
      "revision-date" : "2016-07-27T12:00:17Z",
      "concept-type" : "collection",
      "has-variables" : true,
      "has-formats" : false,
      "has-transforms" : false,
      "has-spatial-subsetting" : false,
      "has-temporal-subsetting" : false,
      "associations" : {
        "variables" : [ "V1200000007-PROV1" ],
        "services" : [ "S1200000008-PROV1", "S1200000009-PROV1" ]
        "tools" : [ "TL1200000010-PROV1", "TL1200000011-PROV1" ]
      }
    },
    "umm" : {
      "SpatialExtent" : {
        "HorizontalSpatialDomain" : {
          "Geometry" : {
            "CoordinateSystem" : "GEODETIC",
            "Points" : [ {
              "Longitude" : 0.0,
              "Latitude" : 90.0
            } ]
          }
        },
        "GranuleSpatialRepresentation" : "GEODETIC"
      },
      "ScienceKeywords" : [ {
        "Category" : "Cat1",
        "Topic" : "Topic1",
        "Term" : "Term1"
      } ],
      "TemporalExtents" : [ {
        "RangeDateTimes" : [ {
          "BeginningDateTime" : "2000-01-01T00:00:00.000Z"
        } ]
      } ],
      "ProcessingLevel" : {
        "Id" : "Level 1"
      },
      "ShortName" : "s1",
      "EntryTitle" : "et1",
      "RelatedUrls" : [ {
        "Description" : "description648",
        "URLContentType" : "DistributionURL",
        "Type" : "GET DATA" ,
        "URL" : "http://example.com/file649"
      } ],
      "DataDates" : [ {
        "Date" : "2012-01-11T10:00:00.000Z",
        "Type" : "CREATE"
      }, {
        "Date" : "2012-01-19T18:00:00.000Z",
        "Type" : "UPDATE"
      } ],
      "Abstract" : "long-name651",
      "Version" : "v2",
      "DataCenters" : [ {
        "Roles" : [ "ARCHIVER" ],
        "ShortName" : "Not provided"
      } ],
      "Platforms" : [ {
        "Type" : "Type647",
        "ShortName" : "platform",
        "LongName" : "long-name646"
      } ]
    }
  }, {
    "meta" : {
      "native-id" : "et3",
      "provider-id" : "PROV2",
      "concept-type" : "collection",
      "concept-id" : "C1200000002-PROV2",
      "revision-date" : "2016-07-27T12:00:17Z",
      "user-id" : "user3",
      "deleted" : false,
      "revision-id" : 1,
      "format" : "application/echo10+xml",
      "has-variables" : false,
      "has-formats" : false,
      "has-transforms" : false,
      "has-spatial-subsetting" : false,
      "has-temporal-subsetting" : false
    },
    "umm" : {
      "..."
    }
  } ]
}
```

When retrieving data in UMM JSON that has a native format other than UMM JSON, if there is an error parsing an individual field an "\_errors" field will be added to the UMM with details about the parsing error.

__Example__

```json
{ "Projects" : [ {
    "ShortName" : "Project2 Short Name",
    "LongName" : "Project2 Long Name",
    "Campaigns" : [ "Project 2 Campaign1 Short Name", "Project 2 Campaign2 Short Name" ],
    "_errors" : {
      "StartDate" : "Could not parse date-time value: 2002:03:01T01:00:00Z",
      "EndDate" : "Could not parse date-time value: 2002:04:02T01:00:00Z"
    }
  } ] }
```

A collection containing "\_errors" is not valid UMM and cannot be ingested into the CMR.

#### <a name="kml"></a> KML

KML is the [XML language](http://www.opengeospatial.org/standards/kml) used by the Google Earth application and is used by the CMR to return spatial data associated with a collection or granule.

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
   <Document>
      <Style id="geodetic_style">
         <LineStyle>
            <color>ffffffff</color>
            <colorMode>random</colorMode>
            <width>2</width>
         </LineStyle>
         <IconStyle>
            <color>ffffffff</color>
            <colorMode>random</colorMode>
            <scale>3.0</scale>
         </IconStyle>
         <PolyStyle>
            <fill>0</fill>
         </PolyStyle>
      </Style>
      <Style id="cartesian_style">
         <LineStyle>
            <color>ffffffff</color>
            <colorMode>random</colorMode>
            <width>2</width>
         </LineStyle>
         <IconStyle>
            <color>ffffffff</color>
            <colorMode>random</colorMode>
            <scale>3.0</scale>
         </IconStyle>
         <PolyStyle>
            <color>00ffffff</color>
         </PolyStyle>
      </Style>
      <Placemark>
         <name>SC:GLAH06.034:52592022</name>
         <styleUrl>#geodetic_style</styleUrl>
         <Polygon>
            <tessellate>1</tessellate>
            <outerBoundaryIs>
               <LinearRing>
                  <coordinates>54.78656326403332,-50.09193846025951 51.20574574139643,-31.559024005924392 48.4203450238013,-12.998336645829609 45.85491943219788,5.57000444208618 43.19937665770624,24.135158655763025 40.044971936186634,42.68280248541986 38.50355532538929,49.84326030061438 38.53129884465536,49.845207013547224 40.06934290325555,42.68451022431186 43.21903945805716,24.13653428810218 45.87295686415376,5.571265771529057 48.438767344855485,-12.997048263813937 51.22679482714953,-31.55755075104193 54.81444766176902,-50.0899817427687 54.78656326403332,-50.09193846025951</coordinates>
               </LinearRing>
            </outerBoundaryIs>
         </Polygon>
      </Placemark>
   </Document>
</kml>
```

#### <a name="open-data"></a> Open Data

The Open Data format was developed as part of [Project Open Data](https://project-open-data.cio.gov) in an attempt to make data more accessible. See the Open Data [schema](https://project-open-data.cio.gov/v1.1/schema/) for details.

__Example__

```json
{
  "conformsTo" : "https://project-open-data.cio.gov/v1.1/schema",
  "dataset" : [ {
    "description" : "The AMSR-E/Aqua Level-3 5-day snow water equivalent (SWE) product includes global 5-day maximum SWE on Northern and Southern Hemisphere 25 km EASE-Grids, generated by the GSFC algorithm using Level-2A TBs.",
    "accessLevel" : "public",
    "bureauCode" : [ "026:00" ],
    "publisher" : {
      "name" : null,
      "subOrganizationOf" : {
        "name" : "National Aeronautics and Space Administration",
        "subOrganizationOf" : {
          "name" : "U.S. Government"
        }
      }
    },
    "contactPoint" : {
      "fn" : "undefined",
      "hasEmail" : "mailto:support@earthdata.nasa.gov"
    },
    "modified" : "2010-10-06T11:45:39.530Z",
    "title" : "dataset-id",
    "theme" : [ "geospatial" ],
    "keyword" : [ "National Geospatial Data Asset", "NGDA" ],
    "language" : [ "en-US" ],
    "programCode" : [ "026:001" ],
    "identifier" : "C1200000000-PROV1",
    "issued" : "2008-04-22T12:53:38.320Z"
  } ]
}
```

#### <a name="stac"></a> STAC

The STAC (SpatioTemporal Asset Catalog) result format is a specification for describing geospatial data with JSON and GeoJSON. The related STAC-API specification defines an API for searching and browsing STAC catalogs. See the [STAC Specification](https://stacspec.org/) for details.

CMR supports STAC result format for collection/granule retrieval and granule searches. Because STAC search is based on paging by page number, parameters `offset`, `scroll`, and `CMR-Scroll-Id` and `CMR-Search-After` headers are not supported with STAC format. The maximum number of results returned in STAC result format is 1 million.

__Granule Retrieval in STAC Format Response Example via GET__

```json
{
  "type": "FeatureCollection",
  "stac_version": "1.0.0",
  "numberReturned": 1,
  "numberMatched": 3,
  "features": [
    {
      "type": "Feature",
      "stac_version": "1.0.0",
      "id": "G1200000011-PROV1",
      "collection": "C1200000009-PROV1",
      "stac_extensions": [
        "https://stac-extensions.github.io/eo/v1.0.0/schema.json"
      ],
      "geometry": {
        "type": "Polygon",
        "coordinates": [
          [
            [ 10, 0 ],
            [ 20, 0 ],
            [ 20, 30 ],
            [ 10, 30 ],
            [ 10, 0 ]
          ]
        ]
      },
      "properties": {
        "eo:cloud_cover": 20,
        "start_datetime": "2011-02-01T12:00:00.000Z",
        "end_datetime": "2011-02-11T12:00:00.000Z",
        "datetime": "2011-02-01T12:00:00.000Z"
      },
      "bbox": [ 10, 0, 20, 30 ],
      "links": [
        {
          "rel": "self",
          "href": "%CMR-ENDPOINT%/concepts/G1200000011-PROV1.stac"
        },
        {
          "rel": "parent",
          "href": "%CMR-ENDPOINT%/concepts/C1200000009-PROV1.stac"
        },
        {
          "rel": "collection",
          "href": "%CMR-ENDPOINT%/concepts/C1200000009-PROV1.stac"
        },
        {
          "rel": "root",
          "href": "%CMR-ENDPOINT%/"
        },
        {
          "rel": "via",
          "href": "%CMR-ENDPOINT%/concepts/G1200000011-PROV1.json"
        },
        {
          "rel": "via",
          "href": "%CMR-ENDPOINT%/concepts/G1200000011-PROV1.umm_json"
        }
      ],
      "assets": {
        "metadata": {
          "type": "application/xml",
          "href": "%CMR-ENDPOINT%/concepts/G1200000011-PROV1.xml"
        }
      }
    }
  ],
  "links": [
    {
      "rel": "self",
      "href": "%CMR-ENDPOINT%/granules.stac?collection-concept-id=C1200000009-PROV1&page_size=1&page_num=2"
    },
    {
      "rel": "root",
      "href": "%CMR-ENDPOINT%/"
    },
    {
      "rel": "prev",
      "method": "GET",
      "href": "%CMR-ENDPOINT%/granules.stac?collection-concept-id=C1200000009-PROV1&page_size=1&page_num=1"
    },
    {
      "rel": "next",
      "method": "GET",
      "href": "%CMR-ENDPOINT%/granules.stac?collection-concept-id=C1200000009-PROV1&page_size=1&page_num=3"
    }
  ],
  "context": {
    "limit": 1000000,
    "returned": 1,
    "matched": 3
  }
}
```

__Granule Retrieval in STAC Format Response Example via POST__

```json
{
  "type" : "FeatureCollection",
  "stac_version" : "1.0.0",
  "numberMatched" : 3,
  "numberReturned" : 1,
  "features" : [ {
    "properties" : {
      "datetime" : "2011-02-01T12:00:00.000Z",
      "start_datetime" : "2011-02-01T12:00:00.000Z",
      "end_datetime" : "2011-02-11T12:00:00.000Z",
      "eo:cloud_cover" : 20.0
    },
    "stac_extensions" : [ "https://stac-extensions.github.io/eo/v1.0.0/schema.json" ],
    "collection" : "C1200000009-PROV1",
    "id" : "G1200000011-PROV1",
    "geometry" : {
      "type" : "Polygon",
      "coordinates" : [ [ [ 10.0, 0.0 ], [ 20.0, 0.0 ], [ 20.0, 30.0 ], [ 10.0, 30.0 ], [ 10.0, 0.0 ] ] ]
    },
    "bbox" : [ 10.0, 0.0, 20.0, 30.0 ],
    "links" : [ {
      "rel" : "self",
      "href" : "http://localhost:3003/concepts/G1200000011-PROV1.stac"
    }, {
      "rel" : "parent",
      "href" : "http://localhost:3003/concepts/C1200000009-PROV1.stac"
    }, {
      "rel" : "collection",
      "href" : "http://localhost:3003/concepts/C1200000009-PROV1.stac"
    }, {
      "rel" : "root",
      "href" : "http://localhost:3003/"
    }, {
      "rel" : "via",
      "href" : "http://localhost:3003/concepts/G1200000011-PROV1.json"
    }, {
      "rel" : "via",
      "href" : "http://localhost:3003/concepts/G1200000011-PROV1.umm_json"
    } ],
    "stac_version" : "1.0.0",
    "type" : "Feature",
    "assets" : {
      "metadata" : {
        "href" : "http://localhost:3003/concepts/G1200000011-PROV1.xml",
        "type" : "application/xml"
      }
    }
  } ],
  "links" : [ {
    "rel" : "self",
    "href" : "http://localhost:3003/granules.stac?collection_concept_id=C1200000009-PROV1&pretty=true&page_num=2"
  }, {
    "rel" : "root",
    "href" : "http://localhost:3003/"
  }, {
    "rel" : "prev",
    "body" : {
      "collection_concept_id" : "C1200000009-PROV1",
      "page_num" : "1",
      "page_size" : "1",
      "pretty" : "true",
      "provider" : "PROV1"
    },
    "method" : "POST",
    "merge" : true,
    "href" : "http://localhost:3003/granules.stac"
  }, {
    "rel" : "next",
    "body" : {
      "collection_concept_id" : "C1200000009-PROV1",
      "page_num" : "3",
      "page_size" : "1",
      "pretty" : "true",
      "provider" : "PROV1"
    },
    "method" : "POST",
    "merge" : true,
    "href" : "http://localhost:3003/granules.stac"
  } ],
  "context" : {
    "returned" : 1,
    "limit" : 1000000,
    "matched" : 3
  }
}
```
#### <a name="xml-reference"></a> XML

The XML response format is used for returning references to search results. It consists of the following fields:

|   Field    |                    Description                     |
| ---------- | -------------------------------------------------- |
| hits       | the number of results matching the search query    |
| took       | time in milliseconds it took to perform the search |
| references | identifying information about each search result    |

The `references` field may contain multiple `reference` entries, each consisting of the following fields:

|    Field    |                                                   Description                                                   |
| ----------- | --------------------------------------------------------------------------------------------------------------- |
| name        | the provider's unique identifier for the item. This is Granule UR for granules and Entry Title for collections. |
| id          | the CMR identifier for the result                                                                               |
| location    | the URL at which the full metadata for the result can be retrieved                                              |
| revision-id | the internal CMR version number for the result                                                                  |

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<results>
   <hits>1</hits>
   <took>9</took>
   <references>
      <reference>
         <name>dataset-id</name>
         <id>C1200000000-PROV1</id>
         <location>%CMR-ENDPOINT%/concepts/C1200000000-PROV1</location>
         <revision-id>1</revision-id>
      </reference>
   </references>
</results>
```

By passing `echo_compatible=true` in the URL parameters the output can be forced to a format that is compatible with the ECHO search response:

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<references type="array">
   <reference>
      <name>SMAP Collection Dataset ID</name>
      <id>C1200000000-PROV1</id>
      <location>%CMR-ENDPOINT%/concepts/C1200000000-PROV1</location>
   </reference>
</references>
```