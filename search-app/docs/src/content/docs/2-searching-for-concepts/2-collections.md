---
title: Collections
description: Provides information on how to use the CMR API with collections.
sidebar:
  label: Collections
  order: 2
---

## <a name="collection-search-by-parameters"></a> Collection Search Examples

### <a name="find-all-collections"></a> Find all collections

    curl "%CMR-ENDPOINT%/collections"

Collection search results are paged. See [Paging Details](#paging-details) for more information on how to page through collection search results.

### <a name="c-concept-id"></a> Find collections by concept id

A CMR concept id is in the format `<concept-type-prefix> <unique-number> "-" <provider-id>`

  * `concept-type-prefix` is a single capital letter prefix indicating the concept type. "C" is used for collections
  * `unique-number` is a single number assigned by the CMR during ingest.
  * `provider-id` is the short name for the provider. i.e. "LPDAAC\_ECS"

Example: `C123456-LPDAAC_ECS`

    curl "%CMR-ENDPOINT%/collections?concept_id\[\]=C123456-LPDAAC_ECS"

### <a name="c-doi-value"></a> Find collections by doi value

  Find a collection matching a collection doi value. Note more than one doi value may be supplied.

    curl "%CMR-ENDPOINT%/collections?doi\[\]=doiValue"

### <a name="c-echo-collection-id"></a> Find collections by echo collection id

  Find a collection matching a echo collection id. Note more than one echo collection id may be supplied.

     curl "%CMR-ENDPOINT%/collections?echo_collection_id\[\]=C1000000001-CMR_PROV2"

### <a name="c-provider-short-name"></a> Find collections by provider short name

This searches for collections whose provider matches the given provider short names. This supports `ignore_case` option, but not the `pattern` option.

    curl "%CMR-ENDPOINT%/collections?provider_short_name\[\]=SHORT_5&options\[provider_short_name\]\[ignore_case\]=true"

### <a name="c-entry-title"></a> Find collections by entry title

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

### <a name="c-entry-id"></a> Find collections by entry id

Find collections matching 'entry_id' param value such as SHORT_V5

    curl "%CMR-ENDPOINT%/collections?entry_id\[\]=SHORT_V5"

### <a name="c-archive-center"></a> Find collections by archive center

This supports `pattern` and `ignore_case`.

Find collections matching 'archive_center' param value

    curl "%CMR-ENDPOINT%/collections?archive_center\[\]=LARC"
    curl "%CMR-ENDPOINT%/collections?archive_center=PROV1+AC"

Find collections matching any of the 'archive_center' param values

     curl "%CMR-ENDPOINT%/collections?archive_center\[\]=Larc&archive_center\[\]=LARC"

### <a name="c-data-center"></a> Find collections by data center

This supports `pattern`, `and`, and `ignore_case`.

Find collections matching 'data_center' param value

    curl "%CMR-ENDPOINT%/collections?data_center\[\]=LARC"
    curl "%CMR-ENDPOINT%/collections?data_center=PROV1+AC"

Find collections matching any of the 'data_center' param values

     curl "%CMR-ENDPOINT%/collections?data_center\[\]=Larc&data_center\[\]=LARC"

### <a name="c-temporal"></a> Find collections with temporal

The temporal datetime has to be in yyyy-MM-ddTHH:mm:ssZ format.

    curl "%CMR-ENDPOINT%/collections?temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z,30,60&temporal\[\]=2000-01-01T10:00:00Z,,30&temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"

The first two values of the parameter together define the temporal bounds. See under [Temporal Range searches](#temporal-range-searches) for different ways of specifying the temporal bounds including ISO 8601.

For temporal range search, the default is inclusive on the range boundaries. This can be changed by specifying `exclude_boundary` option with `options[temporal][exclude_boundary]=true`. This option has no impact on periodic temporal searches.

The collection's temporal range or the temporal range of the granules in the collection can be searched. `options[temporal][limit_to_granules]=true` will indicate that the temporal search should find collections based on the minimum and maximum values of each collection's granules' temporal range. If a collection does not have any granules it will search the collection's temporal range.

If a temporal range search is performed, the search results will be sorted by the temporal overlap across all ranges provided, with usage score being the tie-breaker. If a keyword search is performed in conjunction with the temporal range search, search results are first sorted by relevancy score, then by temporal overlap, then usage score. If a keyword search is used in conjunction with usage-score sort key, the usage-score will be used instead of relevancy score.

### <a name="c-project"></a> Find collections by project

Note: An alias for the parameter 'project' is 'campaign'. As such 'campaign' can be used in place of 'project'.

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'project' param value

     curl "%CMR-ENDPOINT%/collections?project\[\]=ESI"

Find collections matching any of the 'project' param values

     curl "%CMR-ENDPOINT%/collections?project\[\]=ESI&project\[\]=EVI&project\[\]=EPI"

Find collections that match all of the 'project' param values

     curl "%CMR-ENDPOINT%/collections?project\[\]=ESI&project\[\]=EVI&project\[\]=EPI&options\[project\]\[and\]=true"

### <a name="c-consortium"></a> Find collections by consortium

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'consortium' param value

     curl "%CMR-ENDPOINT%/collections?consortium\[\]=CWIC"

Find collections matching any of the 'consortium' param values

     curl "%CMR-ENDPOINT%/collections?consortium\[\]=CWIC&consortium\[\]=FEDEO&consortium\[\]=CEOS"

Find collections that match all of the 'consortium' param values

     curl "%CMR-ENDPOINT%/collections?consortium\[\]=CWIC&consortium\[\]=FEDEO&consortium\[\]=CEOS&options\[consortium\]\[and\]=true"

### <a name="c-updated-since"></a> Find collections by updated_since

  Find collections which have revision date starting at or after 'updated_since' param value

     curl "%CMR-ENDPOINT%/collections?updated_since=2014-05-08T20:06:38.331Z"

### <a name="c-created-at"></a> Find collections by created_at

 This supports option `and`.

 Find collections which were created within the ranges of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/collections?created_at\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z&created_at\[\]=2015-01-01T10:00:00Z,"

### <a name="c-with-new-granules"></a> Find collections with new granules

  This supports option `and`.

  Find collections containing granules added within the range of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/collections?has_granules_created_at\[\]=2015-01-01T10:00:00Z,"

### <a name="c-with-revised-granules"></a> Find collections with granules revised inside of a given range

  This supports option `and`.

  Find collections containing granules created or updated within the range of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/collections?has_granules_revised_at\[\]=2015-01-01T10:00:00Z,"

### <a name="c-revision-date"></a> Find collections by revision_date

  This supports option `and`.

  Find collections which have revision date within the ranges of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/collections?revision_date\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z&revision_date\[\]=2015-01-01T10:00:00Z,"

### <a name="c-processing-level-id"></a> Find collections by processing\_level\_id

This supports `pattern` and `ignore_case`.

Find collections matching 'processing_level_id'

     curl "%CMR-ENDPOINT%/collections?processing_level_id\[\]=1B"

Find collections matching any of the 'processing\_level\_id' param values

     curl "%CMR-ENDPOINT%/collections?processing_level_id\[\]=1B&processing_level_id\[\]=2B"

The alias 'processing_level' also works for searching by processing level id.

### <a name="c-platform"></a> Find collections by platform

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'platform' param value

     curl "%CMR-ENDPOINT%/collections?platform\[\]=1B"

Find collections matching any of the 'platform' param values

     curl "%CMR-ENDPOINT%/collections?platform\[\]=1B&platform\[\]=2B"

### <a name="c-instrument"></a> Find collections by instrument

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'instrument' param value

     curl "%CMR-ENDPOINT%/collections?instrument\[\]=1B"

Find collections matching any of the 'instrument' param values

     curl "%CMR-ENDPOINT%/collections?instrument\[\]=1B&instrument\[\]=2B"

### <a name="c-sensor"></a> Find collections by sensor.

Sensor search is deprecated and should be replaced with instrument. Sensors are now child instruments on an instrument.

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'sensor' param value

     curl "%CMR-ENDPOINT%/collections?sensor\[\]=1B"

Find collections matching any of the 'sensor' param values

     curl "%CMR-ENDPOINT%/collections?sensor\[\]=1B&sensor\[\]=2B"

### <a name="c-spatial-keyword"></a> Find collections by spatial\_keyword

This supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'spatial_keyword' param value

     curl "%CMR-ENDPOINT%/collections?spatial_keyword\[\]=DC"

Find collections matching any of the 'spatial_keyword' param values

     curl "%CMR-ENDPOINT%/collections?spatial_keyword\[\]=DC&spatial_keyword\[\]=LA"

### <a name="c-science-keywords"></a> Find collections by science_keywords

This supports option _or_. Subfields of science_keywords search parameter include: category, topic, term, variable-level-1, variable-level-2, variable-level-3 and detailed-variable.

Find collections matching 'science_keywords' param value

     curl "%CMR-ENDPOINT%/collections?science_keywords\[0\]\[category\]=Cat1"

Find collections matching multiple 'science_keywords' param values, default is :and

     curl "%CMR-ENDPOINT%/collections?science_keywords\[0\]\[category\]=Cat1&science_keywords\[0\]\[topic\]=Topic1&science_keywords\[1\]\[category\]=Cat2"

### <a name="c-twod-coordinate-system"></a> Find collections by two\_d\_coordinate\_system\_name

This supports searching by name of the two-dimensional tiling system for the collection. These are the valid values for two_d_coordinate_system aka TilingIdentificationSystem: `CALIPSO`, `MISR`, `MODIS Tile EASE`, `MODIS Tile SIN`, `WELD Alaska Tile`, `WELD CONUS Tile`, `WRS-1`, `WRS-2` and `Military Grid Reference System`.

This search parameter supports pattern. two\_d\_coordinate\_system\[name\] param is an alias of two\_d\_coordinate\_system\_name, but it does not support pattern.

  Find collections matching 'two\_d\_coordinate\_system\_name' param value

    curl "%CMR-ENDPOINT%/collections?two_d_coordinate_system_name\[\]=CALIPSO"

  Find collections matching any of the 'two\_d\_coordinate\_system\_name' param values

    curl "%CMR-ENDPOINT%/collections?two_d_coordinate_system_name\[\]=CALIPSO&two_d_coordinate_system_name\[\]=MISR"

### <a name="c-collection-data-type"></a> Find collections by collection\_data\_type

Supports ignore_case and the following aliases for "NEAR\_REAL\_TIME": "near\_real\_time", "nrt", "NRT", "near real time", "near-real time", "near-real-time", "near real-time".

  Find collections matching 'collection\_data\_type' param value

     curl "%CMR-ENDPOINT%/collections?collection_data_type\[\]=NEAR_REAL_TIME"

  Find collections matching any of the 'collection\_data\_type' param values

     curl "%CMR-ENDPOINT%/collections?collection_data_type\[\]=NEAR_REAL_TIME&collection_data_type\[\]=OTHER"

### <a name="c-collection-progress"></a> Find collections by collection\_progress

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

### <a name="c-granule-data-format"></a> Find collections by format of data in granules

   Find collections matching 'granule_data_format' param value

    curl "%CMR-ENDPOINT%/collections?granule_data_format=NetCDF"

### <a name="c-online-only"></a> Find collections by online_only

Find collections matching 'online_only' param value, online_only is a legacy parameter and is an alias of downloadable.

    curl "%CMR-ENDPOINT%/collections?online_only=true"

### <a name="c-downloadable"></a> Find collections by downloadable

A collection is downloadable when it contains at least one RelatedURL that is a DistributionURL of type GETDATA.

    curl "%CMR-ENDPOINT%/collections?downloadable=true"

### <a name="c-browse-only"></a> Find collections by browse_only

`browse_only` is a legacy alias of `browsable`. They return the same search results.

    curl "%CMR-ENDPOINT%/collections?browse_only=true"

### <a name="c-browsable"></a> Find collections by browsable

A collection is browsable when it contains at least one RelatedURL with a VisualizationURL URLContentType.

    curl "%CMR-ENDPOINT%/collections?browsable=true"

### <a name="c-keyword"></a> Find collections by keyword (free text) search

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

### <a name="c-provider"></a> Find collections by provider

This parameter supports `pattern` and `ignore_case`.

Find collections matching 'provider' param value

    curl "%CMR-ENDPOINT%/collections?provider=ASF"

Find collections matching any of the 'provider' param values

    curl "%CMR-ENDPOINT%/collections?provider=ASF&provider=LARC"

### <a name="c-native-id"></a> Find collections by native_id

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'native_id' param value

    curl "%CMR-ENDPOINT%/collections?native_id=nativeid1"

Find collections matching any of the 'native_id' param values

    curl "%CMR-ENDPOINT%/collections?native_id[]=nativeid1&native_id[]=nativeid2"

### <a name="c-short-name"></a> Find collections by short name

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching any of the 'short\_name' param values

    curl "%CMR-ENDPOINT%/collections?short_name=DEM_100M&short_name=MINIMAL"

Find collections matching 'short\_name' param value with a pattern

    curl "%CMR-ENDPOINT%/collections?short_name=D*&options[short_name][pattern]=true"

### <a name="c-version"></a> Find collections by version

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching the given 'short\_name' and 'version' param values

    curl "%CMR-ENDPOINT%/collections?short_name=DEM_100M&version=1"

Find collections matching the given 'short\_name' and any of the 'version' param values

    curl "%CMR-ENDPOINT%/collections?short_name=dem_100m&version=1&version=2"

### <a name="c-tag-parameters"></a> Find collections by tag parameters

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

### <a name="c-variable-parameters"></a> Find collections by variable parameters

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

### <a name="c-variables"></a> Find collections by hierarchical variables

This supports option _or_.

Find collections matching 'variables-h' param value

     curl "%CMR-ENDPOINT%/collections?variables-h\[0\]\[measurement\]=M1"

Find collections matching multiple 'variables-h' param values, default is :and

     curl "%CMR-ENDPOINT%/collections?variables-h\[0\]\[measurement\]=M1&variables-h\[0\]\[variable\]=Var1&variables-h\[1\]\[measurement\]=M2"

### <a name="c-service-parameters"></a> Find collections by service parameters

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

### <a name="c-tool-parameters"></a> Find collections by tool parameters

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

### <a name="c-spatial"></a> Find collections by Spatial

#### <a name="c-polygon"></a> Polygon

Polygon points are provided in counter-clockwise order. The last point should match the first point to close the polygon. The values are listed comma separated in longitude latitude order, i.e. lon1, lat1, lon2, lat2, lon3, lat3, and so on.

The polygon parameter could be either "polygon", for single polygon search:

   curl "%CMR-ENDPOINT%/collections?polygon=10,10,30,10,30,20,10,20,10,10"

or "polygon[]", for single or multiple polygon search. It supports the and/or option as shown below. Default option is "and", i.e. it will match both the first polygon and the second polygon.

    curl "%CMR-ENDPOINT%/collections?polygon[]=10,10,30,10,30,20,10,20,10,10"

    curl "%CMR-ENDPOINT%/collections?polygon[]=10,10,30,10,30,20,10,20,10,10&polygon[]=11,11,31,11,31,21,11,21,11,11"

    curl "%CMR-ENDPOINT%/collections?polygon[]=10,10,30,10,30,20,10,20,10,10&polygon[]=11,11,31,11,31,21,11,21,11,11&options[polygon][or]=true"

Note: if you use "polygon" for multiple polygon search, it won't work because only the last polygon parameter will take effect.

#### <a name="c-bounding-box"></a> Bounding Box

Bounding boxes define an area on the earth aligned with longitude and latitude. The Bounding box parameters must be 4 comma-separated numbers: lower left longitude, lower left latitude, upper right longitude, upper right latitude.
This parameter supports the and/or option as shown below.

    curl "%CMR-ENDPOINT%/collections?bounding_box[]=-10,-5,10,5"

    curl "%CMR-ENDPOINT%/collections?bounding_box[]=-10,-5,10,5&bounding_box[]=-11,-6,11,6&options[bounding_box][or]=true"

#### <a name="c-point"></a> Point

Search using a point involves using a pair of values representing the point coordinates as parameters. The first value is the longitude and second value is the latitude. This parameter supports the and/or option as shown below.

    curl "%CMR-ENDPOINT%/collections?point=100,20"

    curl "%CMR-ENDPOINT%/collections?point=100,20&point=80,20&options[point][or]=true"

#### <a name="c-line"></a> Line

Lines are provided as a list of comma separated values representing coordinates of points along the line. The coordinates are listed in the format lon1, lat1, lon2, lat2, lon3, lat3, and so on. This parameter supports the and/or option as shown below.

    curl "%CMR-ENDPOINT%/collections?line[]=-0.37,-14.07,4.75,1.27,25.13,-15.51"

    curl "%CMR-ENDPOINT%/collections?line[]=-0.37,-14.07,4.75,1.27,25.13,-15.51&line[]=-1.37,-15.07,5.75,2.27,26.13,-16.51&options[line][or]=true"

#### <a name="c-circle"></a> Circle

Circle defines a circle area on the earth with a center point and a radius. The center parameters must be 3 comma-separated numbers: longitude of the center point, latitude of the center point, radius of the circle in meters. The circle center cannot be on North or South pole. The radius of the circle must be between 10 and 6,000,000.
This parameter supports the and/or option as shown below.

    curl "%CMR-ENDPOINT%/collections?circle[]=-87.629717,41.878112,1000"

    curl "%CMR-ENDPOINT%/collections?circle[]=-87.629717,41.878112,1000&circle[]=-75,41.878112,1000&options[circle][or]=true"

Note: A query could consist of multiple spatial parameters of different types, two bounding boxes and a polygon for example. If multiple spatial parameters are present, all the parameters irrespective of their type are ANDed in a query. So, if a query contains two bounding boxes and a polygon for example, it will return only those collections which intersect both the bounding boxes and the polygon.

### <a name="c-shapefile"></a> Find collections by shapefile

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

### <a name="c-shapefile-simplification"></a> Simplifying shapefiles during collection search
Shapefiles are limited to 5000 points by default. A user using a shapefile with more points than the CMR supported limit can use the `simplify-shapefile` parameter to request that the CMR try to simplify (reduce the number of points) the shapefile so that it is under the limit.

Example:

    curl -XPOST "%CMR-ENDPOINT%/collections" -F "simplify-shapefile=true"  -F "shapefile=@africa.zip;type=application/shapefile+zip" -F "provider=PROV1"

Note that the simplification process attempts to preserve topology, i.e., the relationship between polygon outer boundaries and holes. The process uses the [Douglas-Peucker algorithm](https://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm) and as such may result in geometries with less coverage than the original shapefile and potentially a loss of matching results.

The simplification may fail if the process cannot reduce the number of points in the file to below the limit. Also the simplification only reduces the number of points in the file, so a shapefile will still fail if the file size is too large or there are too many features.

### <a name="c-additional-attribute"></a> Find collections by additional attribute

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

### <a name="c-author"></a> Find collections by author

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching the given 'author' values

    curl "%CMR-ENDPOINT%/collections?author=*JPL*&options[author][pattern]=true"

### <a name="c-has-granules"></a> Find collections with or without granules

When `has_granules` is set to "true" or "false", results will be restricted to collections with or without granules, respectively.

    curl "%CMR-ENDPOINT%/collections?has_granules=true"

### <a name="c-has-granules-or-cwic"></a> Find collections with or without granules, or the collection with CWIC consortium.

The `has_granules_or_cwic` parameter can be set to "true" or "false". When true, the results will be restricted to collections with granules or with CWIC consortium. When false, will return any collections without granules.

    curl "%CMR-ENDPOINT%/collections?has_granules_or_cwic=true"

**Note:** this parameter will soon be retired in favor of a replacement parameter found below, `have_granules_or_opensearch`.

### <a name="c-has-granules-or-opensearch"></a> Find collections with or without granules, or the collection is tagged with the configured OpenSearch tag.

The `has_granules_or_opensearch` parameter can be set to "true" or "false". When true, the results will be restricted to collections with granules or with any of the configured OpenSearch consortiums, which are CWIC,FEDEO,GEOSS,CEOS and EOSDIS. When false, will return any collections without granules.

    curl "%CMR-ENDPOINT%/collections?has_granules_or_opensearch=true"

### <a name="c-has-opendap-url"></a> Find collections with or without an OPeNDAP service RelatedURL.

    curl "%CMR-ENDPOINT%/collections?has_opendap_url=true"

### <a name="c-cloud-hosted"></a> Find collections with data that is hosted in the cloud.

The `cloud_hosted` parameter can be set to "true" or "false". When true, the results will be restricted to collections that have a `DirectDistributionInformation` element or have been tagged with `gov.nasa.earthdatacloud.s3`.

    curl "%CMR-ENDPOINT%/collections?cloud_hosted=true"

### <a name="c-standard-product"></a> Find collections that are standard products.

The `standard_product` parameter can be set to "true" or "false". When true, the results will be restricted to collections that have `StandardProduct` element being true or collections that don't have `StandardProduct` element set and have been tagged with `gov.nasa.eosdis.standardproduct`.

    curl "%CMR-ENDPOINT%/collections?standard_product=true"

### <a name="sorting-collection-results"></a> Sorting Collection Results

Collection results are sorted by ascending entry title by default when a search does not result in a score.

If a keyword search is performed then the search results will be sorted by:

  * Relevance Score (descending), binned to the nearest 0.2. For example a score of 0.75 and 0.85 will be considered equal for sorting purposes.
  * Temporal Overlap (descending), if one or more temporal ranges are provided.
  * EMS Community Usage Score (descending), binned to the nearest 400. For example, usage of 400 and 500 will be considered equal for sorting purposes. The usage score comes from EMS metrics which contain access counts of the collections by short name and version. The metrics are ingested into the CMR.
  * Collection End Date (descending), with ongoing collections defaulting to today.
  * Humanized processing level Id (descending)

If a temporal range search is performed, the search results will be sorted by temporal overlap percentage over all ranges provided.

One or more sort keys can be specified using the `sort_key[]` parameter. The order used impacts searching. Fields can be prepended with a `-` to sort in descending order. Ascending order is the default but `+` (Note: `+` must be URL encoded as %2B) can be used to explicitly request ascending.

#### Valid Collection Sort Keys

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

### <a name="retrieving-all-revisions-of-a-collection"></a> Retrieving All Revisions of a Collection

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

## <a name="retrieving-concepts-by-concept-id-and-revision-id"></a> Retrieve concept with a given concept-id or concept-id & revision-id

This allows retrieving the metadata for a single concept. This is only supported for collections, granules, variables, services, tools and subscriptions. If no format is specified the native format of the metadata (and the native version, if it exists) will be returned.

By concept id

    curl -i  "%CMR-ENDPOINT%/concepts/:concept-id"

By concept id and revision id

    curl -i "%CMR-ENDPOINT%/concepts/:concept-id/:revision-id"

Plain examples, with and without revision ids:

    curl -i "%CMR-ENDPOINT%/concepts/G100000-PROV1"
    curl -i "%CMR-ENDPOINT%/concepts/C100000-PROV1/1"
    curl -i "%CMR-ENDPOINT%/concepts/V100000-PROV1"
    curl -i "%CMR-ENDPOINT%/concepts/V100000-PROV1/1"
    curl -i "%CMR-ENDPOINT%/concepts/V100000-PROV1/2"

File extension examples:

    curl -i "%CMR-ENDPOINT%/concepts/G100000-PROV1.iso"
    curl -i "%CMR-ENDPOINT%/concepts/G100000-PROV1.json"
    curl -i "%CMR-ENDPOINT%/concepts/G100000-PROV1/2.echo10"
    curl -i "%CMR-ENDPOINT%/concepts/V100000-PROV1.umm_json"
    curl -i "%CMR-ENDPOINT%/concepts/V100000-PROV1/2.umm_json"
    curl -i "%CMR-ENDPOINT%/concepts/V100000-PROV1/2.umm_json_v1_9"

MIME-type examples:

    curl -i -H 'Accept: application/xml' \
        "%CMR-ENDPOINT%/concepts/G100000-PROV1"
    curl -i -H 'Accept: application/metadata+xml' \
        "%CMR-ENDPOINT%/concepts/G100000-PROV1"
    curl -i -H "Accept: application/vnd.nasa.cmr.umm+json;version=1.9" \
        "%CMR-ENDPOINT%/concepts/V100000-PROV1"

Note that attempting to retrieve a revision that is a tombstone is an error and will return a 400 status code.

The following extensions and MIME types are supported by the `/concepts/` resource for collection and granule concept types:

  * `html`      "text/html" (Collections only)
  * `json`      "application/json"
  * `xml`       "application/xml" (same as .native)
  * `native`    "application/metadata+xml"
  * `echo10`    "application/echo10+xml"
  * `iso`       "application/iso19115+xml"
  * `iso19115`  "application/iso19115+xml"
  * `dif`       "application/dif+xml"
  * `dif10`     "application/dif10+xml"
  * `atom`      "application/atom+xml"
  * `umm_json`  "application/vnd.nasa.cmr.umm+json"
  * `stac`      "application/json; profile=stac-catalogue"

`atom` and `json` formats are only supported for retrieval of the latest collection/granule revisions (i.e. without specifying a particular revision).

`stac` format is only supported for retrieval of the latest granule revisions (i.e. without specifying a particular revision).

The following extensions and MIME types are supported by the `/concepts/` resource for the variable, service, tool  and subscription concept types:

  * `umm_json`  "application/vnd.nasa.cmr.umm+json"

## <a name="deleted-collections"></a> Find collections that have been deleted after a given date

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

## <a name="retrieve-provider-holdings"></a> Retrieve Provider Holdings

Provider holdings can be retrieved as XML or JSON. It will show all CMR providers, collections and granule counts regardless of the user's ACL access.

All provider holdings

    curl "%CMR-ENDPOINT%/provider_holdings.xml"

Provider holdings for a list of providers

    curl "%CMR-ENDPOINT%/provider_holdings.json?provider-id\[\]=PROV1&provider-id\[\]=PROV2"

## <a name="tagging"></a> Tagging

Tagging allows arbitrary sets of collections to be grouped under a single namespaced value. The sets of collections can be recalled later when searching by tag fields.

Tags have the following fields:

* tag_key (REQUIRED): free text specifying the key of the tag. Tag key cannot contain `/` character. Tag key is case-insensitive, it is always saved in lower case. When it is specified as mixed case, CMR will convert it into lower case. It normally consists of the name of the organization or the project who created the tag followed by a dot and the name of the tag. For example, org.ceos.wgiss.cwic.quality. The maximum length for tag key is 1030 characters.
* description (OPTIONAL): a free text description of what this tag is and / or how it is used. The maximum length for description is 4000 characters.
* originator_id (REQUIRED): the Earthdata Login ID of the person who created the tag.

### <a name="tag-access-control"></a> Tag Access Control

Access to tags is granted through the TAG_GROUP system object identity. Users can only create, update, or delete a tag if they are granted the appropriate permission. Associating and dissociating collections with a tag is considered an update.

### <a name="creating-a-tag"></a> Creating a Tag

Tags are created by POSTing a JSON representation of a tag to `%CMR-ENDPOINT%/tags` along with a valid EDL bearer token. The user id of the user associated with the token will be used as the originator id. The response will contain a concept id identifying the tag along with the tag revision id.

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags -d \
'{
  "tag_key": "org.ceos.wgiss.cwic.quality",
  "description": "This is a sample tag."
 }'

HTTP/1.1 201 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 48

{"concept_id":"T1200000000-CMR","revision_id":1}
```

### <a name="retrieving-a-tag"></a> Retrieving a Tag

A single tag can be retrieved by sending a GET request to `%CMR-ENDPOINT%/tags/<tag-key>` where `tag-key` is the tag key of the tag.

```
curl -i %CMR-ENDPOINT%/tags/org.ceos.wgiss.cwic.quality?pretty=true

HTTP/1.1 200 OK
Content-Length: 216
Content-Type: application/json;charset=ISO-8859-1

{
  "originator_id" : "mock-admin",
  "tag_key": "org.ceos.wgiss.cwic.quality",
  "description" : "This is a sample tag for indicating some data is high quality."
}
```

### <a name="updating-a-tag"></a> Updating a Tag

Tags are updated by sending a PUT request with the JSON representation of a tag to `%CMR-ENDPOINT%/tags/<tag-key>` where `tag-key` is the tag key of the tag. The same rules apply when updating a tag as when creating it but in addition tag key and originator id cannot be modified. The response will contain the concept id along with the tag revision id.

```
curl -XPUT -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/org.ceos.wgiss.cwic.quality -d \
'{
  "tag_key": "org.ceos.wgiss.cwic.quality",
  "description": "This is a sample tag for indicating some data is high quality."
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 48

{"concept_id":"T1200000000-CMR","revision_id":2}
```

### <a name="deleting-a-tag"></a> Deleting a Tag

Tags are deleted by sending a DELETE request to `%CMR-ENDPOINT%/tags/<tag-key>` where `tag-key` is the tag key of the tag. Deleting a tag creates a tombstone that marks the tag as deleted. The concept id of the tag and the revision id of the tombstone are returned from a delete request. Deleting a tag dissociates all collections with the tag.

```
curl -XDELETE -i  -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/org.ceos.wgiss.cwic.quality

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 48

{"concept_id":"T1200000000-CMR","revision_id":3}
```


### <a name="tag-association"></a> Tag Association

A tag can be associated with collections through either a JSON query or a list of collection concept revisions.
Tag association by query only supports tagging the latest revision of collections.
Tag association by collections supports tagging any specified collection revisions.

Expected Response Status:
<ul>
<li>200 OK -- if all associations succeeded</li>
<li>207 MULTI-STATUS -- if some associations succeeded and some failed due to user error</li>
<li>400 BAD REQUEST -- if all associations failed due to user error</li>
</ul>

Expected Response Body:

The response body will consist of a list of tool association objects
Each association object will have:
<ul>
    <li>A `tagged_item` field</li>
    <ul>
        <li>The `tagged_item` field value has the collection concept id and the optional revision id that is used to identify the collection during tag association.</li>
    </ul>
    <li>Either a `tag_association` field with the tag association concept id and revision id when the tag association succeeded or an `errors` field with detailed error message when the tag association failed. </li>
</ul>

- IMPORTANT: The tag and the collections must exist before they can be associated together.


Here is am example of a tag association request and its response:

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/org.ceos.wgiss.cwic.native_id/associations -d \
'[{"concept_id": "C1200000005-PROV1", "data": "Global Maps of Atmospheric Nitrogen Deposition, 2016"},
  {"concept_id": "C1200000006-PROV1", "data": "Global Maps of Atmospheric Nitrogen Deposition"}]'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "tag_association":{
      "concept_id":"TA1200000009-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000005-PROV1"
    }
  },
  {
    "tag_association":{
      "concept_id":"TA1200000009-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000006-PROV1"
    }
  }
]
```

On occasions when tag association cannot be processed at all due to invalid input, tag association request will return failure status code 400 with the appropriate error message.

### <a name="associating-collections-with-a-tag-by-query"></a> Associating Collections with a Tag by query

Tags can be associated with collections by POSTing a JSON query for collections to `%CMR-ENDPOINT%/tags/<tag-key>/associations/by_query` where `tag-key` is the tag key of the tag.
All collections found will be _added_ to the current set of associated collections with a tag.
Tag associations are maintained throughout the life of a collection.
If a collection is deleted and re-added it will maintain its tags.

Expected Response Status:
- If query delivers no match, it will still return 200 OK with no associations made

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/edsc.in_modaps/associations/by_query -d \
'{
  "condition": {"provider": "PROV1"}
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "tag_association":{
      "concept_id":"TA1200000009-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000000-PROV1"
    }
  },
  {
    "tag_association":{
      "concept_id":"TA1200000008-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000001-PROV1"
    }
  }
]
```

### <a name="associating-collections-with-a-tag-by-concept-ids"></a> Associating Collections with a Tag by collection concept ids and optional revision ids

Tags can be associated with collections by POSTing a JSON array of collection concept-ids and optional revision ids to `%CMR-ENDPOINT%/tags/<tag-key>/associations` where `tag-key` is the tag key of the tag.
User can also provide arbitrary JSON data which is optional during tag association.
The max length of JSON data used for tag association is 32KB.
All referenced collections will be _added_ to the current set of associated collections with a tag.
Tag associations are maintained throughout the life of a collection.
If a collection is deleted and re-added it will maintain its tags.
If a tag is already associated with a collection without revision, it cannot be associated with a specific revision of that collection again, and vice versa.
Tags cannot be associated on tombstoned collection revisions.

Expected Response Status:
<ul>
    <li>200 OK -- if all associations succeeded</li>
    <li>207 MULTI-STATUS -- if some associations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all associations failed due to user error</li>
</ul>

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/gov.nasa.gcmd.review_status/associations -d \
'[{"concept_id": "C1200000005-PROV1", "revision_id": 2, "data": "APPROVED"},
  {"concept_id": "C1200000006-PROV1", "revision_id": 1, "data": "IN_REVIEW"},
  {"concept_id": "C1200000007-PROV1", "revision_id": 1, "data": "REVIEW_DISPUTED"}]'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "tag_association":{
      "concept_id":"TA1200000008-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000005-PROV1",
      "revision_id":2
    }
  },
  {
    "tag_association":{
      "concept_id":"TA1200000009-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000006-PROV1",
      "revision_id":1
    }
  }
]
```

### <a name="tag-dissociation"></a> Tag Dissociation

A tag can be dissociated from collections through either a JSON query or a list of collection concept revisions similar to tag association requests.
Tag dissociation by query only supports tag dissociation of the latest revision of collections.
Tag dissociation by collections supports tag dissociation from any specified collection revisions.
The tag dissociation response looks the same as tag association response.

Expected Response Status:
<ul>
    <li>200 OK -- if all dissociations succeeded</li>
    <li>207 MULTI-STATUS -- if some dissociations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all dissociations failed due to user error</li>
</ul>

Expected Response Body:

The response body will consist of a list of tool association objects
Each association object will have:
<ul>
    <li>A `tagged_item` field</li>
    <ul>
        <li>The `tagged_item` field is the collection concept id and the optional revision id that is used to identify the collection during tag dissociation.</li>
    </ul>
    <li>Either a `tag_association` field with the tag association concept id and revision id when the tag dissociation succeeded or an `errors` or `warnings` field with detailed message when the tag dissociation failed or inapplicable. </li>
</ul>

Here is a sample tag dissociation request and its response:

```
curl -XDELETE -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/edsc.in_modaps/associations -d \
'[{"concept_id": "C1200000005-PROV1"},
  {"concept_id": "C1200000006-PROV1"},
  {"concept_id": "C1200000007-PROV1"}]'

HTTP/1.1 207 MULTI-STATUS
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "status": 200,
    "tag_association":{
      "concept_id":"TA1200000008-CMR",
      "revision_id":2
    },
    "tagged_item":{
      "concept_id":"C1200000005-PROV1"
    }
  },
  {
    "status": 200,
    "warnings":[
      "Tag [edsc.in_modaps] is not associated with collection [C1200000006-PROV1]."
    ],
    "tagged_item":{
      "concept_id":"C1200000006-PROV1"
    }
  },
  {
    "status": 400,
    "errors":[
      "Collection [C1200000007-PROV1] does not exist or is not visible."
    ],
    "tagged_item":{
      "concept_id":"C1200000007-PROV1"
    }
  }
]
```

### <a name="dissociating-collections-with-a-tag-by-query"></a> Dissociating a Tag from Collections by query

Tags can be dissociated from collections by sending a DELETE request with a JSON query for collections to `%CMR-ENDPOINT%/tags/<tag-key>/associations/by_query` where `tag-key` is the tag key of the tag. All collections found in the query will be _removed_ from the current set of associated collections.

Expected Response Status:
- If query delivers no match, it will still return 200 OK with no associations made

```
curl -XDELETE -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/edsc.in_modaps/associations/by_query -d \
'{
  "condition": {"provider": "PROV1"}
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "tag_association":{
      "concept_id":"TA1200000007-CMR",
      "revision_id":2
    },
    "tagged_item":{
      "concept_id":"C1200000000-PROV1"
    }
  },
  {
    "tag_association":{
      "concept_id":"TA1200000008-CMR",
      "revision_id":2
    },
    "tagged_item":{
      "concept_id":"C1200000001-PROV1"
    }
  }
]
```

### <a name="dissociating-collections-with-a-tag-by-concept-ids"></a> Dissociating a Tag from Collections by collection concept ids

Tags can be dissociated from collections by sending a DELETE request with a JSON array of collection concept-ids to
`%CMR-ENDPOINT%/tags/<tag-key>/associations` where `tag-key` is the tag key of the tag.

Expected Response Status:
<ul>
    <li>200 OK -- if all associations succeeded</li>
    <li>207 MULTI-STATUS -- if some associations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all associations failed due to user error</li>
</ul>

```
curl -XDELETE -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/gov.nasa.gcmd.review_status/associations -d \
'[{"concept_id": "C1200000005-PROV1", "revision_id": 1},
  {"concept_id": "C1200000006-PROV1", "revision_id": 2}]'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "warnings":[
      "Tag [gov.nasa.gcmd.review_status] is not associated with the specific collection concept revision concept id [C1200000005-PROV1] and revision id [1]."
    ],
    "tagged_item":{
      "concept_id":"C1200000005-PROV1",
      "revision_id":1
    }
  },
  {
    "tag_association":{
      "concept_id":"TA1200000008-CMR",
      "revision_id":2
    },
    "tagged_item":{
      "concept_id":"C1200000006-PROV1",
      "revision_id":2
    }
  }
]
```

### <a name="searching-for-tags"></a> Searching for Tags

Tags can be searched for by sending a request to `%CMR-ENDPOINT%/tags`.

Tag search results are paged. See [Paging Details](#paging-details) for more information on how to page through tag search results.

#### Tag Search Parameters

The following parameters are supported when searching for tags.

#### Standard Parameters:

* page_size
* page_num
* pretty

#### Tag Matching Parameters

These parameters will match fields within a tag. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `tag_key[]=key1&tag_key[]=key2`.

* tag_key
  * options: pattern
* originator_id
  * options: pattern

#### Tag Search Response

The response is always returned in JSON and includes the following parts.

* hits - How many total tags were found.
* took - How long the search took in milliseconds
* items - a list of the current page of tags with the following fields
  * concept_id
  * revision_id
  * tag_key
  * description
  * originator_id - The id of the user that created the tag.

#### Tag Search Example

```
curl -g -i "%CMR-ENDPOINT%/tags?pretty=true&tag_key=org\\.ceos\\.*&options[tag_key][pattern]=true"

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 292

{
  "items" : [ {
    "concept_id" : "T1200000000-CMR",
    "revision_id" : 1,
    "tag_key" : "org.ceos.wgiss.cwic",
    "description" : "This is a sample tag.",
    "originator_id" : "mock-admin"
  } ],
  "took" : 5,
  "hits" : 1
}
```

## <a name="document-scoring"></a> Document Scoring

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

## <a name="facets"></a> Facets

Facets are counts of unique values from fields in items matching search results. Facets are supported with collection or granule search results and are enabled with the `include_facets` parameter. There are three different types of facet responses. There are flat facets (collection only), hierarchical facets (collection only), and version 2 facets.

Flat and hierarchical facets are supported on all collection search response formats except for the opendata response format. When `echo_compatible=true` parameter is also present, the facets are returned in the catalog-rest search_facet style in XML or JSON format.

Several fields including science keywords, data centers, platforms, instruments, and location keywords can be represented as either flat or hierarchical fields. By default facets are returned in a flat format showing counts for each nested field separately. In order to retrieve hierarchical facets pass in the parameter `hierarchical_facets=true`.

Note that any counts included in hierarchical facets should be considered approximate within a small margin of an error rather than an exact count. If an exact count is required following the link provided to apply that facet will perform a search that returns an exact hits count.

### <a name="facets-v2-response-format"></a> Version 2 Facets Response Format

Version 2 facets are enabled by setting the `include_facets=v2` parameter in either collection or granule search requests in the JSON format. In order to request faceting on granule searches, the search must be limited in scope to a single collection (e.g. by specifying a single concept ID in the collection_concept_id parameter). The max number of values in each v2 facet can be set by using facets_size parameter (i.e. facets_size[platforms]=10, facets_size[instrument]=20. Default size is 50.). facets_size is only supported for collection v2 facet search. The same fields apply in the v2 facets as for the flat facets with the addition of horizontal range facets and latency facets. When calling the CMR with a query the V2 facets are returned. These facets include the apply field described in more detail a few paragraphs below that includes the search parameter and values that need to be sent back to the CMR.

#### Specifying facet fields

Hierarchical Facet requests include any or all parts of the hierarchical structure using the `&parameter[set][subfield]=value` notation where:

* **set**: Field group number denoting related hierarchical subfields where all subfields for one facet use the same number. Values start with 0.
* **subfield**: Field name in the hierarchical facet as defined by KMS. ie: Platforms uses Basis, Category, Sub_Category, Short_Name
* **value**: facet value. ie Platform Basis has a `Air-based Platforms` value.

Example: `science_keywords_h[0][topic]=Oceans`

Example curl calls:

    %CMR-ENDPOINT%/collections.json?include_facets=v2&hierarchical_facets=true&science_keywords_h%5B0%5D%5Btopic%5D=Oceans

#### Responses

With version 2 facets the CMR makes no guarantee of which facets will be present, whether the facets returned are hierarchical or flat in nature, how many values will be returned for each field, or that the same facets will be returned from release to release. The rules for processing v2 facets are as follows.

The response will contain a field, "facets" containing the root node of the facets tree structure. Every node in the tree structure contains the following minimal structure:

```
var treeNode = {
  "title": "Example",         // A human-readable representation of the node
  "type": "group|filter|..."  // The type of node represented
}
```

Currently, the filter response type defines two node types: "group" and "filter". More may be added in the future, and clients must be built to ignore unknown node types.

#### Group Nodes

Group nodes act as containers for similar data. Depending on context, this data may, for instance, be all facet parameters (the root facet node), top-level facets, or children of existing facets. Group nodes further have a

```
var groupNode = { // Inherits treeNode fields
  "applied": true,            // true if the filter represented by this node (see Filter Nodes) or any of its descendants has been applied to the current query
  "has_children": true,       // true if the tree node has child nodes, false otherwise
  "children": [               // List of child nodes, provided at the discretion of the CMR (see below)
  ]
}
```

#### Children

In order to avoid sending unnecessary information, the CMR may in the future opt to not return children for group nodes that have facets, returning only the first few levels of the facet tree. It will, however, allow clients to appropriately display incomplete information and query the full tree as necessary.

#### Relevance

By default, clients should assume that the CMR may limit facet results to only include the most relevant child nodes in facet responses. For instance, if there are hundreds of science keywords at a particular depth, the CMR may choose to only return those that have a substantial number of results. When filtering children, the CMR makes no guarantees about the specific quantities or values of facets returned, only that applied filters attempt to surface the choices that typical users are most likely to find beneficial.

#### Filter Nodes

Filter nodes are group nodes that supply a single query condition indicated by a string (the node title). They further have a field, "applied," which indicates if the query value has already been applied to the current query.

```
var filterNode = { // Inherits groupNode fields
  "count": 1234,                                                          // Count of results matching the filter
  "links": {
    "apply": "%CMR-ENDPOINT%/collections?instrument[]=MODIS", // A URL containing the filter node applied to the current query. Returned only if the node is not applied.
    "remove": "%CMR-ENDPOINT%/collections"                    // A URL containing the filter node removed from the current query. Returned only if the node is applied.
  },
}
```
Note that while two potential queries are listed in "links", in practice only one would be returned based on whether the node is currently applied.

#### Collection Example
The following example is a sample response for a query using the query parameters API which has the "instruments=ASTER" filter applied as well as a page size of 10 applied by the client. The example is hand-constructed for example purposes only. Real-world queries would typically be more complex, counts would be different, and the facet tree would be much larger.

```
{
  "facets": {
    "title": "Browse Collections",
    "type": "group",
    "applied": true, // NOTE: true because the tree does have a descendant node that has been applied
    "has_children": true,
    "children": [
      {
        "title": "Instruments",
        "type": "group",
        "applied": true,
        "has_children": true,
        "children": [
          {
            "title": "MODIS",
            "type": "filter",
            "applied": false,
            "count": 200,
            "links": { "apply": "https://example.com/search/collections?page_size=10&instruments[]=ASTER&instruments[]=MODIS" },
            "has_children": false
          },
          {
            "title": "ASTER",
            "type": "filter",
            "applied": true,
            "count": 2000,
            "links": { "remove": "https://example.com/search/collections?page_size=10" }, // NOTE: Differing response for an applied filter
            "has_children": false
          }
        ]
      },
      {
        "title": "Keywords",
        "type": "group",
        "applied": false,
        "has_children": true,
        "children": [
          {
            "title": "EARTH SCIENCE",
            "type": "filter",
            "applied": false,
            "count": 1500,
            "links": { "apply": "https://example.com/search/collections?page_size=10&instruments[]=ASTER&science_keywords[0][category_keyword]=EARTH%20SCIENCE" },
            "has_children": true,
            "children": [ // NOTE: This is an example of how the CMR may handle children at different levels of a hierarchy.
              {           //       For usability reasons, this should only be done if absolutely necessary, preferring to just collapse the hierarchy when possible
                "title": "Topics",
                "type": "group",
                "applied": false,
                "has_children": true,
                "children": [
                  {
                    "title": "OCEANS",
                    "type": "filter",
                    "applied": false,
                    "count": 500,
                    "links": { "apply": "https://example.com/search/collections?page_size=10&instruments[]=ASTER&science_keywords[0][category_keyword]=EARTH%20SCIENCE&science_keywords[0][topic]=OCEANS" },
                    "has_children": true // NOTE: The node has children, but the CMR has opted not to return them
                  }
                ]
              },
              {
                "title": "Variables",
                "type": "group",
                "applied": false,
                "has_children": true,
                "children": [
                  {
                    "title": "WOLVES",
                    "type": "filter",
                    "applied": false,
                    "count": 2,
                    "links": { "apply": "https://example.com/search/collections?page_size=10&instruments[]=ASTER&science_keywords[0][detailed_variable]=WOLVES" },
                    "has_children": false
                  }
                ]
              }
            ]
          }
        ]
      }
    ]
  }
}
```

#### Granule Example
```
"facets" : {
      "title" : "Browse Granules",
      "has_children" : true,
      "children" : [ {
        "title" : "Temporal",
        "has_children" : true,
        "children" : [ {
          "title" : "Year",
          "has_children" : true,
          "children" : [ {
            "title" : "2004",
            "count" : 50
          }, {
            "title" : "2003",
            "count" : 2
          } ]
        } ]
      } ]
    }
```

### <a name="facets-in-xml-responses"></a> Collection Facets in XML Responses

Facets in XML search response formats will be formatted like the following examples. The exception is ATOM XML which is the same except the tags are in the echo namespace.

#### <a name="flat-xml-facets"></a> Flat XML Facets

```
<facets>
  <facet field="data_center">
    <value count="28989">LARC</value>
    <value count="19965">GSFC</value>
  </facet>
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

#### <a name="hierarchical-xml-facets"></a> Hierarchical XML Facets

Fields that are not hierarchical are returned in the same format as the flat response, but hierarchical fields are returned in a nested structure. Fields which are returned hierarchically include platforms, instruments, data centers, archive centers, and science keywords.

```
<facets>
  <facet field="project"/>
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

### <a name="facets-in-json-responses"></a> Collection Facets in JSON Responses

Facets in JSON search response formats will be formatted like the following examples.

#### <a name="flat-json-facets"></a> Flat JSON facets

```
{
  "feed": {
    "entry": [...],
    "facets": [{
      "field": "data_center",
      "value-counts": [
        ["LARC", 28989],
        ["GSFC", 19965]
      ]
    }, {
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

#### <a name="hierarchical-json-facets"></a> Hierarchical JSON facets

Fields that are not hierarchical are returned in the same format as the flat response, but hierarchical fields are returned in a nested structure.

```
    "facets" : [ {
      "field" : "project",
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

## <a name="autocomplete-facets"></a> Facet Autocompletion

Auto-completion assistance for building queries. This functionality may be used to help build queries. The facet autocomplete functionality does not search for collections directly. Instead it will return suggestions of facets to help narrow a search by providing a list of available facets to construct a CMR collections search.

    curl "%CMR-ENDPOINT%/autocomplete?q=<term>[&type\[\]=<type1>[&type\[\]=<type2>]"

Collection facet autocompletion results are paged. See [Paging Details](#paging-details) for more information on how to page through autocomplete search results.

### Autocompletion of Science Keywords
In the case of science keywords, the `fields` property may be used to determine the hierarchy of the term. The structure of the `fields` field is a colon (`:`) separated list in the following sequence:

 * topic
 * term
 * variable-level-1
 * variable-level-2
 * variable-level-3
 * detailed-variable

There may be gaps within the structure where no associated value exists.

Example With variable-level-1 as the base term
```
{ :score 2.329206,
  :type "science_keywords",
  :value "Solar Irradiance",
  :fields "Sun-Earth Interactions:Solar Activity:Solar Irradiance"
}
```

Example with detailed-variable as the base term, note the extra colons preserving the structure
```
{ :score 1.234588,
  :type "science_keywords",
  :value "Coronal Mass Ejection",
  :fields "Sun-Earth Interactions:Solar Activity::::Coronal Mass Ejection
}
```

### Autocompletion of Platforms
In the case of platforms, the `fields` property may be used to determine the hierarchy of the term. The structure of the `fields` field is a colon (`:`) separated list in the following sequence:

 * basis
 * category
 * sub-category
 * short-name

There may be gaps within the structure where no associated value exists.

Example With short-name as the base term
```
{ :score 2.329206,
  :type "platforms",
  :value "AEROS-1",
  :fields "Space-based Platforms:Earth Observation Satellites:Aeros:AEROS-1"
}
```

Example with short-name as the base term, but the sub-category is missing. Note the extra colons preserving the structure
```
{ :score 1.234588,
  :type "platforms",
  :value "Terra",
  :fields "Space-based Platforms:Earth Observation Satellites::Terra
}
```

### Autocomplete Parameters
  * `q` The string on which to search. The term is case insensitive.
  * `type[]` Optional list of types to include in the search. This may be any number of valid facet types.

__Example Query__

     curl "%CMR-ENDPOINT%/autocomplete?q=ice"

__Example Result__
```
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
CMR-Hits: 15

{
  "feed": {
    "entry": [
      {
        "score": 9.115073,
        "type": "instrument",
        "fields": "ICE AUGERS",
        "value": "ICE AUGERS"
      },
      {
        "score": 9.115073,
        "type": "instrument",
        "fields": "ICECUBE",
        "value": "ICECUBE"
      },
      {
        "score": 9.021176,
        "type": "platforms",
        "fields": "Space-based Platforms:Earth Observation Satellites:Ice, Cloud and Land Elevation Satellite (ICESat):ICESat-2",
        "value": "ICESat-2"
      },
      {
        "score": 8.921176,
        "type": "science_keywords",
        "fields": "Atmosphere:Sun-Earth Interactions:Cloud Cover:Ice Reflectivity",
        "value": "Ice Reflectivity"
      }
    ]
  }
}
```

__Example Query__

     curl "%CMR-ENDPOINT%/autocomplete?q=ice&type[]=platform&type[]=project"

__Example Result with Type Filter__

```
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
CMR-Hits: 3

{
  "feed": {
    "entry": [
      {
        "score": 9.013778,
        "type": "platforms",
        "value": "ICESat-2",
        "fields": "Space-based Platforms:Earth Observation Satellites:Ice, Cloud and Land Elevation Satellite (ICESat):ICESat-2"
      },
      {
        "score": 8.921176,
        "type": "platforms",
        "value": "Sea Ice Mass Balance Station",
        "fields": "Water-based Platforms:Fixed Platforms:Surface:Sea Ice Mass Balance Station"
      },
      {
        "score": 8.921176,
        "type": "project",
        "value": "ICEYE"
      }
    ]
  }
}
```

## <a name="humanizers"></a> Humanizers

Humanizers define the rules that are used by CMR to provide humanized values for various facet fields and also support other features like improved relevancy of faceted terms. The rules are defined in JSON. Operators with Admin privilege can update the humanizer instructions through the update humanizer API.

### <a name="updating-humanizers"></a> Updating Humanizers

Humanizers can be updated with a JSON representation of the humanizer rules to `%CMR-ENDPOINT%/humanizers` along with a valid EDL bearer token or Launchpad token. The response will contain a concept id and revision id identifying the set of humanizer instructions.

```
curl -XPUT -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/humanizers -d \
'[{"type": "trim_whitespace", "field": "platform", "order": -100},
  {"type": "alias", "field": "platform", "source_value": "AM-1", "replacement_value": "Terra", "reportable": true, "order": 0}]'

HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 48

{"concept_id":"H1200000000-CMR","revision_id":2}
```

### <a name="retrieving-humanizers"></a> Retrieving Humanizers

The humanizers can be retrieved by sending a GET request to `%CMR-ENDPOINT%/humanizers`.

```
curl -i %CMR-ENDPOINT%/humanizers?pretty=true

HTTP/1.1 200 OK
Content-Length: 224
Content-Type: application/json; charset=UTF-8

[ {
  "type" : "trim_whitespace",
  "field" : "platform",
  "order" : -100
}, {
  "type" : "alias",
  "field" : "platform",
  "source_value" : "AM-1",
  "replacement_value" : "Terra",
  "reportable" : true,
  "order" : 0
} ]
```

### <a name="facets-humanizers-report"></a> Humanizers Report

The humanizers report provides a list of fields that have been humanized in CSV format. The report format is: provider, concept id, product short name, product version, original field value, humanized field value.

    curl "%CMR-ENDPOINT%/humanizers/report"

Note that this report is currently generated every 24 hours with the expectation that this more than satisfies weekly usage needs.

An administrator with system object INGEST\_MANAGEMENT\_ACL update permission can force the report to be regenerated by passing in a query parameter `regenerate=true`.