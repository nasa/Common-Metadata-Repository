## API Documentation

### General Request Details

#### Maximum URL Length

The Maximum URL Length supported by CMR is indirectly controlled by the Request Header Size setting in Jetty which is at 1MB. This translates to roughly 500k characters. Clients using the Search API with query parameters should be careful not to exceed this limit or they will get an HTTP response of 413 FULL HEAD. If a client expects they will sometimes need to send extra long query url that might exceed 500k characters, they should use the POST API for searching.

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

where paramter is the URL parameter whose behavior is to be affected, value is either `true` or `false`, and `option_key` is one of the following:

 * `ignore_case` - if set to true, the search should be case insensitive. Defaults to true.
 * `pattern` - if set to true, the search should treat the value provided for the parameter as a pattern with wildcards, in which '*' matches zero or
 more characters and '?' matches any single character. For example, `platform[]=AB?D*&options[platform][pattern]=true` would match 'ABAD123', 'ABCD12', 'ABeD', etc. Defaults to false.
 * `and` - if set to true and if multiple values are listed for the param, the concpet must have ALL of these values in order to match. The default
 is `false` which means concpets with ANY of the values match. This option only applies to fields which may be multivalued; these are documented here.
 * `or` - this option only applies to granule attribute or science-keywords searches. If set to true, attribute searches will find granules that match
 any of the attibutes. The default is false.

##### Collection Query Parameters

These are query parameters specific to collections

  * `include_has_granules` - If this parameter is set to "true" this will include a flag indicating true or false if the collection has any granules at all. Supported in all response formats except opendata.
  * `include_granule_counts` - If this parameter is set to "true" this will include a count of the granules in each collection that would match the spatial and temporal conditions from the collection query. Supported in all response formats except opendata.
  * `include_facets` - If this parameter is set to "true" facets will be included in the collection results (not applicable to opendata results). Facets are described in detail below.

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
  * `iso`       "application/iso+xml"
  * `iso_mends` "application/iso-mends+xml"
  * `dif`       "application/dif+xml"
  * `csv`       "text/csv"
  * `atom`      "application/atom+xml"
  * `opendata`  "application/opendata+json" (only supported for collections)
  * `kml`       "application/vnd.google-earth.kml+xml"

iso is an alias for iso\_mends.

### Search for Collections

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

#### Find collections by campaign

This supports `pattern`, `ignore_case` and option `and`. 'campaign' maps to 'project' in UMM

Find collections matching 'campaign' param value

     curl "%CMR-ENDPOINT%/collections?campaign\[\]=ESI"

Find collections matching any of the 'campaign' param values

     curl "%CMR-ENDPOINT%/collections?campaign\[\]=ESI&campaign\[\]=EVI&campaign\[\]=EPI"

Find collections that match all of the 'campaign' param values

     curl "%CMR-ENDPOINT%/collections?campaign\[\]=ESI&campaign\[\]=EVI&campaign\[\]=EPI&options\[campaign\]\[and\]=true"

#### Find collections by updated_since

  Find collections which have revision date starting at or after 'updated_since' param value

     curl "%CMR-ENDPOINT%/collections?updated_since=2014-05-08T20:06:38.331Z"

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

Supports ignore_case and the following aliases for "NEAR\_REAL\_TIME": "near\_real\_time","nrt", "NRT", "near real time","near-real time","near-real-time","near real-time".

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

#### Find collections by provider

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'provider' param value

    curl "%CMR-ENDPOINT%/collections?provider=ASF"

Find collections matching any of the'provider' param values

    curl "%CMR-ENDPOINT%/collections?provider=ASF&provider=SEDAC"

#### Find collections by short name

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching any of the 'short_name' param values

    curl "%CMR-ENDPOINT%/collections?short_name=DEM_100M&short_name=MINIMAL"

Find collections matching 'short_name' param value with a pattern

    curl "%CMR-ENDPOINT%/collections?short_name=D*&options[short_name][pattern]=true"

#### Find collections by version

This parameter supports `pattern`, `ignore_case` and option `and`.

Find collections matching 'version' param value

    curl "%CMR-ENDPOINT%/collections?version=1"

Find collections matching any of the 'version' param values

    curl "%CMR-ENDPOINT%/collections?version=1&version=2"

#### Find collections by Spatial

##### Polygon

Polygon points are provided in counter-clockwise order. The last point should match the first point to close the polygon. The values are listed comma separated in longitude latitude order, i.e. lon1,lat1,lon2,lat2,...

    curl "%CMR-ENDPOINT%/collections?polygon=10,10,30,10,30,20,10,20,10,10"

##### Bounding Box

Bounding boxes define an area on the earth aligned with longitude and latitude. The Bounding box parameters must be 4 comma-separated numbers: lower left longitude,lower left latitude,upper right longitude,upper right latitude.

    curl "%CMR-ENDPOINT%/collections?bounding_box=-10,-5,10,5

##### Point

Search using a point involves using a pair of values representing the point coordinates as parameters. The first value is the longitude and second value is the latitude (lon, lat)

   curl "%CMR-ENDPOINT%/collections?point=100,20"

##### Line

Lines are provided as list of comma separated values representing coordinates of points along the line. The coordinates are listed in the format lon1,lat1,lon2,lat2,...

   curl "%CMR-ENDPOINT%/collections?line=-0.37,-14.07,4.75,1.27,25.13,-15.51"

A query could consist of multiple spatial types at once, two bounding boxes and a polygon for example. All the parameters of a given spatial type are OR'd in a query. If the query contains two bounding boxes for example, it will return collections which intersect either of the bounding boxes. But parameters across different spatial types are AND'd. So if the query contains a polygon and a bounding-box it will return all the collections which intersect both the polygon and the bounding-box. This behavior may be changed in the future to use OR across all the spatial parameters irrespective of their type.

#### Sorting Collection Results

Collection results are sorted by ascending entry title by default. One or more sort keys can be specified using the `sort_key[]` parameter. The order used impacts searching. Fields can be prepended with a `-` to sort in descending order. Ascending order is the default but `+` can be used to explicitly request ascending.

##### Valid Collection Sort Keys

  * `entry_title`
  * `dataset_id` - alias for entry_title
  * `start_date`
  * `end_date`
  * `platform`
  * `instrument`
  * `sensor`
  * `provider`
  * `score` - document relevance score, only valid with keyword search, defaults to descending

Example of sorting by start_date in descending order: (Most recent data first)

    curl "%CMR-ENDPOINT%/collections?sort_key\[\]=-start_date

### Search for Granules

#### Find all granules

    curl "%CMR-ENDPOINT%/granules"

#### Find granules with a granule-ur

    curl "%CMR-ENDPOINT%/granules?granule_ur\[\]=DummyGranuleUR"

#### Find granules with a producer granule id

    curl "%CMR-ENDPOINT%/granules?producer_granule_id\[\]=DummyID"

#### Find granules matching either granule ur or producer granule id

    curl "%CMR-ENDPOINT%/granules?readable_granule_name\[\]=DummyID"

#### Find granules by online_only

    curl "%CMR-ENDPOINT%/granules?online_only=true"

#### Find granules by downloadable

    curl "%CMR-ENDPOINT%/granules?downloadable=true"

#### Find granules by additional attribute

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

Multiple attributes can be provided. The default is for granules to match all the attribute parameters. This can be changed by specifying `or` option with `option[attribute][or]=true`.

For granule additional attributes search, the default is searching for the attributes included in the collection this granule belongs to as well. This can be changed by specifying `exclude_collection` option with `option[attribute][exclude_collection]=true`.

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
  (across the antimeridian)

    curl "%CMR-ENDPOINT%/granules?equator_crossing_longitude=170,-170

#### Find granules by orbit equator crossing date

  Find granules with an orbit equator crossing date in the range of
  2000-01-01T10:00:00Z to 2010-03-10T12:00:00Z

    curl "%CMR-ENDPOINT%/granules?equator_crossing_date=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z

#### Find granules by updated_since

  Find granules which have revision date starting at or after 'updated_since' param value

     curl "%CMR-ENDPOINT%/granules?updated_since=2014-05-08T20:12:35Z"

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

#### Find granules by campaign

This supports `pattern`, `ignore_case` and option `and`. 'campaign' maps to 'project' in UMM

Find granules matching 'campaign' param value

     curl "%CMR-ENDPOINT%/granules?campaign\[\]=2009_GR_NASA"

Find granules matching any of the 'campaign' param values

     curl "%CMR-ENDPOINT%/granules?campaign\[\]=2009_GR_NASA&campaign\[\]=2013_GR_NASA"

Find granules matching the given pattern for the 'campaign' param value
     curl "%CMR-ENDPOINT%/granules?campaign\[\]=20??_GR_NASA&options\[campaign\]\[pattern\]=true" 

Find granules that match all of the 'campaign' param values

     curl "%CMR-ENDPOINT%/granules?campaign\[\]=2009_GR_NASA&campaign\[\]=2013_GR_NASA&options\[campaign\]\[and\]=true"

#### Find granules by echo granule id, echo collection id and concept ids.

Note: more than one may be supplied

  Find granule by concept id

    curl "%CMR-ENDPOINT%/granules?concept_id\[\]=G1000000002-CMR_PROV1"

  Find granule by echo granule id

    curl "%CMR-ENDPOINT%/granules?echo_granule_id\[\]=G1000000002-CMR_PROV1"

  Find granules by echo collection id

    curl "%CMR-ENDPOINT%/granules?echo_collection_id\[\]=C1000000001-CMR_PROV2"

  Find granules by parent concept id

    curl "%CMR-ENDPOINT%/granules?concept_id\[\]=C1000000001-CMR_PROV2"

#### Find granules by day\_night\_flag param, supports pattern and ignore_case

```
curl "%CMR-ENDPOINT%/granules?day_night_flag=night

curl "%CMR-ENDPOINT%/granules?day_night_flag=day

curl "%CMR-ENDPOINT%/granules?day_night=unspecified
```

#### Find granules by grid param.

This is an alias of catalog-rest two\_d\_coordinate_system.

':' is the separator between name and coordinates; range is indicated by '-', otherwise it is a single value.

```
  curl "%CMR-ENDPOINT%/granules?grid\[\]=wrs-1:5,10:8-10,0-10
```

#### Find granules by provider

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules matching 'provider' param value

    curl "%CMR-ENDPOINT%/granules?provider=ASF"

Find granules matching any of the 'provider' param values

    curl "%CMR-ENDPOINT%/granules?provider=ASF&provider=SEDAC"

#### Find granules by short name

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules with the corresponding collection matching any of the 'short_name' param values

    curl "%CMR-ENDPOINT%/granules?short_name=DEM_100M&short_name=MINIMAL"

Find granules with the corresponding collection matching 'short_name' param value with a pattern

    curl "%CMR-ENDPOINT%/granules?short_name=D*&options[short_name][pattern]=true"

#### Find granules by version

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules with the corresponding collection matching 'version' param value

    curl "%CMR-ENDPOINT%/granules?version=1"

Find granules with the corresponding collection matching any of the 'version' param values

    curl "%CMR-ENDPOINT%/granules?version=1&version=2"

#### Find granules by entry title

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules with the corresponding collection matching 'entry_title' param value

    curl "%CMR-ENDPOINT%/granules?entry_title=DatasetId%204"

#### Find granules with temporal

The temporal datetime has to be in yyyy-MM-ddTHH:mm:ssZ format.

    curl "%CMR-ENDPOINT%/granules?temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z,30,60&temporal\[\]=2000-01-01T10:00:00Z,,30&temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"

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

Example of sorting by start_date in descending order: (Most recent data first)

    curl "%CMR-ENDPOINT%/granules/sort_key\[\]=-start_date


### Retrieve concept with a given cmr-concept-id

This allows retrieving the metadata for a single concept. If no format is specified the native format of the metadata will be returned.

    curl -i "%CMR-ENDPOINT%/concepts/G100000-PROV1"


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

### Search with AQL

Search collections or granules with AQL in POST request body. The AQL must conform to the schema
that is defined in `cmr-search-app/resources/schema/IIMSAQLQueryLanguage.xsd`.

    curl -i -XPOST -H "Content-Type: application/xml" %CMR-ENDPOINT%/concepts/search -d '<?xml version="1.0" encoding="UTF-8"?>
    <query><for value="collections"/><dataCenterId><all/></dataCenterId>
    <where><collectionCondition><shortName><value>S1</value></shortName></collectionCondition></where></query>'

### Document Scoring For Keyword Search

When a keyword search is requested, matched documents receive relevancy scores as follows:

A series of filters are executed against each document. Each of these has an associated boost
value. The boost values of all the filters that match a given document are multiplied together
to get the final document score. Documents that match none of the filters have a default
score of 1.0.

The filters are case insensitive, support wildcards * and ?, and are given below:

1. All keywords are conatained in the long-name field OR one of the keywords exactly matches
the short-name field - weight 1.4

2. All keywords are contained in the Project/long-name field OR one of the keywords
exactly matches the Project/short-name field - weight 1.3

3. All keywords are contained in the Platform/long-name field OR one of the keywords
exaclty matches the Platform/short-name field - weight 1.3

4. All keywords are contained in the Platform/Instrument/long-name field OR one of the keywords
exactly matches the Platform/Instrument/short-name field - weight 1.2

5. All keywords are contained in the Platform/Instrument/Sensor/long-name field OR one of the keywords exactly matches the Platform/Instrument/Sensor/short-name field - weight 1.2

6. The keyword field is a single string that exactly matches the science-keyword field - weight 1.2

7. The keyword field is a single string that exactly matches the spatial-keyword field - weight 1.1

8. The keyword field is a single string that exactly matches the temporal-keyword field  - weight 1.1


### Facets

Facets are counts of unique values from fields in items matching search results. Facets are supported with collection search results and are enabled with the `include_facets=true` parameter. Facets are supported on all collection search response formats. When `echo_compatible=true` parameter is also present, the facets are returned in the catalog-rest search_facet style in xml or json format.

#### Facets in XML Responses

Facets in XML search response formats will be formatted like the following example. The exception is ATOM XML which is the same except the tags are in the echo namespace.

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

#### Facets in JSON Responses

Facets in JSON search response formats will be formatted like the following example.

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

### Search for Tiles

Tiles are geographic regions formed by splitting the world into rectangular regions in a projected coordinate system such as Sinusoidal Projection based off an Authalic Sphere. CMR supports searching of tiles which fall within a geographic region defined by a given input geometry. Currently, only tiles in MODIS Integerized Sinusoidal Grid(click [here](https://lpdaac.usgs.gov/products/modis_products_table/modis_overview) for more details on the grid) can be searched. The input geometry could be either a minimum bounding rectangle or one of point, line or polygon in spherical coordinates. The input coordinates are to be supplied in the same format as in granule and collection spatial searches (See under "Find granules by Spatial").

A query could consist of multiple spatial parameters, two points and a bounding box for example. All the spatial parameters are OR'd in a query meaning a query will return all the tiles which intersect atleast one of the given geometries.

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

This will report the current health of the application. It checks all resources and services used by the application and reports their healthes in the response body in JSON format. For resources, the report includes an "ok?" status and a "problem" field if the resource is not OK. For services, the report includes an overall "ok?" status for the service and health reports for each of its dependencies. It returns HTTP status code 200 when the application is healthy, which means all its interfacing resources and services are healthy; or HTTP status code 503 when one of the resources or services is not healthy. It also takes pretty parameter for pretty printing the response.

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