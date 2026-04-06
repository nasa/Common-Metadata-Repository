### <a name="collection-search-by-parameters"></a> Collection Search Examples

#### <a name="find-all-collections"></a> Find all collections

    curl "%CMR-ENDPOINT%/collections"

Collection search results are paged. See [Paging Details](#paging-details) for more information on how to page through collection search results.

#### <a name="c-concept-id"></a> Find collections by concept id

A CMR concept id is in the format `<concept-type-prefix> <unique-number> "-" <provider-id>`

  * `concept-type-prefix` is a single capital letter prefix indicating the concept type. "C" is used for collections
  * `unique-number` is a single number assigned by the CMR during ingest.
  * `provider-id` is the short name for the provider. i.e. "LPDAAC\_ECS"

Example: `C123456-LPDAAC_ECS`

    curl "%CMR-ENDPOINT%/collections?concept_id\[\]=C123456-LPDAAC_ECS"

#### <a name="c-doi-value"></a> Find collections by doi value

  Find a collection matching a collection doi value. Note more than one doi value may be supplied.

    curl "%CMR-ENDPOINT%/collections?doi\[\]=doiValue"

#### <a name="c-echo-collection-id"></a> Find collections by echo collection id

  Find a collection matching a echo collection id. Note more than one echo collection id may be supplied.

     curl "%CMR-ENDPOINT%/collections?echo_collection_id\[\]=C1000000001-CMR_PROV2"

#### <a name="c-provider-short-name"></a> Find collections by provider short name

This searches for collections whose provider matches the given provider short names. This supports `ignore_case` option, but not the `pattern` option.

    curl "%CMR-ENDPOINT%/collections?provider_short_name\[\]=SHORT_5&options\[provider_short_name\]\[ignore_case\]=true"

#### <a name="c-entry-title"></a> Find collections by entry title

Find collections matching 'entry_title' param value such as DatasetId%204

    curl "%CMR-ENDPOINT%/collections?entry_title\[\]=DatasetId%204"

Find collections matching 'dataset_id' param (which is an alias for entry title) value

    curl "%CMR-ENDPOINT%/collections?dataset_id\[\]=DatasetId%204"

with multiple dataset ids

    curl "%CMR-ENDPOINT%/collections?entry_title\[\]=DatasetId%204&entry_title\[\]=DatasetId%205"

with a entry title case insensitively

    curl "%CMR-ENDPOINT%/collections?entry_title\[\]=datasetId%204&options\[entry_title\]\[ignore_case\]=true"

with a entry title pattern

    curl "%CMR-ENDPOINT%/collections?entry_title\[\]=DatasetId*&options\[entry_title\]\[pattern\]=true"

#### <a name="c-entry-id"></a> Find collections by entry id

Find collections matching 'entry_id' param value such as SHORT_V5

    curl "%CMR-ENDPOINT%/collections?entry_id\[\]=SHORT_V5"

#### <a name="c-archive-center"></a> Find collections by archive center

This supports `pattern` and `ignore_case`.

Find collections matching 'archive_center' param value

    curl "%CMR-ENDPOINT%/collections?archive_center\[\]=LARC"
    curl "%CMR-ENDPOINT%/collections?archive_center=PROV1+AC"

Find collections matching any of the 'archive_center' param values

     curl "%CMR-ENDPOINT%/collections?archive_center\[\]=Larc&archive_center\[\]=LARC"

#### <a name="c-data-center"></a> Find collections by data center

This supports `pattern`, `and`, and `ignore_case`.

Find collections matching 'data_center' param value

    curl "%CMR-ENDPOINT%/collections?data_center\[\]=LARC"
    curl "%CMR-ENDPOINT%/collections?data_center=PROV1+AC"

Find collections matching any of the 'data_center' param values

     curl "%CMR-ENDPOINT%/collections?data_center\[\]=Larc&data_center\[\]=LARC"

#### <a name="c-temporal"></a> Find collections with temporal

The temporal datetime has to be in yyyy-MM-ddTHH:mm:ssZ format.

    curl "%CMR-ENDPOINT%/collections?temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z,30,60&temporal\[\]=2000-01-01T10:00:00Z,,30&temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"

The first two values of the parameter together define the temporal bounds. See under [Temporal Range searches](#temporal-range-searches) for different ways of specifying the temporal bounds including ISO 8601.

For temporal range search, the default is inclusive on the range boundaries. This can be changed by specifying `exclude_boundary` option with `options[temporal][exclude_boundary]=true`. This option has no impact on periodic temporal searches.

The collection's temporal range or the temporal range of the granules in the collection can be searched. `options[temporal][limit_to_granules]=true` will indicate that the temporal search should find collections based on the minimum and maximum values of each collection's granules' temporal range. If a collection does not have any granules it will search the collection's temporal range.

If a temporal range search is performed, the search results will be sorted by the temporal overlap across all ranges provided, with usage score being the tie-breaker. If a keyword search is performed in conjunction with the temporal range search, search results are first sorted by relevancy score, then by temporal overlap, then usage score. If a keyword search is used in conjunction with usage-score sort key, the usage-score will be used instead of relevancy score.

#### <a name="c-project"></a> Find collections by project

Note: An alias for the parameter 'project' is 'campaign'. As such 'campaign' can be used in place of 'project'.

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'project' param value

     curl "%CMR-ENDPOINT%/collections?project\[\]=ESI"

Find collections matching any of the 'project' param values

     curl "%CMR-ENDPOINT%/collections?project\[\]=ESI&project\[\]=EVI&project\[\]=EPI"

Find collections that match all of the 'project' param values

     curl "%CMR-ENDPOINT%/collections?project\[\]=ESI&project\[\]=EVI&project\[\]=EPI&options\[project\]\[and\]=true"

#### <a name="c-consortium"></a> Find collections by consortium

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'consortium' param value

     curl "%CMR-ENDPOINT%/collections?consortium\[\]=CWIC"

Find collections matching any of the 'consortium' param values

     curl "%CMR-ENDPOINT%/collections?consortium\[\]=CWIC&consortium\[\]=FEDEO&consortium\[\]=CEOS"

Find collections that match all of the 'consortium' param values

     curl "%CMR-ENDPOINT%/collections?consortium\[\]=CWIC&consortium\[\]=FEDEO&consortium\[\]=CEOS&options\[consortium\]\[and\]=true"

#### <a name="c-updated-since"></a> Find collections by updated_since

  Find collections which have revision date starting at or after 'updated_since' param value

     curl "%CMR-ENDPOINT%/collections?updated_since=2014-05-08T20:06:38.331Z"

#### <a name="c-created-at"></a> Find collections by created_at

 This supports option `and`.

 Find collections which were created within the ranges of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/collections?created_at\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z&created_at\[\]=2015-01-01T10:00:00Z,"

#### <a name="c-with-new-granules"></a> Find collections with new granules

  This supports option `and`.

  Find collections containing granules added within the range of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/collections?has_granules_created_at\[\]=2015-01-01T10:00:00Z,"

#### <a name="c-with-revised-granules"></a> Find collections with granules revised inside of a given range

  This supports option `and`.

  Find collections containing granules created or updated within the range of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/collections?has_granules_revised_at\[\]=2015-01-01T10:00:00Z,"

#### <a name="c-revision-date"></a> Find collections by revision_date

  This supports option `and`.

  Find collections which have revision date within the ranges of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/collections?revision_date\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z&revision_date\[\]=2015-01-01T10:00:00Z,"

#### <a name="c-processing-level-id"></a> Find collections by processing\_level\_id

This supports `pattern` and `ignore_case`.

Find collections matching 'processing_level_id'

     curl "%CMR-ENDPOINT%/collections?processing_level_id\[\]=1B"

Find collections matching any of the 'processing\_level\_id' param values

     curl "%CMR-ENDPOINT%/collections?processing_level_id\[\]=1B&processing_level_id\[\]=2B"

The alias 'processing_level' also works for searching by processing level id.

#### <a name="c-platform"></a> Find collections by platform

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'platform' param value

     curl "%CMR-ENDPOINT%/collections?platform\[\]=1B"

Find collections matching any of the 'platform' param values

     curl "%CMR-ENDPOINT%/collections?platform\[\]=1B&platform\[\]=2B"

#### <a name="c-instrument"></a> Find collections by instrument

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'instrument' param value

     curl "%CMR-ENDPOINT%/collections?instrument\[\]=1B"

Find collections matching any of the 'instrument' param values

     curl "%CMR-ENDPOINT%/collections?instrument\[\]=1B&instrument\[\]=2B"

#### <a name="c-sensor"></a> Find collections by sensor.

Sensor search is deprecated and should be replaced with instrument. Sensors are now child instruments on an instrument.

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'sensor' param value

     curl "%CMR-ENDPOINT%/collections?sensor\[\]=1B"

Find collections matching any of the 'sensor' param values

     curl "%CMR-ENDPOINT%/collections?sensor\[\]=1B&sensor\[\]=2B"

#### <a name="c-spatial-keyword"></a> Find collections by spatial\_keyword

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'spatial_keyword' param value

     curl "%CMR-ENDPOINT%/collections?spatial_keyword\[\]=DC"

Find collections matching any of the 'spatial_keyword' param values

     curl "%CMR-ENDPOINT%/collections?spatial_keyword\[\]=DC&spatial_keyword\[\]=LA"

#### <a name="c-science-keywords"></a> Find collections by science_keywords

This supports option _or_. Subfields of science_keywords search parameter include: category, topic, term, variable-level-1, variable-level-2, variable-level-3 and detailed-variable.

Find collections matching 'science_keywords' param value

     curl "%CMR-ENDPOINT%/collections?science_keywords\[0\]\[category\]=Cat1"

Find collections matching multiple 'science_keywords' param values, default is :and

     curl "%CMR-ENDPOINT%/collections?science_keywords\[0\]\[category\]=Cat1&science_keywords\[0\]\[topic\]=Topic1&science_keywords\[1\]\[category\]=Cat2"

#### <a name="c-twod-coordinate-system"></a> Find collections by two\_d\_coordinate\_system\_name

This supports searching by name of the two-dimensional tiling system for the collection. These are the valid values for two_d_coordinate_system aka TilingIdentificationSystem: `CALIPSO`, `MISR`, `MODIS Tile EASE`, `MODIS Tile SIN`, `WELD Alaska Tile`, `WELD CONUS Tile`, `WRS-1`, `WRS-2` and `Military Grid Reference System`.

This search parameter supports pattern. two\_d\_coordinate\_system\[name\] param is an alias of two\_d\_coordinate\_system\_name, but it does not support pattern.

  Find collections matching 'two\_d\_coordinate\_system\_name' param value

    curl "%CMR-ENDPOINT%/collections?two_d_coordinate_system_name\[\]=CALIPSO"

  Find collections matching any of the 'two\_d\_coordinate\_system\_name' param values

    curl "%CMR-ENDPOINT%/collections?two_d_coordinate_system_name\[\]=CALIPSO&two_d_coordinate_system_name\[\]=MISR"

#### <a name="c-collection-data-type"></a> Find collections by collection\_data\_type

Supports ignore_case and the following aliases for "NEAR\_REAL\_TIME": "near\_real\_time", "nrt", "NRT", "near real time", "near-real time", "near-real-time", "near real-time".

  Find collections matching 'collection\_data\_type' param value

     curl "%CMR-ENDPOINT%/collections?collection_data_type\[\]=NEAR_REAL_TIME"

  Find collections matching any of the 'collection\_data\_type' param values

     curl "%CMR-ENDPOINT%/collections?collection_data_type\[\]=NEAR_REAL_TIME&collection_data_type\[\]=OTHER"

#### <a name="c-collection-progress"></a> Find collections by collection\_progress

Supports ignore_case and pattern matching.

Valid values: ACTIVE, PLANNED, COMPLETE, DEPRECATED, NOT PROVIDED, PREPRINT, INREVIEW, SUPERSEDED

**NOTE:** When the non-operational collection filter is enabled (feature flag), collections with PLANNED, DEPRECATED, PREPRINT, and INREVIEW status are excluded from default search results. Use `include-non-operational=true` to include them. The default filter is automatically bypassed when searching by collection identifiers (`concept_id`, `entry_id`, `entry_title`, `short_name` with `version`, `native_id`, or `doi`), allowing you to find specific collections regardless of their progress status. However, if you explicitly set `include-non-operational=false` (or `true`), that explicit setting takes precedence even when using identifier parameters.

  Find collections matching 'collection\_progress' param value

     curl "%CMR-ENDPOINT%/collections?collection_progress=ACTIVE"

  Find collections matching any of the 'collection\_progress' param values

     curl "%CMR-ENDPOINT%/collections?collection_progress\[\]=ACTIVE&collection_progress\[\]=PLANNED"

  Find collections with provisional status

     curl "%CMR-ENDPOINT%/collections?collection_progress\[\]=PREPRINT&collection_progress\[\]=INREVIEW"

  Find collections using pattern matching

     curl "%CMR-ENDPOINT%/collections?collection_progress=*RE*&options\[collection_progress\]\[pattern\]=true"

**Controlling non-operational collection filtering with `include_non_operational`:**

The `include_non_operational` parameter controls whether non-operational collections are included when the filter is enabled. Valid values: `true`, `false`

  Include non-operational collections in search results

     curl "%CMR-ENDPOINT%/collections?include_non_operational=true"

  Explicitly exclude non-operational collections even when searching by identifier

     curl "%CMR-ENDPOINT%/collections?concept_id=C1200000000-PROV1&include_non_operational=false"

#### <a name="c-granule-data-format"></a> Find collections by format of data in granules

   Find collections matching 'granule_data_format' param value

    curl "%CMR-ENDPOINT%/collections?granule_data_format=NetCDF"

#### <a name="c-online-only"></a> Find collections by online_only

Find collections matching 'online_only' param value, online_only is a legacy parameter and is an alias of downloadable.

    curl "%CMR-ENDPOINT%/collections?online_only=true"

#### <a name="c-downloadable"></a> Find collections by downloadable

A collection is downloadable when it contains at least one RelatedURL that is a DistributionURL of type GETDATA.

    curl "%CMR-ENDPOINT%/collections?downloadable=true"

#### <a name="c-browse-only"></a> Find collections by browse_only

`browse_only` is a legacy alias of `browsable`. They return the same search results.

    curl "%CMR-ENDPOINT%/collections?browse_only=true"

#### <a name="c-browsable"></a> Find collections by browsable

A collection is browsable when it contains at least one RelatedURL with a VisualizationURL URLContentType.

    curl "%CMR-ENDPOINT%/collections?browsable=true"

#### <a name="c-keyword"></a> Find collections by keyword (free text) search

Keyword searches are case insensitive and support wild cards ? and *, in which '\*' matches zero or more characters and '?' matches any single character. There is a limit of 30 wild cards allowed in keyword searches. Within 30 wild cards, there's also limit on the max keyword
string length. The longer the max keyword string length, the less number of keywords with wild cards allowed.

The following searches on "alpha", "beta" and "g?mma" individually and returns the collections that contain all these individual words
in the keyword fields that are indexed. Note: these words don't have to exist in the same keyword field, but they have to exist as a
space (or one of special character delimiter CMR uses) delimited word.

    curl "%CMR-ENDPOINT%/collections?keyword=alpha%20beta%20g?mma"

We also support keyword phrase search. The following searches on "alpha beta g?mma" as a phrase and returns the collections with
one or more indexed keyword field values that contain the phrase.

    curl "%CMR-ENDPOINT%/collections?keyword=\"alpha%20beta%20g?mma\""

Note: Currently we only support either keyword, or single keyword phrase search. We don't support mix of keyword and keyword phrase search and we don't support multiple keyword phrase search. These searches like the following will be rejected with error: <error>keyword phrase mixed with keyword, or another keyword-phrase are not supported. keyword phrase has to be enclosed by two escaped double quotes.</error>

   curl "%CMR-ENDPOINT%/collections?keyword=\"phrase%20one\"%20\"phrase%20two\"" (multiple phrase case)
   curl "%CMR-ENDPOINT%/collections?keyword=\"phrase%20one\"%20\word2" (mix of phrase and word case)
   curl "%CMR-ENDPOINT%/collections?keyword=\"phrase%20one" (missing one \" case)

Also \" is reserved for phrase boundary. For literal double quotes, use \\\". For example, to search for 'alpha "beta" g?mma' phrase, do the following:

    curl "%CMR-ENDPOINT%/collections?keyword=\"alpha%20\\\"beta\\\"%20g?mma\""

To search on 'alpha', '"beta"', 'g?mma' individually, do the following:

    curl "%CMR-ENDPOINT%/collections?keyword=alpha%20\\\"beta\\\"%20g?mma"

The following fields are indexed for keyword and keyword phrase search:

* Concept ID
* DOI value
* Provider ID
* Entry ID
* Entry title
* Data type
* Short name
* Summary
* Version ID
* Version description
* Processing level ID
* Science keywords
* Ancillary keywords
* Directory Names
* Archive centers
* Additional attribute names, data types, values, and descriptions
* detailed locations
* Spatial keywords
* Temporal keywords
* ISO Topic Categories
* Project short and long names
* Platform short and long names
* Instrument short names, long names, and techniques
* Sensor short names, long names, and techniques
* Characteristic names, descriptions and values
* TwoD coordinate system names
* Author

#### <a name="c-provider"></a> Find collections by provider

This parameter supports `pattern` and `ignore_case`.

Find collections matching 'provider' param value

    curl "%CMR-ENDPOINT%/collections?provider=ASF"

Find collections matching any of the 'provider' param values

    curl "%CMR-ENDPOINT%/collections?provider=ASF&provider=LARC"

#### <a name="c-native-id"></a> Find collections by native_id

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'native_id' param value

    curl "%CMR-ENDPOINT%/collections?native_id=nativeid1"

Find collections matching any of the 'native_id' param values

    curl "%CMR-ENDPOINT%/collections?native_id[]=nativeid1&native_id[]=nativeid2"

#### <a name="c-short-name"></a> Find collections by short name

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching any of the 'short\_name' param values

    curl "%CMR-ENDPOINT%/collections?short_name=DEM_100M&short_name=MINIMAL"

Find collections matching 'short\_name' param value with a pattern

    curl "%CMR-ENDPOINT%/collections?short_name=D*&options[short_name][pattern]=true"

#### <a name="c-version"></a> Find collections by version

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching the given 'short\_name' and 'version' param values

    curl "%CMR-ENDPOINT%/collections?short_name=DEM_100M&version=1"

Find collections matching the given 'short\_name' and any of the 'version' param values

    curl "%CMR-ENDPOINT%/collections?short_name=dem_100m&version=1&version=2"

#### <a name="c-tag-parameters"></a> Find collections by tag parameters

Collections can be found by searching for associated tags. The following tag parameters are supported.

* tag_key
  supports options: `pattern`
* tag_originator_id
  supports options: `pattern`
* tag_data
  supports options: `pattern`

`exclude` parameter can be used with tag_key to exclude any collections that are associated with the specified tag key from the search result.

Find collections matching tag key.

    curl "%CMR-ENDPOINT%/collections?tag_key=org.ceos.wgiss.cwic.quality"

Find collections with exclude tag key.

    curl "%CMR-ENDPOINT%/collections?exclude\[tag_key\]=gov.nasa.earthdata.search.cwic"

Find collections with tag_data in the form of tag_data[tag_key]=tag_value. It finds collections match on both tag_key and tag_value, which is the string data that is associated with the collection during tag association.

    curl "%CMR-ENDPOINT%/collections?tag_data[org.ceos.wgiss.cwic.quality]=foo"

#### <a name="c-variable-parameters"></a> Find collections by variable parameters

Collections can be found by searching for associated variables. The following variable parameters are supported.

* variable_name
  supports options: `pattern`, `ignore_case` and `and`
* variable_native_id
  supports options: `pattern`, `ignore_case` and `and`
* variable_concept_id
  supports options: `and`

Find collections matching variable name.

    curl "%CMR-ENDPOINT%/collections?variable_name=totcldh2ostderr"

Find collections matching variable native id.

    curl "%CMR-ENDPOINT%/collections?variable_native_id\[\]=var1&variable_native_id\[\]=var2"

Find collections matching variable concept id.

    curl "%CMR-ENDPOINT%/collections?variable_concept_id\[\]=V100000-PROV1"

#### <a name="c-variables"></a> Find collections by hierarchical variables

This supports option _or_.

Find collections matching 'variables-h' param value

     curl "%CMR-ENDPOINT%/collections?variables-h\[0\]\[measurement\]=M1"

Find collections matching multiple 'variables-h' param values, default is :and

     curl "%CMR-ENDPOINT%/collections?variables-h\[0\]\[measurement\]=M1&variables-h\[0\]\[variable\]=Var1&variables-h\[1\]\[measurement\]=M2"

#### <a name="c-service-parameters"></a> Find collections by service parameters

Collections can be found by searching for associated services. The following service parameters are supported.

* service_name
  supports options: `pattern`, `ignore_case` and `and`
* service_type
  supports options: `pattern` and `ignore_case`
* service_concept_id
  supports options: `and`

Find collections matching service name.

    curl "%CMR-ENDPOINT%/collections?service_name=AtlasNorth"

Find collections matching service type. In this example, find all collections matching service type of Harmony or OPeNDAP.

    curl "%CMR-ENDPOINT%/collections?service_type\[\]=Harmony&service_type\[\]=OPeNDAP"

Find collections matching service concept id.

    curl "%CMR-ENDPOINT%/collections?service_concept_id\[\]=S100000-PROV1&service_concept_id\[\]=S12345-PROV1"

#### <a name="c-tool-parameters"></a> Find collections by tool parameters

Collections can be found by searching for associated tools. The following tool parameters are supported.

* tool_name
  supports options: `pattern`, `ignore_case` and `and`
* tool_type
  supports options: `pattern` and `ignore_case`
* tool_concept_id
  supports options: `and`

Find collections matching tool name.

    curl "%CMR-ENDPOINT%/collections?tool_name=NASA_GISS_Panoply"

Find collections matching tool type. In this example, find all collections matching tool type of Downloadable Tool or Web User Interface.

    curl "%CMR-ENDPOINT%/collections?tool_type\[\]=Downloadable%20Tool&tool_type\[\]=Web%20User%20Interface"

Find collections matching tool concept id.

    curl "%CMR-ENDPOINT%/collections?tool_concept_id\[\]=TL100000-PROV1&tool_concept_id\[\]=TL12345-PROV1"

#### <a name="c-spatial"></a> Find collections by Spatial

##### <a name="c-polygon"></a> Polygon

Polygon points are provided in counter-clockwise order. The last point should match the first point to close the polygon. The values are listed comma separated in longitude latitude order, i.e. lon1, lat1, lon2, lat2, lon3, lat3, and so on.

The polygon parameter could be either "polygon", for single polygon search:

   curl "%CMR-ENDPOINT%/collections?polygon=10,10,30,10,30,20,10,20,10,10"

or "polygon[]", for single or multiple polygon search. It supports the and/or option as shown below. Default option is "and", i.e. it will match both the first polygon and the second polygon.

    curl "%CMR-ENDPOINT%/collections?polygon[]=10,10,30,10,30,20,10,20,10,10"

    curl "%CMR-ENDPOINT%/collections?polygon[]=10,10,30,10,30,20,10,20,10,10&polygon[]=11,11,31,11,31,21,11,21,11,11"

    curl "%CMR-ENDPOINT%/collections?polygon[]=10,10,30,10,30,20,10,20,10,10&polygon[]=11,11,31,11,31,21,11,21,11,11&options[polygon][or]=true"

Note: if you use "polygon" for multiple polygon search, it won't work because only the last polygon parameter will take effect.

##### <a name="c-bounding-box"></a> Bounding Box

Bounding boxes define an area on the earth aligned with longitude and latitude. The Bounding box parameters must be 4 comma-separated numbers: lower left longitude, lower left latitude, upper right longitude, upper right latitude.
This parameter supports the and/or option as shown below.

    curl "%CMR-ENDPOINT%/collections?bounding_box[]=-10,-5,10,5"

    curl "%CMR-ENDPOINT%/collections?bounding_box[]=-10,-5,10,5&bounding_box[]=-11,-6,11,6&options[bounding_box][or]=true"

##### <a name="c-point"></a> Point

Search using a point involves using a pair of values representing the point coordinates as parameters. The first value is the longitude and second value is the latitude. This parameter supports the and/or option as shown below.

    curl "%CMR-ENDPOINT%/collections?point=100,20"

    curl "%CMR-ENDPOINT%/collections?point=100,20&point=80,20&options[point][or]=true"

##### <a name="c-line"></a> Line

Lines are provided as a list of comma separated values representing coordinates of points along the line. The coordinates are listed in the format lon1, lat1, lon2, lat2, lon3, lat3, and so on. This parameter supports the and/or option as shown below.

    curl "%CMR-ENDPOINT%/collections?line[]=-0.37,-14.07,4.75,1.27,25.13,-15.51"

    curl "%CMR-ENDPOINT%/collections?line[]=-0.37,-14.07,4.75,1.27,25.13,-15.51&line[]=-1.37,-15.07,5.75,2.27,26.13,-16.51&options[line][or]=true"

##### <a name="c-circle"></a> Circle

Circle defines a circle area on the earth with a center point and a radius. The center parameters must be 3 comma-separated numbers: longitude of the center point, latitude of the center point, radius of the circle in meters. The circle center cannot be on North or South pole. The radius of the circle must be between 10 and 6,000,000.
This parameter supports the and/or option as shown below.

    curl "%CMR-ENDPOINT%/collections?circle[]=-87.629717,41.878112,1000"

    curl "%CMR-ENDPOINT%/collections?circle[]=-87.629717,41.878112,1000&circle[]=-75,41.878112,1000&options[circle][or]=true"

Note: A query could consist of multiple spatial parameters of different types, two bounding boxes and a polygon for example. If multiple spatial parameters are present, all the parameters irrespective of their type are ANDed in a query. So, if a query contains two bounding boxes and a polygon for example, it will return only those collections which intersect both the bounding boxes and the polygon.

#### <a name="c-shapefile"></a> Find collections by shapefile

A shapefile can be uploaded with a query to restrict results to those that overlap the geometry in the shapefile. Note that unlike the spatial parameters, geometry in the shapefile is ORed together, not ANDed. So if a collection overlaps _any_ of the geometry in the shapefile it will match. Note also that the `shapefile` parameter supports shapefiles containing polygons with holes.

Currently the only supported shapefile formats are ESRI, KML, and GeoJSON. For ESRI all the sub-files (*.shp, *.shx, etc.) must be uploaded in a single zip file.

The following limits apply to uploaded shapefiles:
* Shapefiles are limited in size to 1,000,000 bytes.
* Shapefiles are limited to 500 features
* Shapefiles are limited to 5000 points.
* Shapefile geometries with precision greater than 7 significant digits should ensure their points are at least 1 meter apart.

Regarding polygon ring winding, ESRI shapefiles **must** follow the ESRI standard, i.e., exterior (boundary) rings are clockwise, and holes are counter-clockwise. GeoJSON **must** follow the RFC7946 specification, i.e., exterior rings are counterclockwise, and holes are clockwise. KML **must** follow the KML 2.2 specification, i.e., _all_ polygon rings are counter-clockwise.

Shapefile upload is only supported using POST with `multipart/form-data` and the mime type for the shapefile must be given as `application/shapefile+zip`, `application/geo+json`, or `application/vnd.google-earth.kml+xml`.

Examples:

**ESRI Shapefile**

    curl -XPOST "%CMR-ENDPOINT%/collections" -F "shapefile=@box.zip;type=application/shapefile+zip" -F "provider=PROV1"

**GeoJSON**

    curl -XPOST "%CMR-ENDPOINT%/collections" -F "shapefile=@box.geojson;type=application/geo+json" -F "provider=PROV1"

**KML**

    curl -XPOST "%CMR-ENDPOINT%/collections" -F "shapefile=@box.kml;type=application/vnd.google-earth.kml+xml" -F "provider=PROV1"

Internally a WGS 84 Coordinate Reference System (CRS) is used. The system will attempt to transform shapefile geometry that uses a different CRS, but this is not guaranteed to work and the request will be rejected if a suitable transformation is not found.

**NOTE:** This is an experimental feature and may not be enabled in all environments.

#### <a name="c-shapefile-simplification"></a> Simplifying shapefiles during collection search
Shapefiles are limited to 5000 points by default. A user using a shapefile with more points than the CMR supported limit can use the `simplify-shapefile` parameter to request that the CMR try to simplify (reduce the number of points) the shapefile so that it is under the limit.

Example:

    curl -XPOST "%CMR-ENDPOINT%/collections" -F "simplify-shapefile=true"  -F "shapefile=@africa.zip;type=application/shapefile+zip" -F "provider=PROV1"

Note that the simplification process attempts to preserve topology, i.e., the relationship between polygon outer boundaries and holes. The process uses the [Douglas-Peucker algorithm](https://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm) and as such may result in geometries with less coverage than the original shapefile and potentially a loss of matching results.

The simplification may fail if the process cannot reduce the number of points in the file to below the limit. Also the simplification only reduces the number of points in the file, so a shapefile will still fail if the file size is too large or there are too many features.

#### <a name="c-additional-attribute"></a> Find collections by additional attribute

Find an additional attribute with name "PERCENTAGE" only

    curl "%CMR-ENDPOINT%/collections?attribute\[\]=PERCENTAGE"

Find an additional attribute with name "PERCENTAGE" of type float with value 25.5

    curl "%CMR-ENDPOINT%/collections?attribute\[\]=float,PERCENTAGE,25.5"

Find an additional attribute with name "PERCENTAGE" of type float in range 25.5 - 30.

    curl "%CMR-ENDPOINT%/collections?attribute\[\]=float,PERCENTAGE,25.5,30"

Find an additional attribute with name "PERCENTAGE" of type float with min value 25.5.

    curl "%CMR-ENDPOINT%/collections?attribute\[\]=float,PERCENTAGE,25.5,"

Find an additional attribute with name "PERCENTAGE" of type float with max value 30.

    curl "%CMR-ENDPOINT%/collections?attribute\[\]=float,PERCENTAGE,,30"

Find an additional attribute with name "X\Y\Z" with value 7.

    curl "%CMR-ENDPOINT%/collections?attribute\[\]=float,X\Y\Z,7"

Find an additional attribute with name "MISSION_NAME" with value "Big Island, HI".

    curl "%CMR-ENDPOINT%/collections?attribute\[\]=string,MISSION_NAME,Big Island\, HI"

Multiple attributes can be provided. The default is for collections to match all the attribute parameters. This can be changed by specifying `or` option with `options[attribute][or]=true`.

For additional attribute range search, the default is inclusive on the range boundaries. This can be changed by specifying `exclude_boundary` option with `options[attribute][exclude_boundary]=true`.

#### <a name="c-author"></a> Find collections by author

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching the given 'author' values

    curl "%CMR-ENDPOINT%/collections?author=*JPL*&options[author][pattern]=true"

#### <a name="c-has-granules"></a> Find collections with or without granules

When `has_granules` is set to "true" or "false", results will be restricted to collections with or without granules, respectively.

    curl "%CMR-ENDPOINT%/collections?has_granules=true"

#### <a name="c-has-granules-or-cwic"></a> Find collections with or without granules, or the collection with CWIC consortium.

The `has_granules_or_cwic` parameter can be set to "true" or "false". When true, the results will be restricted to collections with granules or with CWIC consortium. When false, will return any collections without granules.

    curl "%CMR-ENDPOINT%/collections?has_granules_or_cwic=true"

**Note:** this parameter will soon be retired in favor of a replacement parameter found below, `have_granules_or_opensearch`.

#### <a name="c-has-granules-or-opensearch"></a> Find collections with or without granules, or the collection is tagged with the configured OpenSearch tag.

The `has_granules_or_opensearch` parameter can be set to "true" or "false". When true, the results will be restricted to collections with granules or with any of the configured OpenSearch consortiums, which are CWIC,FEDEO,GEOSS,CEOS and EOSDIS. When false, will return any collections without granules.

    curl "%CMR-ENDPOINT%/collections?has_granules_or_opensearch=true"

#### <a name="c-has-opendap-url"></a> Find collections with or without an OPeNDAP service RelatedURL.

    curl "%CMR-ENDPOINT%/collections?has_opendap_url=true"

#### <a name="c-cloud-hosted"></a> Find collections with data that is hosted in the cloud.

The `cloud_hosted` parameter can be set to "true" or "false". When true, the results will be restricted to collections that have a `DirectDistributionInformation` element or have been tagged with `gov.nasa.earthdatacloud.s3`.

    curl "%CMR-ENDPOINT%/collections?cloud_hosted=true"

#### <a name="c-standard-product"></a> Find collections that are standard products.

The `standard_product` parameter can be set to "true" or "false". When true, the results will be restricted to collections that have `StandardProduct` element being true or collections that don't have `StandardProduct` element set and have been tagged with `gov.nasa.eosdis.standardproduct`.

    curl "%CMR-ENDPOINT%/collections?standard_product=true"

#### <a name="sorting-collection-results"></a> Sorting Collection Results

Collection results are sorted by ascending entry title by default when a search does not result in a score.

If a keyword search is performed then the search results will be sorted by:

  * Relevance Score (descending), binned to the nearest 0.2. For example a score of 0.75 and 0.85 will be considered equal for sorting purposes.
  * Temporal Overlap (descending), if one or more temporal ranges are provided.
  * EMS Community Usage Score (descending), binned to the nearest 400. For example, usage of 400 and 500 will be considered equal for sorting purposes. The usage score comes from EMS metrics which contain access counts of the collections by short name and version. The metrics are ingested into the CMR.
  * Collection End Date (descending), with ongoing collections defaulting to today.
  * Humanized processing level Id (descending)

If a temporal range search is performed, the search results will be sorted by temporal overlap percentage over all ranges provided.

One or more sort keys can be specified using the `sort_key[]` parameter. The order used impacts searching. Fields can be prepended with a `-` to sort in descending order. Ascending order is the default but `+` (Note: `+` must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Collection Sort Keys

  * `entry_title`
  * `dataset_id` - alias for entry_title
  * `short_name`
  * `entry_id`
  * `start_date`
  * `end_date`
  * `platform`
  * `instrument`
  * `sensor`
  * `provider`
  * `revision_date`
  * `score` - document relevance score, defaults to descending. See [Document Scoring](#document-scoring).
  * `has_granules` - Sorts collections by whether they have granules or not. Collections with granules are sorted before collections without granules.
  * `has_granules_or_cwic` - Sorts collections by whether they have granules or CWIC consortium. Collections with granules or CWIC consortium are sorted before collections without granules or a CWIC consortium.
  * `usage_score` - Sorts collection by usage. The usage score comes from the EMS metrics, which are ingested into the CMR.
  * `ongoing` - Sorts collection by fuzzy collection end-date in relation to ongoing-days configured. Any end-date after today, minus the configured ongoing-days (30 by default), is considered ongoing. Any end-date before that is not ongoing.
  * `create-data-date` - Sorts collections by the earliest CREATE date in DataDates field.

Examples of sorting by start_date in descending(Most recent data first) and ascending orders(Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/collections?sort_key\[\]=-start_date"
    curl "%CMR-ENDPOINT%/collections?sort_key\[\]=%2Bstart_date"

### <a name="document-scoring"></a> Document Scoring

Collection search results are scored when any of the following parameters are searched:

* keyword
* platform
* instrument
* sensor
* two_d_coordinate_system_name
* science_keywords
* project
* processing_level_id
* data_center
* archive_center

Any terms found in the those parameters are used to score results across other fields in the search results. A term is a contiguous set of characters not containing whitespace. A series of filters are executed against each document. Each of these has an associated boost value. The boost values of all the filters that match a given document are multiplied together to get the final document score.

The terms are separated between keywords found in the keywords field and additional terms found in the fields listed above.

The filters are case insensitive, support wild-cards * and ?, and are given below:

1. All keyword terms are contained in the long-name field OR one of the keyword terms exactly matches the short-name field OR one of the additional terms is contained in the short-name or long-name - weight 1.4
2. The keyword term field is a single string that exactly matches the entry-id field OR one of the additional terms is contained in the entry-id - weight 1.4
3. All keyword terms are contained in the Project/long-name field OR one of the keyword terms exactly matches the Project/short-name field OR one of the additional terms is contained in the Project/short-name or Project/long-name - weight 1.3
4. All keyword terms are contained in the Platform/long-name field OR one of the terms exactly matches the Platform/short-name field OR one of the additional terms is contained in the Platform/short-name or Platform/long-name - weight 1.3
5. All keyword terms are contained in the Platform/Instrument/long-name field OR one of the keyword terms exactly matches the Platform/Instrument/short-name field OR one of the additional terms is contained in the Platform/Instrument/short-name or Platform/Instrument/long-name - weight 1.2
6. All keyword terms are contained in the Platform/Instrument/Sensor/long-name field OR one of the keyword terms exactly matches the Platform/Instrument/Sensor/short-name field OR one of the additional terms is contained in the Platform/Instrument/Sensor/short-name or Platform/Instrument/Sensor/long-name - weight 1.2
7. The keyword term field is a single string that exactly matches the science-keyword field OR an additional term is contained in the science-keyword field - weight 1.2
8. The keyword term field is a single string that exactly matches the spatial-keyword field OR an additional term is contained in the spatial-keyword field - weight 1.1
9. The keyword term field is a single string that exactly matches the temporal-keyword field OR an additional term is contained in the temporal-keyword field - weight 1.1

#### <a name="retrieving-all-revisions-of-a-collection"></a> Retrieving All Revisions of a Collection

In addition to retrieving the latest revision for a collection parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference and UMM JSON response formats are supported for all revision searches. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true".

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

### <a name="deleted-collections"></a> Find collections that have been deleted after a given date

To support metadata harvesting, a harvesting client can search CMR for collections that are deleted after a given date. The only search parameter supported is `revision_date` and its format is slightly different from the `revision_date` parameter in regular collection search in that only one revision date can be provided and it can only be a starting date, not a date range. The only supported result format is xml references. The response is the references to the highest non-tombstone collection revisions of the collections that are deleted. e.g.

The following search will return the last revision of the collections that are deleted since 01/20/2017.

    curl -i "%CMR-ENDPOINT%/deleted-collections?revision_date=2017-01-20T00:00:00Z&pretty=true"

__Example Response__

```
HTTP/1.1 200 OK
Content-Type: application/xml; charset=UTF-8
CMR-Hits: 3

<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>3</hits>
    <took>10</took>
    <references>
        <reference>
            <name>Dataset1</name>
            <id>C1200000001-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/C1200000001-PROV1/4</location>
            <revision-id>4</revision-id>
        </reference>
        <reference>
            <name>Dataset2</name>
            <id>C1200000002-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/C1200000002-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
        <reference>
            <name>Dataset3</name>
            <id>C1200000003-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/C1200000003-PROV1/8</location>
            <revision-id>8</revision-id>
        </reference>
    </references>
</results>
```