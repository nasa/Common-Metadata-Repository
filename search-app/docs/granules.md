### <a name="granule-search-by-parameters"></a> Granule Search By Parameters
**Note:** The CMR does not permit queries across all granules in all collections in order to provide fast search responses. Granule queries must target a subset of the collections in the CMR using a condition like provider, provider_id, concept_id, collection_concept_id, short_name, version or entry_title.

#### <a name="find-all-granules"></a> Find all granules for a collection.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%"


Granule search results are paged. See [Paging Details](#paging-details) for more information on how to page through granule search results.

#### <a name="g-granule-ur"></a> Find granules with a granule-ur

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&granule_ur\[\]=DummyGranuleUR"

#### <a name="g-producer-granule-id"></a> Find granules with a producer granule id

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&producer_granule_id\[\]=DummyID"

#### <a name="g-granule-ur-or-producer-granule-id"></a> Find granules matching either granule ur or producer granule id

This condition is encapsulated in a single parameter called readable_granule_name

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&readable_granule_name\[\]=DummyID"

#### <a name="g-online-only"></a> Find granules by online_only

The online_only parameter is a legacy parameter and is an alias of downloadable.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&online_only=true"

#### <a name="g-downloadable"></a> Find granules by downloadable

A granule is downloadable when it contains at least one RelatedURL of type GETDATA.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&downloadable=true"

#### <a name="g-browsable"></a> Find granules by browsable

A granule is browsable when it contains at least one RelatedURL of type GET RELATED VISUALIZATION.

    curl "%CMR-ENDPOINT%/granules?browsable=true&provider=PROV1"

#### <a name="g-additional-attribute"></a> Find granules by additional attribute

Find an additional attribute with name "PERCENTAGE" only

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&attribute\[\]=PERCENTAGE"

Find an additional attribute with name "PERCENTAGE" of type float with value 25.5

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&attribute\[\]=float,PERCENTAGE,25.5"

Find an additional attribute with name "PERCENTAGE" of type float in range 25.5 - 30.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&attribute\[\]=float,PERCENTAGE,25.5,30"

Find an additional attribute with name "PERCENTAGE" of type float with min value 25.5.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&attribute\[\]=float,PERCENTAGE,25.5,"

Find an additional attribute with name "PERCENTAGE" of type float with max value 30.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&attribute\[\]=float,PERCENTAGE,,30"

Find an additional attribute with name "X,Y,Z" with value 7.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&attribute\[\]=float,X\,Y\,Z,7"

Find an additional attribute with name "X\Y\Z" with value 7.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&attribute\[\]=float,X\Y\Z,7"

Find an additional attribute with name "MISSION_NAME" with value "Big Island, HI".

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&attribute\[\]=string,MISSION_NAME,Big Island\, HI"

Multiple attributes can be provided. The default is for granules to match all the attribute parameters. This can be changed by specifying `or` option with `options[attribute][or]=true`.

For additional attribute range search, the default is inclusive on the range boundaries. This can be changed by specifying `exclude_boundary` option with `options[attribute][exclude_boundary]=true`.

For granule additional attributes search, the default is searching for the attributes included in the collection this granule belongs to as well. This can be changed by specifying `exclude_collection` option with `options[attribute][exclude_collection]=true`.

#### <a name="g-spatial"></a> Find granules by Spatial
The parameters used for searching granules by spatial are the same as the spatial parameters used in collections searches. (See under "Find collections by Spatial" for more details.)
Note: When querying a granule which has multiple types of spatial features in the granule metadata (i.e. a Polygon and a Bounding Box), the granule will be returned if the spatial query matches at least one of the spatial types on the given granule (i.e. matches the granule's Polygon OR Bounding Box).

##### <a name="g-polygon"></a> Polygon
Polygon points are provided in counter-clockwise order. The last point should match the first point to close the polygon. The values are listed comma separated in longitude latitude order, i.e. lon1, lat1, lon2, lat2, lon3, lat3, and so on.

The polygon parameter could be either "polygon", for single polygon search:

   curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&polygon=10,10,30,10,30,20,10,20,10,10"

or "polygon[]", for single or multiple polygon search. It supports the and/or option as shown below. Default option is "and", i.e. it will match both the first polygon and the second polygon.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&polygon[]=10,10,30,10,30,20,10,20,10,10"

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&polygon[]=10,10,30,10,30,20,10,20,10,10&polygon[]=11,11,31,11,31,21,11,21,11,11"

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&polygon[]=10,10,30,10,30,20,10,20,10,10&polygon[]=11,11,31,11,31,21,11,21,11,11&options[polygon][or]=true"

Note: if you use "polygon" for multiple polygon search, it won't work because only the last polygon parameter will take effect.

##### <a name="g-bounding-box"></a> Bounding Box

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&bounding_box=-10,-5,10,5"

##### <a name="g-point"></a> Point

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&point=100,20"

##### <a name="g-line"></a> Line

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&line=-0.37,-14.07,4.75,1.27,25.13,-15.51"

##### <a name="g-circle"></a> Circle

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&circle=-87.629717,41.878112,1000"

#### <a name="g-shapefile"></a> Find granules by shapefile

As with collections, a shapefile can be uploaded to find granules that overlap the shapefile's geometry. See [Find collections by shapefile](#c-shapefile) for more details.

    curl -XPOST "%CMR-ENDPOINT%/granules" -F "shapefile=@box.zip;type=application/shapefile+zip" -F "provider=PROV1"

**NOTE**: This is an experimental feature and may not be enabled in all environments.

#### <a name="g-shapefile-simplification"></a> Simplifying shapefiles during granule search

As with collections, an uploaded shapefile can be simplified by setting the `simplify-shapefile` parameter to `true`. See [Simplifying shapefiles during collection search](#c-shapefile-simplification) for more details.

    curl -XPOST "%CMR-ENDPOINT%/granules" -F "simplify-shapefile=true" -F "shapefile=@africa.zip;type=application/shapefile+zip" -F "provider=PROV1"

#### <a name="g-orbit-number"></a> Find granules by orbit number

  Find granules with an orbit number of 10

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&orbit_number=10"

Find granules with an orbit number in a range of 0.5 to 1.5

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&orbit_number=0.5,1.5"

#### <a name="g-orbit-equator-crossing-longitude"></a> Find granules by orbit equator crossing longitude

Find granules with an exact equator crossing longitude of 90

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&equator_crossing_longitude=90"

Find granules with an orbit equator crossing longitude in the range of 0 to 10

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&equator_crossing_longitude=0,10"

Find granules with an equator crossing longitude in the range from 170 to -170
  (across the anti-meridian)

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&equator_crossing_longitude=170,-170"

#### <a name="g-orbit-equator-crossing-date"></a> Find granules by orbit equator crossing date

Find granules with an orbit equator crossing date in the range of 2000-01-01T10:00:00Z to 2010-03-10T12:00:00Z

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&equator_crossing_date=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"

The time interval in equator crossing date range searches can be specified in different ways including ISO 8601. See under [Temporal Range searches](#temporal-range-searches).

#### <a name="g-updated-since"></a> Find granules by updated_since

Find granules which have revision date starting at or after 'updated_since' param value

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&updated_since=2014-05-08T20:12:35Z"

#### <a name="g-revision-date"></a> Find granules by revision_date

This supports option `and`.

Find granules which have revision date within the ranges of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&revision_date\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z&revision_date\[\]=2015-01-01T10:00:00Z,"

#### <a name="g-created-at"></a> Find granules by created_at

 This supports option `and`.

 Find granules which were created within the ranges of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&created_at\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z&created_at\[\]=2015-01-01T10:00:00Z,"

#### <a name="g-production-date"></a> Find granules by production_date

This supports option `and`.

Find granules which have production date within the ranges of datetimes. The datetime has to be in yyyy-MM-ddTHH:mm:ssZ format. The default is inclusive on the range boundaries.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&production_date\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z&production_date\[\]=2015-01-01T10:00:00Z,"

#### <a name="g-cloud-cover"></a> Find granules by cloud_cover

Find granules with just the min cloud cover value set to 0.2

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&cloud_cover=0.2,"

Find granules with just the max cloud cover value set to 30

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&cloud_cover=,30"

Find granules with cloud cover numeric range set to min: -70.0 max: 120.0

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&cloud_cover=-70.0,120.0"

#### <a name="g-platform"></a> Find granules by platform

This supports `pattern`, `ignore_case`, `exclude_collection` and option `and`. The default behavior is that granules without platform values inherit their parent collection's platform.   This can be changed by specifying `exclude_collection` option with `options[platform][exclude_collection]=true`.

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&platform\[\]=1B"

#### <a name="g-instrument"></a> Find granules by instrument

This supports `pattern`, `ignore_case`, `exclude_collection` and option `and`. The default behavior is that granules without instrument values inherit their parent collection's instrument.   This can be changed by specifying `exclude_collection` option with `options[instrument][exclude_collection]=true`.

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&instrument\[\]=1B"

#### <a name="g-sensor"></a> Find granules by sensor param

This supports `pattern`, `ignore_case`, `exclude_collection` and option `and`. The default behavior is that granules without sensor values inherit their parent collection's sensor.   This can be changed by specifying `exclude_collection` option with `options[sensor][exclude_collection]=true`.

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&sensor\[\]=1B"

#### <a name="g-project"></a> Find granules by project

Note: An alias for the parameter 'project' is 'campaign'. As such 'campaign' can be used in place of 'project'.

This supports `pattern`, `ignore_case` and option `and`.

Find granules matching 'project' param value

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&project\[\]=2009_GR_NASA"

Find granules matching any of the 'project' param values

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&project\[\]=2009_GR_NASA&project\[\]=2013_GR_NASA"

Find granules matching the given pattern for the 'project' param value

```
curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&project\[\]=20??_GR_NASA&options\[project\]\[pattern\]=true"
```

Find granules that match all of the 'project' param values

     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&project\[\]=2009_GR_NASA&project\[\]=2013_GR_NASA&options\[project\]\[and\]=true"

#### <a name="g-concept-id"></a> Find granules by concept id

Note: more than one may be supplied

Find granule by concept id

     curl "%CMR-ENDPOINT%/granules?provider=PROV1&concept_id\[\]=G1000000002-CMR_PROV1"

Find granule by echo granule id

     curl "%CMR-ENDPOINT%/granules?provider=PROV1&echo_granule_id\[\]=G1000000002-CMR_PROV1"

Find granules by parent concept id. `concept_id` or `collection_concept_id` can be used interchangeably.

     curl "%CMR-ENDPOINT%/granules?concept_id=%CMR-EXAMPLE-COLLECTION-ID%"
     curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%"

Find granules by echo collection id

     curl "%CMR-ENDPOINT%/granules?echo_collection_id=%CMR-EXAMPLE-COLLECTION-ID%"

#### <a name="g-day-night-flag"></a> Find granules by day\_night\_flag param, supports pattern and ignore_case

```
curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&day_night_flag=night

curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&day_night_flag=day

curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&day_night=unspecified
```

#### <a name="g-twod-coordinate-system"></a> Find granules by two\_d\_coordinate\_system parameter.

Note: An alias for the parameter 'two_d_coordinate_system' is 'grid'. As such 'grid' can be used in place of 'two_d_coordinate_system'.

```
  curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&two_d_coordinate_system\[\]=wrs-1:5,10:8-10,0-10:8,12
```

The parameter expects a coordinate system name and a set of two-d coordinates. The two-d coordinates could be represented either by a single coordinate pair or a pair of coordinate ranges. ':' is used as the separator between the coordinate system name, single coordinate pairs and coordinate range pairs. The coordinates in the single coordinate pair are represented in the format "x,y". And the coordinates in the coordinate range pairs are represented in the format "x1-x2,y1-y2" where x1 and x2 are the bounds of the values for the first coordinate and y1 and y2, for the second coordinate. One can also use single values for each of the two ranges, say "x1" instead of "x1-x2", in which case the upper and lower bound are considered the same. In other words using "x1" for range is equivalent to using "x1-x1". A single query can consist of a combination of individual coordinate pairs and coordinate range pairs. For example, the query above indicates that the user wants to search for granules which have a two\_d\_coordinate\_system whose name is wrs-1 and whose two-d coordinates match(or fall within) at least one of the given pairs: a single coordinate pair (5,10), a range coordinate pair 8-10,0-10 and another single coordinate pair (8,12).

#### <a name="g-provider"></a> Find granules by provider

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules matching 'provider' param value

    curl "%CMR-ENDPOINT%/granules?provider=ASF"

Find granules matching any of the 'provider' param values

    curl "%CMR-ENDPOINT%/granules?provider=ASF&provider=LARC"

#### <a name="g-native-id"></a> Find granules by native_id

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules matching 'native_id' param value

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&native_id=nativeid1"

Find granules matching any of the 'native_id' param values

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&native_id[]=nativeid1&native_id[]=nativeid2"

#### <a name="g-short-name"></a> Find granules by short name

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules matching any of the 'short\_name' param values. The 'short\_name' here refers to the short name of the collections corresponding to the granules being searched for.

    curl "%CMR-ENDPOINT%/granules?short_name=DEM_100M&short_name=MINIMAL"

Find granules matching 'short\_name' param value with a pattern.

    curl "%CMR-ENDPOINT%/granules?short_name=D*&options[short_name][pattern]=true"

#### <a name="g-version"></a> Find granules by parent collection version

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules matching the 'short\_name' and 'version' param values. The 'short\_name' and 'version' here refers to the short name and version of the collections corresponding to the granules being searched for.

    curl "%CMR-ENDPOINT%/granules?short_name=DEM_100M&version=1"

Find granules matching the given 'short_name' and any of the 'version' param values

    curl "%CMR-ENDPOINT%/granules?short_name=DEM_100M&version=1&version=2"

#### <a name="g-entry-title"></a> Find granules by parent collection entry title

This parameter supports `pattern`, `ignore_case` and option `and`.

Find granules matching 'entry\_title' param value. The 'entry\_title' here refers to the entry title of the collections corresponding to the granules being searched for.

    curl "%CMR-ENDPOINT%/granules?entry_title=DatasetId%204"

See under "Find collections by entry title" for more examples of how to use this parameter.

#### <a name="g-entry-id"></a> Find granules by parent collection entry id

Find granules matching 'entry\_id' param value where 'entry\_id' refers to the granule's parent collection. 'entry\_id' is the concatenation of short name, an underscore, and version of the corresponding collection.

    curl "%CMR-ENDPOINT%/granules?entry_id\[\]=SHORT_V5"

Multiple collections may be specified.

    curl "%CMR-ENDPOINT%/granules?entry_id\[\]=MYCOLLECTION_V1&entry_id\[\]=OTHERCOLLECTION_V2"

See under "Find collections by entry id" for more examples of how to use this parameter.

#### <a name="g-temporal"></a> Find granules with temporal

The temporal datetime has to be in yyyy-MM-ddTHH:mm:ssZ format.

    curl "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z,30,60&temporal\[\]=2000-01-01T10:00:00Z,,30&temporal\[\]=2000-01-01T10:00:00Z,2010-03-10T12:00:00Z"

The first two values of the parameter together define the temporal bounds. See under [Temporal Range searches](#temporal-range-searches) for different ways of specifying the temporal bounds including ISO 8601.

For temporal range search, the default is inclusive on the range boundaries. This can be changed by specifying `exclude_boundary` option with `options[temporal][exclude_boundary]=true`. This option has no impact on periodic temporal searches.

#### <a name="g-cycle"></a> Find granules by cycle

Cycle is part of the track information of the granule. Track information is used to allow a user to search for granules whose spatial extent is based on an orbital cycle, pass, and tile mapping. Cycle must be a positive integer.

User can search granules by one or more cycles. e.g.

    curl -g "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&cycle[]=1&cycle[]=2"

User can only search granules by exactly one cycle value when there are passes parameters in the search.

#### <a name="g-passes"></a> Find granules by passes

Passes is part of the track information of the granule as specified in [UMM-G Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/unified-metadata-model/browse/granule). Track information is used to allow a user to search for granules whose spatial extent is based on an orbital cycle, pass, and tile mapping. Cycles and passes must be positive integers, tiles are in the format of an integer followed by L, R or F. e.g. 2L.

User can search granules by pass and tiles in a nested object called passes. Multiple passes can be specified via different indexes to search granules. There must be one and only one cycle parameter value present in the search params when searching granules with passes. Each `passes` parameter must have one and only one `pass` value. Pass and tiles within a `passes` parameter are ANDed together. Multiple passes are ORed together by default, but can be AND together through the AND options, i.e. `options[passes][AND]=true`. The following example searches for granules with orbit track info that has cycle 1, tiles cover 1L or 2F within pass 1, or 3R within pass 2.

    curl -g "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&cycle[]=1&passes[0][pass]=1&passes[0][tiles]=1L,2F&passes[1][pass]=2&passes[1][tiles]=3R"

The following example searches for granules with orbit track info that has cycle 1, tiles cover 1L and 2F within pass 1, and 3R within pass 2.

    curl -g "%CMR-ENDPOINT%/granules?collection_concept_id=%CMR-EXAMPLE-COLLECTION-ID%&cycle[]=1&passes[0][pass]=1&passes[0][tiles]=1L&passes[1][pass]=1&passes[1][tiles]=2F&passes[2][pass]=2&passes[2][tiles]=3R&options[passes][AND]=true"

#### <a name="g-exclude-by-id"></a> Exclude granules from elastic results by echo granule id and concept ids.

Note: more than one id may be supplied in exclude param

Exclude granule by echo granule id

```
curl "%CMR-ENDPOINT%/granules?provider=PROV1&provider=PROV2&echo_granule_id\[\]=G1000000002-CMR_PROV1&echo_granule_id\[\]=G1000000003-CMR_PROV1&echo_granule_id\[\]=G1000000006-CMR_PROV2&exclude\[echo_granule_id\]\[\]=G1000000006-CMR_PROV2"

curl "%CMR-ENDPOINT%/granules?provider=PROV2&exclude\[echo_granule_id\]\[\]=G1000000006-CMR_PROV2&cloud_cover=-70,120"
```

Exclude granule by concept id

    curl "%CMR-ENDPOINT%/granules?provider=PROV1&provider=PROV2&echo_granule_id\[\]=G1000000002-CMR_PROV1&echo_granule_id\[\]=G1000000003-CMR_PROV1&echo_granule_id\[\]=G1000000006-CMR_PROV2&exclude\[concept_id\]\[\]=G1000000006-CMR_PROV2"

Exclude granule by parent concept id

    curl "%CMR-ENDPOINT%/granules?provider=PROV1&provider=PROV2&echo_granule_id\[\]=G1000000002-CMR_PROV1&echo_granule_id\[\]=G1000000003-CMR_PROV1&echo_granule_id\[\]=G1000000006-CMR_PROV2&exclude\[concept_id\]\[\]=C1000000001-CMR_PROV2"

#### <a name="sorting-granule-results"></a> Sorting Granule Results

Granule results are sorted by ascending provider and start date by default. One or more sort keys can be specified using the `sort_key[]` parameter. The order used impacts searching. Fields can be prepended with a `-` to sort in descending order. Ascending order is the default but `+`(Note: `+` must be URL encoded as %2B) can be used to explicitly request ascending.

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

Examples of sorting by start_date in descending(Most recent data first) and ascending orders(Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/granules/sort_key\[\]=-start_date"
    curl "%CMR-ENDPOINT%/granules/sort_key\[\]=%2Bstart_date"

### <a name="deleted-granules"></a> Find granules that have been deleted after a given date

To support metadata harvesting, a harvesting client can search CMR for granules that are deleted after a given date. The only search parameter supported is `revision_date` and its format is slightly different from the `revision_date` parameter in regular granule search in that only one revision date can be provided and it can only be a starting date, not a date range. The only supported result format is json. The revision_date is limited to 1 year in the past.

Additionally, deleted granules search can be filtered by the provider parameter, which takes the provider id, and parent_collection_id which takes the granule's parent collection concept id.

The following search will return the concept-id, parent-collection-id, granule-ur, revision-date, and provider-id of the granules that are deleted since 01/20/2017.

    curl -i "%CMR-ENDPOINT%/deleted-granules.json?revision_date=2017-01-20T00:00:00Z&pretty=true"

__Example Response__

```
HTTP/1.1 200 OK
Date: Thu, 07 Sep 2017 18:52:04 GMT
Content-Type: ; charset=utf-8
Access-Control-Expose-Headers: CMR-Hits, CMR-Request-Id
Access-Control-Allow-Origin: *
CMR-Hits: 4
CMR-Request-Id: 03da8f3d-57b3-4ce8-bbc0-29970a4a8b30
Content-Length: 653
Server: Jetty(9.2.10.v20150310)

[{"granule-ur":"ur2","revision-date":"2017-09-07T18:51:39+0000","parent-collection-id":"C1200000009-PROV1","concept-id":"G2-PROV1","provider-id":"PROV1"},{"granule-ur":"ur1","revision-date":"2017-09-07T18:51:39.123Z","parent-collection-id":"C1200000009-PROV1","concept-id":"G1-PROV1","provider-id":"PROV1"},{"granule-ur":"ur3","revision-date":"2017-09-07T18:51:39.000Z","parent-collection-id":"C1200000010-PROV1","concept-id":"G3-PROV1","provider-id":"PROV1"},{"granule-ur":"ur4","revision-date":"2017-09-07T18:51:39.256Z","parent-collection-id":"C1200000011-PROV2","concept-id":"G4-PROV2","provider-id":"PROV2"}]
```