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

### <a name="autocomplete-facets"></a> Facet Autocompletion

Auto-completion assistance for building queries. This functionality may be used to help build queries. The facet autocomplete functionality does not search for collections directly. Instead it will return suggestions of facets to help narrow a search by providing a list of available facets to construct a CMR collections search.

    curl "%CMR-ENDPOINT%/autocomplete?q=<term>[&type\[\]=<type1>[&type\[\]=<type2>]"

Collection facet autocompletion results are paged. See [Paging Details](#paging-details) for more information on how to page through autocomplete search results.

#### Autocompletion of Science Keywords
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

#### Autocompletion of Platforms
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

#### Autocomplete Parameters
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

### <a name="search-with-post"></a> Search with POST

Search collections or granules with query parameters encoded form in POST request body.

    curl -i -XPOST %CMR-ENDPOINT%/collections -d "dataset_id[]=Example%20DatasetId&dataset_id[]=Dataset2"

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