## API Documentation

### General Request Details

#### Maximum URL Length

The Maximum URL Length supported by CMR is indirectly controlled by the Request Header Size setting in Jetty which is set to 1MB. This translates to roughly 500k characters. Clients using the Search API with query parameters should be careful not to exceed this limit or they will get an HTTP response of 413 FULL HEAD. If a client expects that the query url could be extra long so that it exceeds 500k characters, they should use the POST API for searching.

#### CORS Header support

The CORS headers are supported on search endpoints. Check [CORS Documentation](https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS) for an explanation of CORS headers.

#### Query Parameters

 * `page_size` - number of results per page - default is 10, max is 2000
 * `page_num` - The page number to return
 * `sort_key` - Indicates one or more fields to sort on. Described below
 * `pretty` - return formatted results if set to true
 * `token` - specifies a user/guest token from ECHO to use to authenticate yourself. This can also be specified as the header Echo-Token
 * `echo_compatible` - When set to true results will be returned in an ECHO compatible format. This mostly removes fields and features specific to the CMR such as revision id, granule counts and facets in collection results. Metadata format style results will also use ECHO style names for concept ids such as `echo_granule_id` and `echo_dataset_id`.

#### Parameter Options

The behavior of search with respect to each parameter can be modified using the `options` parameter. The `options` parameter takes the following form:

  `options[parameter][option_key]=value`

where parameter is the URL parameter whose behavior is to be affected, value is either `true` or `false`, and `option_key` is one of the following:

 * `ignore_case` - if set to true, the search will be case insensitive and if set to false, the search will be case sensitive. Defaults to true.
 * `pattern` - if set to true, the search will treat the value provided for the parameter as a pattern with wild-cards, in which '*' matches zero or more characters and '?' matches any single character. For example, `platform[]=AB?D*&options[platform][pattern]=true` would match 'ABAD123', 'ABCD12', 'ABeD', etc. Defaults to false.
 * `and` - if set to true and if multiple values are listed for the param, the concepts must have ALL of these values in order to match. The default is `false` which means concepts with ANY of the values match. This option only applies to fields which may be multivalued; these are documented here.
 * `or` - this option only applies to granule attributes or science-keywords searches. If set to true, attribute searches will find granules that match any of the attributes. The default is false.

##### Collection Query Parameters

These are query parameters specific to collections

  * `include_has_granules` - If this parameter is set to "true" this will include a flag indicating true or false if the collection has any granules at all. Supported in all response formats except opendata.
  * `include_granule_counts` - If this parameter is set to "true" this will include a count of the granules in each collection that would match the spatial and temporal conditions from the collection query. Supported in all response formats except opendata.
  * `include_facets` - If this parameter is set to "true" facets will be included in the collection results (not applicable to opendata results). Facets are described in detail below.
  * `hierarchical_facets` - If this parameter is set to "true" and the parameter `include_facets` is set to "true" the facets that are returned will be hierarchical. Hierarchical facets are described in the facets section below.
  * `include_highlights` - If this parameter is set to "true", the collection results will contain an additional field, 'highlighted_summary_snippets'. The field is an array of strings which contain a snippet of the summary which highlight any terms which match the terms provided in the keyword portion of a search. By default up to 5 snippets may be returned with each individual snippet being up to 100 characters, and keywords in the snippets are delineated with begin tag `<em>` and end tag `</em>`. This is configurable using `options[highlights][param]=value`. Supported option params are `begin_tag`, `end_tag`, `snippet_length` and `num_snippets`. The values for `snippet_length` and `num_snippets` must be integers greater than 0.

  _There is a known bug with the `snippet_length` parameter that occasionally leads to snippets that are longer than `snippet_length` characters._

  _The `include_highlights` feature is only supported for the JSON response format and only applies to keyword searches._


#### Headers

  * Accept - specifies the MimeType to return search results in. Default is "application/xml".
    * `curl -H "Accept: application/xml" -i "%CMR-ENDPOINT%/collections"`
  * `Echo-Token` - specifies an ECHO token to use to authenticate yourself.
  * `Client-Id` - Indicates a name for the client using the CMR API. Specifying this helps Operations monitor query performance per client. It can also make it easier for them to identify your requests if you contact them for assistance.

  * The response headers include CMR-Hits and CMR-Took which indicate the number of result hits
     and the time to build and execute the query, respectively.

#### Extensions

Besides MimeTypes, client can also use extensions to specify the format for search results. Default is xml.

  * `curl -i "%CMR-ENDPOINT%/collections"`
  * `curl -i "%CMR-ENDPOINT%/collections.json"`
  * `curl -i "%CMR-ENDPOINT%/collections.echo10"`
  * `curl -i "%CMR-ENDPOINT%/collections.iso_mends"`

Here is a list of supported extensions and their corresponding MimeTypes:

  * `json`      "application/json"
  * `xml`       "application/xml"
  * `echo10`    "application/echo10+xml"
  * `iso`       "application/iso19115+xml"
  * `iso19115`  "application/iso19115+xml"
  * `dif`       "application/dif+xml"
  * `dif10`     "application/dif10+xml"
  * `csv`       "text/csv"
  * `atom`      "application/atom+xml"
  * `opendata`  "application/opendata+json" (only supported for collections)
  * `kml`       "application/vnd.google-earth.kml+xml"
  * `native`    "application/metadata+xml" (Returns search results in their individual native formats)
  * `umm-json`   "application/umm+json" (only supported for collections)

### Supported Result Formats

#### Atom

See the [Atom specification](http://tools.ietf.org/html/rfc4287) for a full description of Atom.

The CMR Atom format provides search results in an XML file representing a feed of entries. The feed has the following fields:

|                         Atom Feed Level Feeds                         ||
|   Field    |                        Description                        |
| ---------- | --------------------------------------------------------- |
| id         | the URL linking to this feed                               |
| title      | Either 'ECHO dataset metadata' or 'ECHO granule metadata' |
| updated    | The date/time the search was executed                     |

 Each entry represents one search result, consisting of the following Atom standard fields and additional CMR specific fields:


|                                                           Atom Standard Fields                                                          ||
|            Field            |                                                Description                                                 |
| --------------------------- | ---------------------------------------------------------------------------------------------------------- |
| id                          | the CMR identifier for the result                                                                          |
| title                       | the UMM Entry Title                                                                                        |
| summary  (collections only) | the summary of intentions with which this collection was developed. - corresponds to the UMM summary field |
| updated                     | date/time of the last update to the assocated metadata                                                     |

The following fields are specific to the CMR output and correspond to ECHO10 fields of the same name:

|                                                                      CMR Specific Fields                                                                      ||
|                  Field                  |                                                     Description                                                      |
| --------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| echo:datasetId                          | description of the dataset                                                                                           |
| echo:shortName (collections only)       | UMM Entry Title                                                                                                      |
| echo:versionId  (collections only)      | provider defined version id of the metadata                                                                          |
| echo:originalFormat                     | original metadata format                                                                                             |
| echo:dataCenter                         | datacenter providing the metadata                                                                                    |
| echo:orbitParameters (collections only) | fields releated to the satellite orbit (startCircularLatitude, numberOfOrbits, inclinationAngle, period, swathWidth) |
| echo:onlineAccessFlag                   | true if the data is available online                                                                                 |
| echo:browseFlag                         | true if the data contains browse imagery                                                                             |

__Example__

```xml
<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns:os="http://a9.com/-/spec/opensearch/1.1/"
      xmlns:georss="http://www.georss.org/georss/10"
      xmlns="http://www.w3.org/2005/Atom"
      xmlns:dc="http://purl.org/dc/terms/"
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
   </entry>
</feed>
```

#### CSV

The comma separated value (CSV) format is only supported for granules.

__Example__

```csv
Granule UR,Producer Granule ID,Start Time,End Time,Online Access URLs,Browse URLs,Cloud Cover,Day/Night,Size
SC:SPL1AA.001:12345,SMAP_L1C_S0_HIRES_00016_A_20150530T160100_R03001_001.h5,,,,,,,
```

#### Metadata Responses (DIF, DIF 10, ECHO 10, ISO-SMAP, ISO-MENDS)

All of the XML Metadata formats (except the XML used in returning references only) have the same structure, differing only in the way each result is returned. These formats return a single XML document with a `<results>` tag containing the following fields as sub-tags:

|         Field         |                    Description                     |
| --------------------- | -------------------------------------------------- |
| hits                  | the number of results matching the search query    |
| took                  | time in milliseconds it took to perform the search |
| result (zero or more) | a single search result - documented below          |

The results are returned as a seqeuence of `<result>` tags, the contents of which are documents in the specified format (DIF, ECHO 10 , etc.). Each `<result>` tag contains the following attributes:

|  Attribute  |                  Description                  |
| ----------- | --------------------------------------------- |
| concept-id  | the CMR unique identifier for the concept     |
| format      | the mime-type for the returned metadata       |
| revision-id | the CMR revision number of the stored concept |

#### DIF

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
    </result>
</results>
```

#### DIF 10

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
            <Product_Flag>Not provided</Product_Flag>
        </DIF>
    </result>
</results>
```

#### ECHO 10

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
    </result>
</results>
```

#### ISO-MENDS (ISO-19115)

See the [specification](https://www.iso.org/obp/ui/#iso:std:iso:19115:-2:ed-1:v1:en)

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
    </result>
</results>
```

#### ISO-SMAP (ISO-19115)

See the [specification](https://cdn.earthdata.nasa.gov/iso/schema/1.0/)

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

#### JSON

The JSON response contains the same fields as the ATOM response, only in JSON format instead of XML.

__Example__

```json
{
  "feed" : {
    "updated" : "2015-06-05T17:52:10.316Z",
    "id" : "%CMR-ENDPOINT%/collections.json?pretty=true",
    "title" : "ECHO dataset metadata",
    "entry" : [ {
      "version_id" : "v1",
      "updated" : "2010-10-06T11:45:39.530Z",
      "dataset_id" : "dataset-id",
      "data_center" : "PROV1",
      "short_name" : "short",
      "title" : "dataset-id",
      "summary" : "The AMSR-E/Aqua Level-3 5-day snow water equivalent (SWE) product includes global 5-day maximum SWE on Northern and Southern Hemisphere 25 km EASE-Grids, generated by the GSFC algorithm using Level-2A TBs.",
      "orbit_parameters" : { },
      "id" : "C1200000000-PROV1",
      "original_format" : "ECHO10",
      "browse_flag" : false,
      "online_access_flag" : false
    } ]
  }
}
```

#### UMM JSON

The JSON response contains meta-metadata of the collection and its UMM fields. The UMM JSON format is only applicable to collection searches. It is a beta feature and subject to change in the future.

__Example__

```json
{
  "hits": 2,
  "took": 4,
  "items": [{
    "meta": {
      "concept-id": "C1200000000-PROV1",
      "concept-type": "collection",
      "deleted": false,
      "format": "application/echo10+xml",
      "native-id": "et1",
      "provider-id": "PROV1",
      "revision-date": "2015-08-06T12:50:49Z",
      "revision-id": 3
    },
    "umm": {
      "entry-id": "s1_v2",
      "entry-title": "et1",
      "short-name": "s1",
      "version-id": "v2"
    }
  }, {
    "meta": {
      "concept-id": "C1200000002-PROV2",
      "concept-type": "collection",
      "deleted": false,
      "format": "application/echo10+xml",
      "native-id": "et3",
      "provider-id": "PROV2",
      "revision-date": "2015-08-06T12:50:49Z",
      "revision-id": 1
    },
    "umm": {
      "entry-id": "s1_v4",
      "entry-title": "et3",
      "short-name": "s1",
      "version-id": "v4"
    }
  }]
}

```

#### KML

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

#### Open Data

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

#### XML

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

### <a name="temporal-range-searches"></a> Temporal Range searches

A couple of parameters used in search expect a date range as input. For example, the parameter "temporal" used in collection and granule searches and the parameter "equator_crossing_longitude" used in granule searches both accept date ranges. All these parameters expect temporal ranges in the same format. The temporal ranges can be specified as a pair of date-time values separated by comma(,). Exactly one of the two bounds of the interval can be omitted. In addition to comma seperated values, one can also specify temporal ranges as [ISO 8601 time intervals](https://en.wikipedia.org/?title=ISO_8601#Time_intervals). Some examples of valid temporal range values are:

`2000-01-01T10:00:00Z,2010-03-10T12:00:00Z` - matches data between `2000-01-01T10:00:00Z` and `2010-03-10T12:00:00Z`
`,2010-03-10T12:00:00Z` - matches data before `2010-03-10T12:00:00Z`
`2000-01-01T10:00:00Z,` - matches data after `2010-03-10T12:00:00Z`
`2000-01-01T10:00:00Z/2010-03-10T12:00:00Z` - matches data between `2000-01-01T10:00:00Z` and `2010-03-10T12:00:00Z`
`2000-01-01T10:00:00Z/` - matches data after `2010-03-10T12:00:00Z`
`/2010-03-10T12:00:00Z` - matches data before `2010-03-10T12:00:00Z`
`2000-01-01T10:00:00Z/P10Y2M10DT2H` - matches data between `2000-01-01T10:00:00Z` and a date 10 years 2 months 10 days and 2 hours after that or `2010-03-11T02:00:00Z`
`P1Y2M10DT2H30M/2008-05-11T15:30:00Z` - matches data between `2008-07-11T16:30:00Z` and a date 1 year 2 months 10 days 2 hours and 30 minutes before that or `2007-05-01T14:00:00Z`.

Note: ISO 8601 does not allow open-ended time intervals but the CMR API does allow specification of intervals which are open ended on one side. For example, `2000-01-01T10:00:00Z/` and `/2000-01-01T10:00:00Z` are valid ranges.

### Collection Search Examples

#### Find all collections

    curl "%CMR-ENDPOINT%/collections"

#### Find collections by concept id

A CMR concept id is in the format `<concept-type-prefix> <unique-number> "-" <provider-id>`

  * `concept-type-prefix` is a single capital letter prefix indicating the concept type. "C" is used for collections
  * `unique-number` is a single number assigned by the CMR during ingest.
  * `provider-id` is the short name for the provider. i.e. "LPDAAC\_ECS"

Example: `C123456-LPDAAC_ECS`

    curl "%CMR-ENDPOINT%/collections?concept_id\[\]=C123456-LPDAAC_ECS"

#### Find collections by echo collection id

  Find a collection matching a echo collection id. Note more than one echo collection id may be supplied.

     curl "%CMR-ENDPOINT%/collections?echo_collection_id\[\]=C1000000001-CMR_PROV2"

#### Find collections by provider short name

This searches for collections whose provider matches the given provider short names. This supports `ignore_case` option, but not the `pattern` option.

    curl "%CMR-ENDPOINT%/collections?provider_short_name\[\]=SHORT_5&options\[provider_short_name\]\[ignore_case\]=true"

#### Find collections by entry title

One entry title

    curl "%CMR-ENDPOINT%/collections?entry_title\[\]=DatasetId%204"

a dataset id (alias for entry title)

    curl "%CMR-ENDPOINT%/collections?dataset_id\[\]=DatasetId%204"

with multiple dataset ids

    curl "%CMR-ENDPOINT%/collections?entry_title\[\]=DatasetId%204&entry_title\[\]=DatasetId%205"

with a entry title case insensitively

    curl "%CMR-ENDPOINT%/collections?entry_title\[\]=datasetId%204&options\[entry_title\]\[ignore_case\]=true"

with a entry title pattern

    curl "%CMR-ENDPOINT%/collections?entry_title\[\]=DatasetId*&options\[entry_title\]\[pattern\]=true"

#### Find collections by entry id

One entry id

    curl "%CMR-ENDPOINT%/collections?entry_id\[\]=SHORT_V5"

#### Find collections by dif entry id

This searches for matches on either entry id or associated difs

One dif\_entry\_id

    curl "%CMR-ENDPOINT%/collections?dif_entry_id\[\]=SHORT_V5"

#### Find collections by archive center

This supports `pattern` and `ignore_case`.

Find collections matching 'archive_center' param value

    curl "%CMR-ENDPOINT%/collections?archive_center\[\]=LARC"
    curl "%CMR-ENDPOINT%/collections?archive_center=Sedac+AC"

Find collections matching any of the 'archive_center' param values

     curl "%CMR-ENDPOINT%/collections?archive_center\[\]=Larc&archive_center\[\]=SEDAC"

#### Find collections with temporal

The temporal datetime has to be in yyyy-MM-ddTHH:mm:ssZ format.

    curl "%CMR-ENDPOINT%/collections?temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z,30,60&temporal\[\]=2000-01-01T10:00:00Z,,30&temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"

The first two values of the parameter together define the temporal bounds. See under [Temporal Range searches](#temporal-range-searches) for different ways of specifying the temporal bounds including ISO 8601.

For temporal range search, the default is inclusive on the range boundaries. This can be changed by specifying `exclude_boundary` option with `options[temporal][exclude_boundary]=true`. This option has no impact on periodic temporal searches.

#### Find collections by project

Note: An alias for the parameter 'project' is 'campaign'. As such 'campaign' can be used in place of 'project'.

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'project' param value

     curl "%CMR-ENDPOINT%/collections?project\[\]=ESI"

Find collections matching any of the 'project' param values

     curl "%CMR-ENDPOINT%/collections?project\[\]=ESI&project\[\]=EVI&project\[\]=EPI"

Find collections that match all of the 'project' param values

     curl "%CMR-ENDPOINT%/collections?project\[\]=ESI&project\[\]=EVI&project\[\]=EPI&options\[project\]\[and\]=true"

#### Find collections by updated_since

  Find collections which have revision date starting at or after 'updated_since' param value

     curl "%CMR-ENDPOINT%/collections?updated_since=2014-05-08T20:06:38.331Z"

#### Find collections by revision_date

  This supports option `and`.

  Find collections which have revision date within the ranges of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/collections?revision_date\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z&revision_date\[\]=2015-01-01T10:00:00Z,"

#### Find collections by processing\_level\_id

This supports `pattern` and `ignore_case`.

Find collections matching 'processing_level_id'

     curl "%CMR-ENDPOINT%/collections?processing_level_id\[\]=1B"

Find collections matching any of the 'processing\_level\_id' param values

     curl "%CMR-ENDPOINT%/collections?processing_level_id\[\]=1B&processing_level_id\[\]=2B"

The alias 'processing_level' also works for searching by processing level id.

#### Find collections by platform

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'platform' param value

     curl "%CMR-ENDPOINT%/collections?platform\[\]=1B"

Find collections matching any of the 'platform' param values

     curl "%CMR-ENDPOINT%/collections?platform\[\]=1B&platform\[\]=2B"

#### Find collections by instrument

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'instrument' param value

     curl "%CMR-ENDPOINT%/collections?instrument\[\]=1B"

Find collections matching any of the 'instrument' param values

     curl "%CMR-ENDPOINT%/collections?instrument\[\]=1B&instrument\[\]=2B"

#### Find collections by sensor.

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'sensor' param value

     curl "%CMR-ENDPOINT%/collections?sensor\[\]=1B"

Find collections matching any of the 'sensor' param values

     curl "%CMR-ENDPOINT%/collections?sensor\[\]=1B&sensor\[\]=2B"

#### Find collections by spatial\_keyword

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'spatial_keyword' param value

     curl "%CMR-ENDPOINT%/collections?spatial_keyword\[\]=DC"

Find collections matching any of the 'spatial_keyword' param values

     curl "%CMR-ENDPOINT%/collections?spatial_keyword\[\]=DC&spatial_keyword\[\]=LA"

#### Find collections by science_keywords

This supports option _or_.

Find collections matching 'science_keywords' param value

     curl "%CMR-ENDPOINT%/collections?science_keywords\[0\]\[category\]=Cat1"

Find collections matching multiple 'science_keywords' param values, default is :and

     curl "%CMR-ENDPOINT%/collections?science_keywords\[0\]\[category\]=Cat1&science_keywords\[0\]\[topic\]=Topic1&science_keywords\[1\]\[category\]=Cat2"

#### Find collections by two\_d\_coordinate\_system\_name

This supports pattern. two\_d\_coordinate\_system\[name\] param is an alias of two\_d\_coordinate\_system\_name, but it does not support pattern.

  Find collections matching 'two\_d\_coordinate\_system\_name' param value

    curl "%CMR-ENDPOINT%/collections?two_d_coordinate_system_name\[\]=Alpha"

  Find collections matching any of the 'two\_d\_coordinate\_system\_name' param values

    curl "%CMR-ENDPOINT%/collections?two_d_coordinate_system_name\[\]=Alpha&two_d_coordinate_system_name\[\]=Bravo"

#### Find collections by collection\_data\_type

Supports ignore_case and the following aliases for "NEAR\_REAL\_TIME": "near\_real\_time", "nrt", "NRT", "near real time", "near-real time", "near-real-time", "near real-time".

  Find collections matching 'collection\_data\_type' param value

     curl "%CMR-ENDPOINT%/collections?collection_data_type\[\]=NEAR_REAL_TIME"

  Find collections matching any of the 'collection\_data\_type' param values

     curl "%CMR-ENDPOINT%/collections?collection_data_type\[\]=NEAR_REAL_TIME&collection_data_type\[\]=OTHER"

#### Find collections by online_only

    curl "%CMR-ENDPOINT%/collections?online_only=true"

#### Find collections by downloadable

    curl "%CMR-ENDPOINT%/collections?downloadable=true"

#### Find collections by browse_only

    curl "%CMR-ENDPOINT%/collections?browse_only=true"

#### Find collections by browsable

    curl "%CMR-ENDPOINT%/collections?browsable=true"

#### Find collections by keyword search

Keyword searches are case insensitive and support wild cards ? and *.

    curl "%CMR-ENDPOINT%/collections?keyword=alpha%20beta%20g?mma"

The following fields are indexed for keyword search:

    * Concept ID
    * Provider ID
    * Entry title
    * Data type
    * Short name
    * Long name
    * Summary
    * Version ID
    * Version description
    * Processing level ID
    * Science keywords
    * Archive centers
    * Additional attribute names, data types, values, and descriptions
    * Spatial keywords
    * Temporal keywords
    * Associated DIFs
    * Project short and long names
    * Platform short and long names
    * Instrument short names, long names, and techniques
    * Sensor short names, long names, and techniques
    * Characteristic names and descriptions
    * TwoD coordinate system names

#### Find collections by provider

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'provider' param value

    curl "%CMR-ENDPOINT%/collections?provider=ASF"

Find collections matching any of the 'provider' param values

    curl "%CMR-ENDPOINT%/collections?provider=ASF&provider=SEDAC"

#### Find collections by short name

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching any of the 'short\_name' param values

    curl "%CMR-ENDPOINT%/collections?short_name=DEM_100M&short_name=MINIMAL"

Find collections matching 'short\_name' param value with a pattern

    curl "%CMR-ENDPOINT%/collections?short_name=D*&options[short_name][pattern]=true"

#### Find collections by version

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching the given 'short\_name' and 'version' param values

    curl "%CMR-ENDPOINT%/collections?short_name=DEM_100M&version=1"

Find collections matching the given 'short\_name' and any of the 'version' param values

    curl "%CMR-ENDPOINT%/collections?short_name=dem_100m&version=1&version=2"

#### Find collections by Spatial

##### Polygon

Polygon points are provided in counter-clockwise order. The last point should match the first point to close the polygon. The values are listed comma separated in longitude latitude order, i.e. lon1, lat1, lon2, lat2, lon3, lat3, and so on.

    curl "%CMR-ENDPOINT%/collections?polygon=10,10,30,10,30,20,10,20,10,10"

##### Bounding Box

Bounding boxes define an area on the earth aligned with longitude and latitude. The Bounding box parameters must be 4 comma-separated numbers: lower left longitude, lower left latitude, upper right longitude, upper right latitude.

    curl "%CMR-ENDPOINT%/collections?bounding_box=-10,-5,10,5

##### Point

Search using a point involves using a pair of values representing the point coordinates as parameters. The first value is the longitude and second value is the latitude.

    curl "%CMR-ENDPOINT%/collections?point=100,20"

##### Line

Lines are provided as a list of comma separated values representing coordinates of points along the line. The coordinates are listed in the format lon1, lat1, lon2, lat2, lon3, lat3, and so on.

    curl "%CMR-ENDPOINT%/collections?line=-0.37,-14.07,4.75,1.27,25.13,-15.51"

Note: A query could consist of multiple spatial parameters of different types, two bounding boxes and a polygon for example. If multiple spatial parameters are present, all the parameters irrespective of their type are AND'd in a query. So, if a query contains two bounding boxes and a polygon for example, it will return only those collections which intersect both the bounding boxes and the polygon.

#### Sorting Collection Results

Collection results are sorted by ascending entry title by default. One or more sort keys can be specified using the `sort_key[]` parameter. The order used impacts searching. Fields can be prepended with a `-` to sort in descending order. Ascending order is the default but `+` can be used to explicitly request ascending.

##### Valid Collection Sort Keys

  * `entry_title`
  * `dataset_id` - alias for entry_title
  * `entry_id`
  * `start_date`
  * `end_date`
  * `platform`
  * `instrument`
  * `sensor`
  * `provider`
  * `revision_date`
  * `score` - document relevance score, only valid with keyword search, defaults to descending

Example of sorting by start_date in descending order: (Most recent data first)

    curl "%CMR-ENDPOINT%/collections?sort_key\[\]=-start_date


#### Retrieving All Revisions of a Collection

In addition to retrieving the latest revision for a collection parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisons, by passing in `all_revisons=true` with the URL parameters. The reference and UMM JSON response formats are supported for all revision searches. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true".

    curl "%CMR-ENDPOINT%/collections?provider=PROV1&all_revisions=true&pretty=true"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>3</hits>
        <took>5</took>
        <references>
            <reference>
                <name>et1</name>
                <id>C1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/C1200000000-PROV1/3</location>
                <revision-id>3</revision-id>
            </reference>
            <reference>
                <name>et1</name>
                <id>C1200000000-PROV1</id>
                <revision-id>2</revision-id>
                <deleted>true</deleted>
            </reference>
            <reference>
                <name>et1</name>
                <id>C1200000000-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/C1200000000-PROV1/1</location>
                <revision-id>1</revision-id>
            </reference>
        </references>
    </results>
```

### Granule Search Examples

#### Find all granules

    curl "%CMR-ENDPOINT%/granules"

#### Find granules with a granule-ur

    curl "%CMR-ENDPOINT%/granules?granule_ur\[\]=DummyGranuleUR"

#### Find granules with a producer granule id

    curl "%CMR-ENDPOINT%/granules?producer_granule_id\[\]=DummyID"

#### Find granules matching either granule ur or producer granule id

This condition is encapsulated in a single parameter called readable_granule_name

    curl "%CMR-ENDPOINT%/granules?readable_granule_name\[\]=DummyID"

#### Find granules by online_only

    curl "%CMR-ENDPOINT%/granules?online_only=true"

#### Find granules by downloadable

    curl "%CMR-ENDPOINT%/granules?downloadable=true"

#### Find granules by additional attribute

Find an attribute attribute with name "PERCENTAGE" only

    curl "%CMR-ENDPOINT%/granules?attribute\[\]=PERCENTAGE"

Find an attribute attribute with name "PERCENTAGE" of type float with value 25.5

    curl "%CMR-ENDPOINT%/granules?attribute\[\]=float,PERCENTAGE,25.5"

Find an attribute attribute with name "PERCENTAGE" of type float in range 25.5 - 30.

    curl "%CMR-ENDPOINT%/granules?attribute\[\]=float,PERCENTAGE,25.5,30"

Find an attribute attribute with name "PERCENTAGE" of type float with min value 25.5.

    curl "%CMR-ENDPOINT%/granules?attribute\[\]=float,PERCENTAGE,25.5,"

Find an attribute attribute with name "PERCENTAGE" of type float with max value 30.

    curl "%CMR-ENDPOINT%/granules?attribute\[\]=float,PERCENTAGE,,30"

Find an additional attribute with name "X,Y,Z" with value 7.

    curl "%CMR-ENDPOINT%/granules?attribute\[\]=float,X\,Y\,Z,7"

Find an additional attribute with name "X\Y\Z" with value 7.

    curl "%CMR-ENDPOINT%/granules?attribute\[\]=float,X\Y\Z,7"

Multiple attributes can be provided. The default is for granules to match all the attribute parameters. This can be changed by specifying `or` option with `options[attribute][or]=true`.

For additional attribute range search, the default is inclusive on the range boundaries. This can be changed by specifying `exclude_boundary` option with `options[attribute][exclude_boundary]=true`.

For granule additional attributes search, the default is searching for the attributes included in the collection this granule belongs to as well. This can be changed by specifying `exclude_collection` option with `options[attribute][exclude_collection]=true`.

#### Find granules by Spatial
The parameters used for searching granules by spatial are the same as the spatial parameters used in collections searches. (See under "Find collections by Spatial" for more details.)

##### Polygon

    curl "%CMR-ENDPOINT%/granules?polygon=10,10,30,10,30,20,10,20,10,10"

##### Bounding Box

    curl "%CMR-ENDPOINT%/granules?bounding_box=-10,-5,10,5

##### Point

    curl "%CMR-ENDPOINT%/granules?point=100,20"

##### Line

    curl "%CMR-ENDPOINT%/granules?line=-0.37,-14.07,4.75,1.27,25.13,-15.51"

#### Find granules by orbit number

  Find granules with an orbit number of 10

    curl "%CMR-ENDPOINT%/granules?orbit_number=10"

Find granules with an orbit number in a range of 0.5 to 1.5

    curl "%CMR-ENDPOINT%/granules?orbit_number=0.5,1.5"

#### Find granules by orbit equator crossing longitude

Find granules with an exact equator crossing longitude of 90

    curl "%CMR-ENDPOINT%/granules?equator_crossing_longitude=90"

Find granules with an orbit equator crossing longitude in the range of 0 to 10

    curl "%CMR-ENDPOINT%/granules?equator_crossing_longitude=0,10

Find granules with an equator crossing longitude in the range from 170 to -170
  (across the anti-meridian)

    curl "%CMR-ENDPOINT%/granules?equator_crossing_longitude=170,-170

#### Find granules by orbit equator crossing date

Find granules with an orbit equator crossing date in the range of 2000-01-01T10:00:00Z to 2010-03-10T12:00:00Z

    curl "%CMR-ENDPOINT%/granules?equator_crossing_date=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z

The time interval in equator crossing date range searches can be specified in different ways including ISO 8601. See under [Temporal Range searches](#temporal-range-searches).

#### Find granules by updated_since

Find granules which have revision date starting at or after 'updated_since' param value

     curl "%CMR-ENDPOINT%/granules?updated_since=2014-05-08T20:12:35Z"

#### Find granules by revision_date

This supports option `and`.

Find granules which have revision date within the ranges of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/granules?revision_date\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z&revision_date\[\]=2015-01-01T10:00:00Z,"

#### Find granules by cloud_cover

Find granules with just the min cloud cover value set to 0.2

     curl "%CMR-ENDPOINT%/granules?cloud_cover=0.2,"

Find granules with just the max cloud cover value set to 30

     curl "%CMR-ENDPOINT%/granules?cloud_cover=,30"

Find granules with cloud cover numeric range set to min: -70.0 max: 120.0

     curl "%CMR-ENDPOINT%/granules?cloud_cover=-70.0,120.0"

#### Find granules by platform

This supports `pattern`, `ignore_case` and option `and`.

     curl "%CMR-ENDPOINT%/granules?platform\[\]=1B"

#### Find granules by instrument

This supports `pattern`, `ignore_case` and option `and`.

     curl "%CMR-ENDPOINT%/granules?instrument\[\]=1B"

#### Find granules by sensor param

This supports `pattern`, `ignore_case` and option `and`.

     curl "%CMR-ENDPOINT%/granules?sensor\[\]=1B"

#### Find granules by project

Note: An alias for the parameter 'project' is 'campaign'. As such 'campaign' can be used in place of 'project'.

This supports `pattern`, `ignore_case` and option `and`.

Find granules matching 'project' param value

     curl "%CMR-ENDPOINT%/granules?project\[\]=2009_GR_NASA"

Find granules matching any of the 'project' param values

     curl "%CMR-ENDPOINT%/granules?project\[\]=2009_GR_NASA&project\[\]=2013_GR_NASA"

Find granules matching the given pattern for the 'project' param value
     curl "%CMR-ENDPOINT%/granules?project\[\]=20??_GR_NASA&options\[project\]\[pattern\]=true"

Find granules that match all of the 'project' param values

     curl "%CMR-ENDPOINT%/granules?project\[\]=2009_GR_NASA&project\[\]=2013_GR_NASA&options\[project\]\[and\]=true"

#### Find granules by concept id

Note: more than one may be supplied

Find granule by concept id

     curl "%CMR-ENDPOINT%/granules?concept_id\[\]=G1000000002-CMR_PROV1"

Find granule by echo granule id

     curl "%CMR-ENDPOINT%/granules?echo_granule_id\[\]=G1000000002-CMR_PROV1"

Find granules by parent concept id. `concept_id` or `collection_concept_id` can be used interchangeably.

     curl "%CMR-ENDPOINT%/granules?concept_id\[\]=C1000000001-CMR_PROV2"
     curl "%CMR-ENDPOINT%/granules?collection_concept_id\[\]=C1000000001-CMR_PROV2"

Find granules by echo collection id

     curl "%CMR-ENDPOINT%/granules?echo_collection_id\[\]=C1000000001-CMR_PROV2"

#### Find granules by day\_night\_flag param, supports pattern and ignore_case

```
curl "%CMR-ENDPOINT%/granules?day_night_flag=night

curl "%CMR-ENDPOINT%/granules?day_night_flag=day

curl "%CMR-ENDPOINT%/granules?day_night=unspecified
```

#### Find granules by two\_d\_coordinate\_system parameter.

Note: An alias for the parameter 'two_d_coordinate_system' is 'grid'. As such 'grid' can be used in place of 'two_d_coordinate_system'.

```
  curl "%CMR-ENDPOINT%/granules?two_d_coordinate_system\[\]=wrs-1:5,10:8-10,0-10:8,12
```

The parameter expects a coordinate system name and a set of two-d coordinates. The two-d coordinates could be represented either by a single coordinate pair or a pair of coordinate ranges. ':' is used as the separator between the coordinate system name, single coordinate pairs and coordinate range pairs. The coordinates in the single coordinate pair are represented in the format "x,y". And the coordinates in the coordinate range pairs are represented in the format "x1-x2,y1-y2" where x1 and x2 are the bounds of the values for the first coordinate and y1 and y2, for the second coordinate. One can also use single values for each of the two ranges, say "x1" instead of "x1-x2", in which case the upper and lower bound are considered the same. In other words using "x1" for range is equivalent to using "x1-x1". A single query can consist of a combination of individual coordinate pairs and coordinate range pairs. For example, the query above indicates that the user wants to search for granules which have a two\_d\_coordinate\_system whose name is wrs-1 and whose two-d coordinates match(or fall within) at least one of the given pairs: a single coordinate pair (5,10), a range coordinate pair 8-10,0-10 and another single coordinate pair (8,12).

#### Find granules by provider

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules matching 'provider' param value

    curl "%CMR-ENDPOINT%/granules?provider=ASF"

Find granules matching any of the 'provider' param values

    curl "%CMR-ENDPOINT%/granules?provider=ASF&provider=SEDAC"

#### Find granules by short name

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules matching any of the 'short\_name' param values. The 'short\_name' here refers to the short name of the collections corresponding to the granules being searched for.

    curl "%CMR-ENDPOINT%/granules?short_name=DEM_100M&short_name=MINIMAL"

Find granules matching 'short\_name' param value with a pattern.

    curl "%CMR-ENDPOINT%/granules?short_name=D*&options[short_name][pattern]=true"

#### Find granules by version

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules matching the 'short\_name' and 'version' param values. The 'short\_name' and 'version' here refers to the short name and version of the collections corresponding to the granules being searched for.

    curl "%CMR-ENDPOINT%/granules?short_name=DEM_100M&version=1"

Find granules matching the given 'short_name' and any of the 'version' param values

    curl "%CMR-ENDPOINT%/granules?short_name=DEM_100M&version=1&version=2"

#### Find granules by entry title

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules matching 'entry\_title' param value. The 'entry\_title' here refers to the entry title of the collections corresponding to the granules being searched for.

    curl "%CMR-ENDPOINT%/granules?entry_title=DatasetId%204"

See under "Find collections by entry title" for more examples of how to use this parameter.

#### Find granules with temporal

The temporal datetime has to be in yyyy-MM-ddTHH:mm:ssZ format.

    curl "%CMR-ENDPOINT%/granules?temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z,30,60&temporal\[\]=2000-01-01T10:00:00Z,,30&temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"

The first two values of the parameter together define the temporal bounds. See under [Temporal Range searches](#temporal-range-searches) for different ways of specifying the temporal bounds including ISO 8601.

For temporal range search, the default is inclusive on the range boundaries. This can be changed by specifying `exclude_boundary` option with `options[temporal][exclude_boundary]=true`. This option has no impact on periodic temporal searches.

#### Exclude granules from elastic results by echo granule id and concept ids.

Note: more than one id may be supplied in exclude param

Exclude granule by echo granule id

```
curl "%CMR-ENDPOINT%/granules?echo_granule_id\[\]=G1000000002-CMR_PROV1&echo_granule_id\[\]=G1000000003-CMR_PROV1&echo_granule_id\[\]=G1000000006-CMR_PROV2&exclude\[echo_granule_id\]\[\]=G1000000006-CMR_PROV2"

curl "%CMR-ENDPOINT%/granules?exclude\[echo_granule_id\]\[\]=G1000000006-CMR_PROV2&cloud_cover=-70,120"
```

Exclude granule by concept id

    curl "%CMR-ENDPOINT%/granules?echo_granule_id\[\]=G1000000002-CMR_PROV1&echo_granule_id\[\]=G1000000003-CMR_PROV1&echo_granule_id\[\]=G1000000006-CMR_PROV2&exclude\[concept_id\]\[\]=G1000000006-CMR_PROV2"

Exclude granule by parent concept id

    curl "%CMR-ENDPOINT%/granules?echo_granule_id\[\]=G1000000002-CMR_PROV1&echo_granule_id\[\]=G1000000003-CMR_PROV1&echo_granule_id\[\]=G1000000006-CMR_PROV2&exclude\[concept_id\]\[\]=C1000000001-CMR_PROV2"

#### Sorting Granule Results

Granule results are sorted by ascending provider and start date by default. One or more sort keys can be specified using the `sort_key[]` parameter. The order used impacts searching. Fields can be prepended with a `-` to sort in descending order. Ascending order is the default but `+` can be used to explicitly request ascending.

##### Valid Granule Sort Keys

  * `campaign` - alias for project
  * `entry_title`
  * `dataset_id` - alias for entry_title
  * `data_size`
  * `end_date`
  * `granule_ur`
  * `producer_granule_id`
  * `project`
  * `provider`
  * `readable_granule_name` - this sorts on a combination of `producer_granule_id` and `granule_ur`. If a `producer_granule_id` is present, that value is used. Otherwise, the `granule_ur` is used.
  * `short_name`
  * `start_date`
  * `version`
  * `platform`
  * `instrument`
  * `sensor`
  * `day_night_flag`
  * `online_only`
  * `browsable` (legacy key browse_only is supported as well)
  * `cloud_cover`
  * `revision_date`

Example of sorting by start_date in descending order: (Most recent data first)

    curl "%CMR-ENDPOINT%/granules/sort_key\[\]=-start_date


### Retrieve concept with a given concept-id or concept-id & revision-id

This allows retrieving the metadata for a single concept. If no format
is specified the native format of the metadata will be returned.

By concept id

    curl -i  "%CMR-ENDPOINT%/concepts/:concept-id"

By concept id and revision id

    curl -i "%CMR-ENDPOINT%/concepts/:concept-id/:revision-id"

    curl -i "%CMR-ENDPOINT%/concepts/G100000-PROV1"
    curl -i "%CMR-ENDPOINT%/concepts/G100000-PROV1.iso"
    curl -i -H 'Accept: application/xml' "%CMR-ENDPOINT%/concepts/G100000-PROV1"
    curl -i -H 'Accept: application/metadata+xml' "%CMR-ENDPOINT%/concepts/G100000-PROV1"
    curl -i "%CMR-ENDPOINT%/concepts/G100000-PROV1.json"
    curl -i "%CMR-ENDPOINT%/concepts/C100000-PROV1/1"
    curl -i "%CMR-ENDPOINT%/concepts/G100000-PROV1/2.echo10"

Note that attempting to retrieve a revision that is a tombstone is an error and will return a 400 status code.

The following extensions and MIME types are supported by the
`/concepts/` resource:

  * `json`      "application/json"
  * `xml`       "application/xml" (same as .native)
  * `native`    "application/metadata+xml"
  * `echo10`    "application/echo10+xml"
  * `iso`       "application/iso19115+xml"
  * `iso19115`  "application/iso19115+xml"
  * `dif`       "application/dif+xml"
  * `dif10`     "application/dif10+xml"
  * `atom`      "application/atom+xml"

### Search with POST

Search collections or granules with query parameters encoded form in POST request body.

    curl -i -XPOST %CMR-ENDPOINT%/collections -d "dataset_id[]=Example%20DatasetId&dataset_id[]=Dataset2"

### Search Response as Granule Timeline

Granule timeline queries allow clients to find time intervals with continuous granule coverage per collection. The intervals are listed per collection and contain the number of granules within each interval. A timeline search can be performed by sending a `GET` request with query parameters or a `POST` request with query parameters form encoded in request body to the `granules/timeline` route. The utility of this feature for clients is in building interactive timelines. Clients need to display on the timeline where there is granule data and where there is none.

It supports all normal granule parameters. It requires the following parameters.

  * `start_date` - The start date of the timeline intervals to search from.
  * `end_date` - The end date of to search from.
  * `interval` - The interval granularity. This can be one of year, month, day, hour, minute, or second. At least one granule found within the interval time will be considered coverage for that interval.
  * `concept_id` - Specifies a collection concept id to search for. It is recommended that the timeline search be limited to a few collections for good performance.

The response format is in JSON. Intervals are returned as tuples containing three numbers like `[949363200,965088000,4]`. The two numbers are the start and stop date of the interval represented by the number of seconds since the epoch. The third number is the number of granules within that interval.

#### Example Request:

    curl -i "%CMR-ENDPOINT%/granules/timeline?concept_id=C1-PROV1&start_date=2000-01-01T00:00:00Z&end_date=2002-02-01T00:00:00.000Z&interval=month""

#### Example Response

```
[{"concept-id":"C1200000000-PROV1","intervals":[[949363200,965088000,4],[967766400,970358400,1],[973036800,986083200,3],[991353600,1072915200,3]]}]
```

### Retrieve Provider Holdings

Provider holdings can be retrieved as XML or JSON.

All provider holdings

    curl "%CMR-ENDPOINT%/provider_holdings.xml"

Provider holdings for a list of providers

    curl "%CMR-ENDPOINT%/provider_holdings.json?provider-id\[\]=PROV1&provider-id\[\]=PROV2"

### Search with JSON Query

Search for collections with JSON in a POST request body. The JSON must conform to the schema
that is defined in `%CMR-ENDPOINT%/site/JSONQueryLanguage.json`. Only collection search is
supported, not granule search.

    curl -XPOST -H "Content-Type: application/json" %CMR-ENDPOINT%/collections
    -d '{"condition": { "and": [{ "not": { "or": [{ "provider": "TEST" },
                                                  { "and": [{ "project": "test-project",
                                                              "platform": "mars-satellite" }]}]}},
                                { "bounding_box": [-45,15,0,25],
                                  "science_keywords": { "category": "EARTH SCIENCE" }}]}}'
### Search with AQL

Search collections or granules with AQL in POST request body. The AQL must conform to the schema
that is defined in `%CMR-ENDPOINT%/site/IIMSAQLQueryLanguage.xsd`.

    curl -i -XPOST -H "Content-Type: application/xml" %CMR-ENDPOINT%/concepts/search -d '<?xml version="1.0" encoding="UTF-8"?>
    <query><for value="collections"/><dataCenterId><all/></dataCenterId>
    <where><collectionCondition><shortName><value>S1</value></shortName></collectionCondition></where></query>'

### Document Scoring For Keyword Search

When a keyword search is requested, matched documents receive relevancy scores as follows:

A series of filters are executed against each document. Each of these has an associated boost
value. The boost values of all the filters that match a given document are multiplied together
to get the final document score. Documents that match none of the filters have a default
score of 1.0.

The filters are case insensitive, support wild-cards * and ?, and are given below:

1. All keywords are contained in the long-name field OR one of the keywords exactly matches
the short-name field - weight 1.4

2. All keywords are contained in the Project/long-name field OR one of the keywords
exactly matches the Project/short-name field - weight 1.3

3. All keywords are contained in the Platform/long-name field OR one of the keywords
exactly matches the Platform/short-name field - weight 1.3

4. All keywords are contained in the Platform/Instrument/long-name field OR one of the keywords
exactly matches the Platform/Instrument/short-name field - weight 1.2

5. All keywords are contained in the Platform/Instrument/Sensor/long-name field OR one of the keywords exactly matches the Platform/Instrument/Sensor/short-name field - weight 1.2

6. The keyword field is a single string that exactly matches the science-keyword field - weight 1.2

7. The keyword field is a single string that exactly matches the spatial-keyword field - weight 1.1

8. The keyword field is a single string that exactly matches the temporal-keyword field  - weight 1.1


### Facets

Facets are counts of unique values from fields in items matching search results. Facets are supported with collection search results and are enabled with the `include_facets=true` parameter. Facets are supported on all collection search response formats. When `echo_compatible=true` parameter is also present, the facets are returned in the catalog-rest search_facet style in XML or JSON format.

The science_keywords field is a hierarchical field. By default facets are returned in a flat format showing counts for each nested field separately. In order to retrieve hierarchical facets pass in the parameter `hierarchical_facets=true`.

#### Facets in XML Responses

Facets in XML search response formats will be formatted like the following examples. The exception is ATOM XML which is the same except the tags are in the echo namespace.

##### Flat XML Facets

```
<facets>
  <facet field="archive_center">
    <value count="28989">LARC</value>
    <value count="19965">GSFC</value>
  </facet>
  <facet field="project">
    <value count="245">MANTIS</value>
    <value count="132">THUNDER</value>
    <value count="13">Mysterio</value>
  </facet>
  <facet field="platform">
    <value count="76">ASTER</value>
  </facet>
  <facet field="instrument">
    <value count="2">MODIS</value>
  </facet>
  <facet field="sensor">...</facet>
  <facet field="two_d_coordinate_system_name">...</facet>
  <facet field="processing_level_id">...</facet>
  <facet field="category">...</facet>
  <facet field="topic">...</facet>
  <facet field="term">...</facet>
  <facet field="variable_level_1">...</facet>
  <facet field="variable_level_2">...</facet>
  <facet field="variable_level_3">...</facet>
  <facet field="detailed_variable">...</facet>
</facets>
```

##### Hierarchical XML Facets

Fields that are not hierarchical are returned in the same format as the flat response, but hierarchical fields are returned in a nested structure.

```
<facets>
  <facet field="archive_center"/>
  ...
  <facet field="science_keywords">
    <facet field="category">
      <value-count-maps>
        <value-count-map>
          <value count="31550">EARTH SCIENCE</value>
          <facet field="topic">
            <value-count-maps>
              <value-count-map>
                <value count="8166">ATMOSPHERE</value>
                <facet field="term">
                  <value-count-maps>
                    <value-count-map>
                      <value count="785">AEROSOLS</value>
                    </value-count-map>
                  </value-count-maps>
                </facet>
              </value-count-map>
              <value-count-map>
                <value count="10269">OCEANS</value>
                <facet field="term">
                  <value-count-maps>
                    <value-count-map>
                      <value count="293">AQUATIC SCIENCES</value>
                    </value-count-map>
                  </value-count-maps>
                </facet>
              </value-count-map>
            </value-count-maps>
          </facet>
        </value-count-map>
      </value-count-maps>
    </facet>
  </facet>
</facets>
```

#### Facets in JSON Responses

Facets in JSON search response formats will be formatted like the following examples.

##### Flat JSON facets

```
{
  "feed": {
    "entry": [...],
    "facets": [{
      "field": "archive_center",
      "value-counts": [
        ["LARC", 28989],
        ["GSFC", 19965]
      ]
    }, {
      "field": "project",
      "value-counts": [
        ["MANTIS", 245],
        ["THUNDER", 132],
        ["Mysterio", 13]
      ]
    }, {
      "field": "platform",
      "value-counts": [["ASTER", 76]]
    }, {
      "field": "instrument",
      "value-counts": [["MODIS", 2]]
    }, {
      "field": "sensor",
      "value-counts": [...]
    }, {
      "field": "two_d_coordinate_system_name",
      "value-counts": [...]
    }, {
      "field": "processing_level_id",
      "value-counts": [...]
    }, {
      "field": "category",
      "value-counts": [...]
    }, {
      "field": "topic",
      "value-counts": [...]
    }, {
      "field": "term",
      "value-counts": [...]
    }, {
      "field": "variable_level_1",
      "value-counts": [...]
    }, {
      "field": "variable_level_2",
      "value-counts": [...]
    }, {
      "field": "variable_level_3",
      "value-counts": [...]
    }, {
      "field": "detailed_variable",
      "value-counts": [...]
    }]
  }
}
```

##### Hierarchical JSON facets

Fields that are not hierarchical are returned in the same format as the flat response, but hierarchical fields are returned in a nested structure.

```
    "facets" : [ {
      "field" : "archive_center",
      "value-counts" : [ ]
    ...
    }, {
      "field" : "science_keywords",
      "subfields" : [ "category" ],
      "category" : [ {
        "value" : "EARTH SCIENCE",
        "count" : 31550,
        "subfields" : [ "topic" ],
        "topic" : [ {
          "value" : "ATMOSPHERE",
          "count" : 8166,
          "subfields" : [ "term" ],
          "term" : [ {
            "value" : "AEROSOLS",
            "count" : 785 } ]
          }, {
          "value" : "OCEANS",
          "count" : 10269,
          "subfields" : [ "term" ],
          "term" : [ {
            "value" : "AQUATIC SCIENCES",
            "count" : 293
          } ]
        } ]
      } ]
    } ]
```

### Search for Tiles

Tiles are geographic regions formed by splitting the world into rectangular regions in a projected coordinate system such as Sinusoidal Projection based off an Authalic Sphere. CMR supports searching of tiles which fall within a geographic region defined by a given input geometry. Currently, only tiles in MODIS Integerized Sinusoidal Grid(click [here](https://lpdaac.usgs.gov/products/modis_products_table/modis_overview) for more details on the grid) can be searched. The input geometry could be either a minimum bounding rectangle or one of point, line or polygon in spherical coordinates. The input coordinates are to be supplied in the same format as in granule and collection spatial searches (See under "Find granules by Spatial").

A query could consist of multiple spatial parameters, two points and a bounding box for example. All the spatial parameters are OR'd in a query meaning a query will return all the tiles which intersect at-least one of the given geometries.

Here are some examples:
Find the tiles which intersect a polygon.

    curl -i "%CMR-ENDPOINT%/tiles?polygon=10,10,30,10,30,20,10,20,10,10"

Find the tiles which intersect a bounding rectangle.

    curl -i "%CMR-ENDPOINT%/tiles?bounding_box=-10,-5,10,5"

Find the tile which contains a point.

    curl -i "%CMR-ENDPOINT%/tiles?point=-84.2625,36.013"

Find all the tiles which a line intersects.

    curl -i "%CMR-ENDPOINT%/tiles?line=1,1,10,5,15,9"

The output of these requests is a list of tuples containing tile coordinates, e.g: [[16,8],[16,9],[17,8],[17,9]], in the json format. The first value in each tuple is the horizontal grid coordinate(h), i.e. along east-west and the second value is the vertical grid coordinate(v), i.e. along north-south.

### Retrieve Controlled Keywords

The keyword endpoint is used to retrieve the full list of keywords for each of the controlled vocabulary fields. The controlled vocabulary is cached within CMR, but the actual source is the GCMD Keyword Management System (KMS). Users of this endpoint are interested in knowing what the CMR considers as the current controlled vocabulary, since it is the cached CMR values that will eventually be enforced on CMR ingest.

The keywords are returned in a hierarchical JSON format. The response format is such that the caller does not need to know the hierarchy, but it can be inferred from the results. Keywords are not guaranteed to have values for every subfield in the hierarchy, so the response will indicate the next subfield below the current field in the hierarchy which has a value. It is possible for the keywords to have multiple potential subfields below it for different keywords with the same value for the current field in the hierarchy. When this occurs the subfields property will include each of the subfields.

Supported keywords include 'archive_centers', 'platforms', 'instruments', and 'science_keywords'. The endpoint also supports 'providers' which is an alias to 'archive_centers'.

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

### Tagging

Tagging allows arbitrary sets of collections to be grouped under a single namespaced value. The sets of collections can be recalled later when searching by tag fields.

Tags have the following fields:

* Namespace (REQUIRED): free text specifying the name of the organization (e.g., LPDAAC) or the project (e.g., org.ceos.wgiss.cwic) who created the tag. Allowing the Namespace to be a part of the Tag ensures uniqueness when different organizations or projects choose to use the same tag Value (see below). The maximum length for a namespace is 514 characters.
* Category (OPTIONAL): free text category name for the tag. Category is a way of grouping tags with similar purposes, i.e. grouping tags by type (e.g., Category of public_data_set and Values of cwic_public, xxx_public for the tags in that category) The maximum length for a category is 1030 characters.
* Value (REQUIRED): free text "name" of the tag. The maximum length of a value is 515 characters.
* Description (OPTIONAL): a free text description of what this tag is and / or how it is used. The maximum length for description is 4000 characters.
* Originator ID (REQUIRED): the Earthdata Login ID of the person who created the tag.

Both the tag namespace and value cannot contain the Group Separator character. This is the ASCII decimal character 29 and in Unicode U+001D.

#### Creating a Tag

Tags are created by POSTing a JSON representation of a tag to `/tags` along with a valid ECHO token. The user id of the user associated with the token will be used as the originator id. The response will contain a concept id identifying the tag along with the tag revision id.

```
curl -XPOST -i -H "Content-Type: application/json" -H "Echo-Token: mock-echo-system-token" %CMR-ENDPOINT%/tags -d \
'{
  "namespace": "org.ceos.wgiss.cwic",
  "category": "cwic_public",
  "value": "quality",
  "description": "This is a sample tag."
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 48

{"concept-id":"T1200000000-CMR","revision-id":1}
```

#### Retrieving a Tag

A single tag can be retrieved by sending a GET request to `/tags/<concept-id>` where `concept-id` is the concept id of the tag returned when it was created.

```
curl -i %CMR-ENDPOINT%/tags/T1200000000-CMR?pretty=true

HTTP/1.1 200 OK
Content-Length: 216
Content-Type: application/json;charset=ISO-8859-1

{
  "originator-id" : "mock-admin",
  "namespace" : "org.ceos.wgiss.cwic",
  "category" : "cwic_non_public",
  "value" : "quality",
  "description" : "This is a sample tag for indicating some data is high quality."
}
```

#### Updating a Tag

Tags are updated by sending a PUT request with the JSON representation of a tag to `/tags/<concept-id>` where `concept-id` is the concept id of the tag returned when it was created. The same rules apply when updating a tag as when creating it but in addition namespace, value, and originator id cannot be modified. The response will contain the concept id along with the tag revision id.

```
curl -XPUT -i -H "Content-Type: application/json" -H "Echo-Token: mock-echo-system-token" %CMR-ENDPOINT%/tags/T1200000000-CMR -d \
'{
  "namespace": "org.ceos.wgiss.cwic",
  "category": "cwic_non_public",
  "value": "quality",
  "description": "This is a sample tag for indicating some data is high quality."
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 48

{"concept-id":"T1200000000-CMR","revision-id":2}
```

#### Deleting a Tag

Tags are deleted by sending a DELETE request to `/tags/<concept-id>` where `concept-id` is the concept id of the tag returned when it was created. Deleting a tag creates a tombstone that marks the tag as deleted. The concept id of the tag and the revision id of the tombstone are returned from a delete request. Deleting a tag dissociates all collections with the tag.

```
curl -XDELETE -i  -H "Echo-Token: mock-echo-system-token" %CMR-ENDPOINT%/tags/T1200000000-CMR

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 48

{"concept-id":"T1200000000-CMR","revision-id":3}
```

#### Associating Collections with a Tag

Tags can be associated with collections by POSTing a JSON query for collections to `/tags/<concept-id>/associations` where `concept-id` is the concept id of the tag returned when it was created. All collections found will be _added_ to the current set of associated collections with a tag. Tag associations are maintained throughout the life of a collection. If a collection is deleted and readded it will maintain its tags.


```
curl -XPOST -i -H "Content-Type: application/json" -H "Echo-Token: mock-echo-system-token" %CMR-ENDPOINT%/tags/T1200000000-CMR/associations -d \
'{
  "condition": {"provider": "PROV1"}
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 48

{"concept-id":"T1200000000-CMR","revision-id":3}
```


### Administrative Tasks

These tasks require an admin user token with the INGEST\_MANAGEMENT\_ACL with read or update
permission.

#### Clear the cache cache

    curl -i -XPOST %CMR-ENDPOINT%/clear-cache

#### Reset the application to the initial state

Every CMR application has a reset function to reset it back to it's initial state. Currently this only clears the cache so it is effectively the the same as the clear-cache endpoint.

    curl -i -XPOST %CMR-ENDPOINT%/reset

#### Querying caches

Endpoints are provided for querying the contents of the various caches used by the application.
The following curl will return the list of caches:

    curl -i %CMR-ENDPOINT%/caches

The following curl will return the keys for a specific cache:

    curl -i %CMR-ENDPOINT%/caches/cache-name

This curl will return the value for a specific key in the named cache:

    curl -i %CMR-ENDPOINT%/caches/cache-name/cache-key

#### Check application health

This will report the current health of the application. It checks all resources and services used by the application and reports their health in the response body in JSON format. For resources, the report includes an "ok?" status and a "problem" field if the resource is not OK. For services, the report includes an overall "ok?" status for the service and health reports for each of its dependencies. It returns HTTP status code 200 when the application is healthy, which means all its interfacing resources and services are healthy; or HTTP status code 503 when one of the resources or services is not healthy. It also takes pretty parameter for pretty printing the response.

    curl -i -XGET %CMR-ENDPOINT%/health?pretty=true

Example healthy response body:

```
{
  "echo" : {
    "ok?" : true
  },
  "internal-metadata-db" : {
    "ok?" : true,
    "dependencies" : {
      "oracle" : {
        "ok?" : true
      },
      "echo" : {
        "ok?" : true
      }
    }
  },
  "index-set" : {
    "ok?" : true,
    "dependencies" : {
      "elastic_search" : {
        "ok?" : true
      },
      "echo" : {
        "ok?" : true
      }
    }
  }
}
```

Example un-healthy response body:

```
{
  "echo" : {
    "ok?" : true
  },
  "internal-metadata-db" : {
    "ok?" : true,
    "dependencies" : {
      "oracle" : {
        "ok?" : true
      },
      "echo" : {
        "ok?" : true
      }
    }
  },
  "index-set" : {
    "ok?" : false,
    "problem" : {
      "elastic_search" : {
        "ok?" : false,
        "problem" : {
          "status" : "Inaccessible",
          "problem" : "Unable to get elasticsearch cluster health, caught exception: Connection refused"
        }
      },
      "echo" : {
        "ok?" : true
      }
    }
  }
}
```
