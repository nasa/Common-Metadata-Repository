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

    ### <a name="retrieving-concepts-by-concept-id-and-revision-id"></a> Retrieve concept with a given concept-id or concept-id & revision-id

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

### <a name="search-response-as-granule-timeline"></a> Search Response as Granule Timeline

Granule timeline queries allow clients to find time intervals with continuous granule coverage per collection. The intervals are listed per collection and contain the number of granules within each interval. A timeline search can be performed by sending a `GET` request with query parameters or a `POST` request with query parameters form encoded in request body to the `granules/timeline` route. The utility of this feature for clients is in building interactive timelines. Clients need to display on the timeline where there is granule data and where there is none.

It supports all normal granule parameters. It requires the following parameters.

  * `start_date` - The start date of the timeline intervals to search from.
  * `end_date` - The end date of to search from.
  * `interval` - The interval granularity. This can be one of year, month, day, hour, minute, or second. At least one granule found within the interval time will be considered coverage for that interval.
  * **Collection identifier** - One of the following parameters must be provided to specify which collection(s) to search:
    * `concept_id` - Collection concept id.
    * `collection_concept_id` - Collection concept id.
    * `entry_id` - Collection entry id (concatenation of short_name and version).
    * `entry_title` - Collection entry title.
    * `short_name` and `version` - Both parameters must be provided together to identify a specific collection.

  It is recommended that the timeline search be limited to a few collections for good performance.

The response format is in JSON. Intervals are returned as tuples containing three numbers like `[949363200,965088000,4]`. The two numbers are the start and stop date of the interval represented by the number of seconds since the epoch. The third number is the number of granules within that interval.

#### Example Request:

    curl -i "%CMR-ENDPOINT%/granules/timeline?concept_id=C1-PROV1&start_date=2000-01-01T00:00:00Z&end_date=2002-02-01T00:00:00.000Z&interval=month"

#### Example Response

```
[{"concept-id":"C1200000000-PROV1","intervals":[[949363200,965088000,4],[967766400,970358400,1],[973036800,986083200,3],[991353600,1072915200,3]]}]
```

### <a name="retrieving-concepts-by-concept-id-and-revision-id"></a> Retrieve concept with a given concept-id or concept-id & revision-id

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

### <a name="retrieve-provider-holdings"></a> Retrieve Provider Holdings

Provider holdings can be retrieved as XML or JSON. It will show all CMR providers, collections and granule counts regardless of the user's ACL access.

All provider holdings

    curl "%CMR-ENDPOINT%/provider_holdings.xml"

Provider holdings for a list of providers

    curl "%CMR-ENDPOINT%/provider_holdings.json?provider-id\[\]=PROV1&provider-id\[\]=PROV2"

### <a name="facets"></a> Facets

Facets are counts of unique values from fields in items matching search results. Facets are supported with collection or granule search results and are enabled with the `include_facets` parameter. There are three different types of facet responses. There are flat facets (collection only), hierarchical facets (collection only), and version 2 facets.

Flat and hierarchical facets are supported on all collection search response formats except for the opendata response format. When `echo_compatible=true` parameter is also present, the facets are returned in the catalog-rest search_facet style in XML or JSON format.

Several fields including science keywords, data centers, platforms, instruments, and location keywords can be represented as either flat or hierarchical fields. By default facets are returned in a flat format showing counts for each nested field separately. In order to retrieve hierarchical facets pass in the parameter `hierarchical_facets=true`.

Note that any counts included in hierarchical facets should be considered approximate within a small margin of an error rather than an exact count. If an exact count is required following the link provided to apply that facet will perform a search that returns an exact hits count.

#### <a name="facets-v2-response-format"></a> Version 2 Facets Response Format

Version 2 facets are enabled by setting the `include_facets=v2` parameter in either collection or granule search requests in the JSON format. In order to request faceting on granule searches, the search must be limited in scope to a single collection (e.g. by specifying a single concept ID in the collection_concept_id parameter). The max number of values in each v2 facet can be set by using facets_size parameter (i.e. facets_size[platforms]=10, facets_size[instrument]=20. Default size is 50.). facets_size is only supported for collection v2 facet search. The same fields apply in the v2 facets as for the flat facets with the addition of horizontal range facets and latency facets. When calling the CMR with a query the V2 facets are returned. These facets include the apply field described in more detail a few paragraphs below that includes the search parameter and values that need to be sent back to the CMR.

##### Specifying facet fields

Hierarchical Facet requests include any or all parts of the hierarchical structure using the `&parameter[set][subfield]=value` notation where:

* **set**: Field group number denoting related hierarchical subfields where all subfields for one facet use the same number. Values start with 0.
* **subfield**: Field name in the hierarchical facet as defined by KMS. ie: Platforms uses Basis, Category, Sub_Category, Short_Name
* **value**: facet value. ie Platform Basis has a `Air-based Platforms` value.

Example: `science_keywords_h[0][topic]=Oceans`

Example curl calls:

    %CMR-ENDPOINT%/collections.json?include_facets=v2&hierarchical_facets=true&science_keywords_h%5B0%5D%5Btopic%5D=Oceans

##### Responses

With version 2 facets the CMR makes no guarantee of which facets will be present, whether the facets returned are hierarchical or flat in nature, how many values will be returned for each field, or that the same facets will be returned from release to release. The rules for processing v2 facets are as follows.

The response will contain a field, "facets" containing the root node of the facets tree structure. Every node in the tree structure contains the following minimal structure:

```
var treeNode = {
  "title": "Example",         // A human-readable representation of the node
  "type": "group|filter|..."  // The type of node represented
}
```

Currently, the filter response type defines two node types: "group" and "filter". More may be added in the future, and clients must be built to ignore unknown node types.

##### Group Nodes

Group nodes act as containers for similar data. Depending on context, this data may, for instance, be all facet parameters (the root facet node), top-level facets, or children of existing facets. Group nodes further have a

```
var groupNode = { // Inherits treeNode fields
  "applied": true,            // true if the filter represented by this node (see Filter Nodes) or any of its descendants has been applied to the current query
  "has_children": true,       // true if the tree node has child nodes, false otherwise
  "children": [               // List of child nodes, provided at the discretion of the CMR (see below)
  ]
}
```

##### Children

In order to avoid sending unnecessary information, the CMR may in the future opt to not return children for group nodes that have facets, returning only the first few levels of the facet tree. It will, however, allow clients to appropriately display incomplete information and query the full tree as necessary.

##### Relevance

By default, clients should assume that the CMR may limit facet results to only include the most relevant child nodes in facet responses. For instance, if there are hundreds of science keywords at a particular depth, the CMR may choose to only return those that have a substantial number of results. When filtering children, the CMR makes no guarantees about the specific quantities or values of facets returned, only that applied filters attempt to surface the choices that typical users are most likely to find beneficial.

##### Filter Nodes

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

##### Collection Example
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

##### Granule Example
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

#### <a name="facets-in-xml-responses"></a> Collection Facets in XML Responses

Facets in XML search response formats will be formatted like the following examples. The exception is ATOM XML which is the same except the tags are in the echo namespace.

##### <a name="flat-xml-facets"></a> Flat XML Facets

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

##### <a name="hierarchical-xml-facets"></a> Hierarchical XML Facets

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

#### <a name="facets-in-json-responses"></a> Collection Facets in JSON Responses

Facets in JSON search response formats will be formatted like the following examples.

##### <a name="flat-json-facets"></a> Flat JSON facets

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

##### <a name="hierarchical-json-facets"></a> Hierarchical JSON facets

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

### <a name="humanizers"></a> Humanizers

Humanizers define the rules that are used by CMR to provide humanized values for various facet fields and also support other features like improved relevancy of faceted terms. The rules are defined in JSON. Operators with Admin privilege can update the humanizer instructions through the update humanizer API.

#### <a name="updating-humanizers"></a> Updating Humanizers

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

#### <a name="retrieving-humanizers"></a> Retrieving Humanizers

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

#### <a name="facets-humanizers-report"></a> Humanizers Report

The humanizers report provides a list of fields that have been humanized in CSV format. The report format is: provider, concept id, product short name, product version, original field value, humanized field value.

    curl "%CMR-ENDPOINT%/humanizers/report"

Note that this report is currently generated every 24 hours with the expectation that this more than satisfies weekly usage needs.

An administrator with system object INGEST\_MANAGEMENT\_ACL update permission can force the report to be regenerated by passing in a query parameter `regenerate=true`.