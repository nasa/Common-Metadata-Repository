---
title: CMR Service-Bridge REST API Documentation

language_tabs: # must be one of https://git.io/vQNgJ
  - shell
  - javascript
  - clojure

toc_footers:
  - <a href='https://github.com/lord/slate'>Documentation Powered by Slate</a>

search: true
---

# REST API Overview

The REST API for CMR Service-Bridge aims to accomplish a few core goals:

* Provide a straight forward means of bridging the CMR and various service
  deployments
* Provide operational insights into the CMR Service-Bridge REST service itself
* Do both of these as simply and in as scalable a manner as possible

The REST API is currently focused on the following:
* generating OPeNDAP data access URLs, and
* providing size estimates for subsetted GIS data

The first allows users to easily download subsetted files from OPeNDAP servers
based on metadata stored in the CMR and presented to end users by EDSC.
The second provides users with the ability to see what the total estimated size
of their data request would be. This will give users the necessary feedback
on whether the total of their subsetted data is too large, too small, or just
right.


# About the Docs

This API documentation provides prose in the central column, using
the left column to highlight usage and code.

Note that, due to some of the long URLs and query examples, the lines
in the examples are broken up -- this is done for the sake of visiability
on the page. When you use these to make a query you must remove newline
breaks.

<aside class="error">
  In many cases, copying and pasting the examples as-is will not work!
  Line separations have been added to the examples for visual clarity; to
  actually use these, you need to make sure extraneous newlines have been
  removed.
</aside>

<aside class="warning">
  The concept ids for collections, variables, granules, etc., used in the
  examples below could very well have changed since this documentation
  was first created, or you may be using the service in a different
  environment (which would have different concept ids). Be sure to use
  the concept ids that you know exist in your target environment.
</aside>


# Releases

The following table provides a mapping between CMR Service-Bridge releases
and the API versions supported in each.

Release             | REST API Versions | Status
------------------- | ----------------- | --------------
1.6.0-SNAPSHOT      | v1, v2, v2.1, v3  | In development
1.1.0 through 1.5.0 | v1, v2, v2.1      | Released
1.0.0               | v1, v2            | Released
0.1.0               | v1                | Released

Summary of changes in REST API:

* v3: Changed default format to DAP4; Added dap-version parameter
* v2.1: Use of UMM-Var 1.2 new dimensional metadata to remove old hacks
* v2: Breaking change to cache management admin resource
* v1: First version of API (including admin resources)


# Versioned API

The CMR Service-Bridge REST API is versioned. By default, the most recent version of
the API is accessed when a request is submitted without specifying the
desired version. Currently, this is `v3`.

To request a specific version of the REST API, you must send an additional
header in your request to CMR Service-Bridge.

> An `Accept` header is required to request a specific version of the API:

```
"Accept: application/vnd.cmr-service-bridge.v2+json"
```

> The following is also valid:

```
"Accept: application/vnd.cmr-service-bridge.v2"
```

All HTTP responses from the REST API also provide information on the version
used when processing the request. This can be useful for clients debugging
calls to the service.

> Examine the headers with a call to the service:

```shell
curl -i ...
```

```clj
(request/get ...)
```

> The response headers will include the version used by the service:

```shell
Cmr-Media-Type: cmr-service-bridge.v2; format=json
```

```clj
{...
 :cmr-media-type "cmr-service-bridge.v2; format=json"
 ...}
```

<aside class="warning">
  The most recent release of the CMR Service-Bridge API is always the default;
  if you experience unexpected issues and/or errors, this may be a result
  of using a new version of the API by default.
</aside>

<aside class="info">
  We recommend explicitly "pinning" your client requests to the latest
  release of the API so that you are always using exactly what you expect
  to be using. Then your use of new versions of the API will be an intentional decision.
</aside>


# Authorized Access

CMR Service-Bridge requires the use of tokens in order to provide access to
potentially protected collections, services, variables, and/or granules.

> A token needs to be included in each request, and this is done by sending a special header:

```
"Echo-Token: <YOUR_TOKEN>"
```

> For example, if you have stored your token in a file:

```shell
"Echo-Token: `cat /path/to/tokens/sit`"
```

```clj

```

Both ECHO as well as URS/Earthdata Login tokens are supported.

Many of the examples in the documentation below show usage of the ECHO token.


# Responses

## Success

CMR Service-Bridge responses have particular formats, inherited from CMR response
data. A successful request will provide the results, the number of results
and, the time taken to process the request.

> Successful response body:

```
{
  "hits": ...,
  "took": ...,
  "sa-header": "[...]",
  "items": [...],
  "request-id": ...,
  "warnings:" null
}
```

## Search-After

CMR Service-Bridge allows the use of CMR-Search-After header to take advantage of the 
CMR Search-After support for paging. Any search against CMR Service Bridge that has results
not fully returned in the current request will return a search-after value in the 
CMR-Search-After header of the search response. User can then pass this returned value 
in the CMR-Search-After header of the following request to retrieve the next page of result 
based on the specified page_size. See CMR API document on Search-After for a detailed description.

## Request IDs

Each request is given a random UUID to allow for better debugging and
correlation of events. In addition to being provided in the response body
for a successful query, the request ID is also sent in the response headers.
In the headers, the request ID has the field name `Cmr-Request-Id`.

> Example request ID in response headers:

```
HTTP/1.1 200 OK
Cmr-Took: 0.541
Cmr-Hits: 1
Cmr-Search-After: ["hmr_tme", 10310976000000, 1200267320]
Content-Type: application/json
Cmr-Request-Id: 23713cac-5225-c29c-ca5a-f59d0982de8b
Cmr-Media-Type: cmr-service-bridge.v3; format=*
Access-Control-Allow-Origin: *
Content-Length: 170
Server: http-kit
Date: Mon, 26 Nov 2018 16:16:27 GMT
```

## Errors

When CMR Service-Bridge returns errors, it does so in a consistent format. In
particular, the body of the HTTP response is a JSON string (or stream)
representing an object with one key, `errors`, an associated value being
an array of the errors encountered.

> CMR Service-Bridge error format

```
{
  "errors": [...]
}
```

## Warnings

In either a success or error state, CMR Service-Bridge may also return a `warning`
key containing one or more messages. This is reserved for future use and
will be the primary mechanism used to notify clients of coming changes in the
API that will impact users of the default version.

<aside class="info">
  As a best practice, clients should check for and log any warning messages.
</aside>


> Warnings sent in a successful response body:

```
{
  "hits": ...,
  "took": ...,
  "sa-header": "[...]",
  "request-id": ...,
  "items": [...],
  "warnings": [...]
}
```

> Warnings sent in an error response body:

```
{
  "warnings": [...],
  "errors": [...]
}
```


# OUS Resources

This is the part of the REST API responsible for creating Service-Bridge-compatible
query URLs (intended to be sent to a deployed Service-Bridge service). The name
"OUS" (OPeNDAP URL Service) was used in the prototype for this service, and
that name has carried through here.


## Tag Associations

Currently, OUS depends upon CMR tag associations in order to map from a source file
(e.g., an HDF granule file) to a URL that represents an OPeNDAP service which supports
subsetting on that file. In other words, this allows OUS to convert a granule's archive
location to an OPeNDAP location.

> To map a granule's archive location to an OPeNDAP location, we first need to create a
tag using the tag key `cmr.earthdata.nasa.ous.datafile.replace`:

```shell
curl --silent \
     -XPOST \
     -H "Content-Type: application/json" \
     -H "Echo-Token: $MY_TOKEN" \
     "%%BASE_URL%%search/tags \
     -d {"tag_key" "cmr.earthdata.nasa.ous.datafile.replace", "description" "This tag will provide a mapping from archive location to OPeNDAP location for one or more collections"}
```

> Then, we can associate the tag with the collection. We do this by using a regex that
replaces the archive location with the OPeNDAP location:

```shell
curl --silent \
     -XPOST \
     -H "Content-Type: application/json" \
     -H "Echo-Token: $MY_TOKEN" \
     "%%BASE_URL%%search/tags/cmr.earthdata.nasa.ous.datafile.replace/associations \
     -d '[{"concept_id": "C1224363486-EEDTEST",
           "data": {"match": "/data//", "replace": "/opendap/"}}]'
```

> Then, for any future OUS queries made to that collection and it's granules, OUS will
perform the substitution behind the scenes. This is demonstrated by the following query
to our tagged collection:

```shell
curl \
     -H "Echo-Token: $MY_TOKEN" \
     "%%BASE_URL%%service-bridge/ous/collection/C1224363486-EEDTEST?granules=G1224363487-EEDTEST
```
```shell
{"hits":1,
 "took":0.244,
 "items":[
   "http://e4ftl01.cr.usgs.gov:40510/opendap/ASTT/AST_L1T.003/2001.11.29/AST_L1T_00311292001175440_20150303161825_63101.hdf.nc"],
 ...}
```

> The returned results show the appropriate OPeNDAP URLS for the granule's archive location.


## Collection Resources

The CMR Service-Bridge REST API supports two different means of creating a subsetted
OPeNDAP URL for granules and/or variables in a collection: one returns a
standard JSON document; the other, more effectual means returns a JSON document via HTTP streaming.

The resources are as follows:

* `GET /service-bridge/ous/collection/:concept-id`
* `GET /service-bridge/ous/streaming-collection/:concept-id`

> Example API usage if your client supports streaming:

```shell
curl --silent \
     -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/ous/streaming-collection/C1200187767-EDF_OPS" | \
     jq .
```

> Example API usage if your client doesn't support streaming:

```shell
curl --silent \
     -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/ous/collection/C1200187767-EDF_OPS" | \
     jq .
```

> Both of which return the same result:

```shell
{
  "hits": 2,
  "took": 0.621,
  "items": [
    "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006..."
  ]
}
```

Both take the same parameters; the second one takes better, more efficient
advantage of system resources (micro-threads and OS threads). In particular,
the second one avoids making blocking calls in the request-processing thread;
as such, we recommend that this resource be preferred. The only requirement is
that your HTTP client supports streaming.


## Interaction of Parameters

In each sections below, the various parameters one can pass to the OUS resource
are described. Each parameter is discussed in isolation, simply addressing it
in its own context. However, common usage will involve more than one parameter
acting at the same time. As such, here are some points to note:

* Currently CMR Service-Bridge supports only queries with one collection; as such,
  the collection query to CMR Search explicitly limits the results to one
  with the `page_size=1` parameter.
* When multiple variable concept ids are passed, these are sent to CMR Search
  in a single query for efficiency (as opposed to sending one variable request
  at a time).
* Similarly, multiple granules are queried at once with a single query to CMR
  Search.
* In the granule query to CMR Search, spatial and temporal parameters that
  were passed to CMR Service-Bridge will be used to limit the granule results from
  CMR Search to just those in which the user is interested.


## CMR-Style Parameters

The example above will generate a URL for downloading all the granules and all
the variables across the globe for the given collection. To limit these, there
are various parameters supported:

* `bounding-box`
* `exclude-granules`
* `format`
* `dap-version`
* `granules`
* `temporal`
* `variables`
* `page-size`
* `page-num`


These may be used alone or in any combination. Details for usage are provided
below in separate sections.

Note that both hyphens and underscores are supported in parameters that have
them; you may use either, per your preference.


### `bounding-box`

This provides clients with the ability to select the spatial data to include
in the granule data extracted by the OPeNDAP query to an area of interest.

If not provided, the entire spatial extent of the granule will be used.

The following are examples of accepted `bounding-box` parameter usage,
note the acceptance of either dash or underscore in the parameter name:

* `bounding-box=-9.984375,56.109375,19.828125,67.640625`
* `bounding_box=-9.984375,56.109375,19.828125,67.640625`


> Use of `bounding-box` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/ous/collection/C1200187767-EDF_OPS?
     variables=V1200241812-EDF_OPS,V1200241817-EDF_OPS&
     bounding-box=-9.984375,56.109375,19.828125,67.640625"
```

Note that spatial subsetting on non-gridded data is not supported. If
`bounding-box` is provided against a collection whose granules
are not gridded, the spatial subsetting information will be dropped and one
or more warning messages will be returned in the payload.


### `exclude-granules`

This allows clients to perform the inverse of a granule search. If the
value of this parameter is set to `"true"`, then all granules _except_
the ones passed will be returned.

If not provided, a regular granule search is performed.

The following are examples of accepted `exclude-granules` parameter usage,
note the acceptance of either dash or underscore in the parameter name:

* `exclude-granules=true&granules=G1200187775-EDF_OPS`
* `exclude_granules=true&granules=G1200187775-EDF_OPS`

Note that the `granules` parameter here may take any of the accepted forms
shown in the `granules` section below.

> Use of `exclude-granules` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/ous/collection/C1200187767-EDF_OPS?
     exclude-granules=true&
     granules=G1200187775-EDF_OPS"
```


### `format`

This allows the client to ask OPeNDAP to provide files in the given format.

If not provided, the default of `nc` (NetCDF) is used. Supported formats
depend upon the target OPeNDAP server.

The following is an example of accepted `format` parameter usage:

* `format=nc`

> Use of `format` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/ous/collection/C1200187767-EDF_OPS?format=nc"
```

### `dap-version`

This allows the client to request OPeNDAP links in [DAP4](https://docs.opendap.org/index.php/DAP4:_Specification_Volume_1) or [DAP2](https://earthdata.nasa.gov/esdis/esco/standards-and-references/data-access-protocol-2) format.

`dap-version` is supported since API v3. It will be ignored when used with API versions before v3.

The valid values of `dap-version` parameter is either 4 or 2. If not provided, the default value of `4` (DAP4) is used.

The following is an example of accepted `dap-version` parameter usage:

* `dap-version=2`

> Use of `dap-version` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/ous/collection/C1200187767-EDF_OPS?dap-version=2"
```

### `granules`

This allows the client to select the granules to include (or exclude) in their
OPeNDAP queries.

If not provided, all granules for the given collection will be used. If used
in conjunction with the `exclude-granules` parameter, then all granules
_except_ the ones passed will be used in the OPeNDAP query.


The following are examples of accepted `granules` parameter usage:

* `granules=G1200187775-EDF_OPS`
* `granules=G1200187775-EDF_OPS,G1200245955-EDF_OPS`
* `granules=G1200187775-EDF_OPS&granules=G1200245955-EDF_OPS`
* `granules[]=G1200187775-EDF_OPS&granules[]=G1200245955-EDF_OPS`

> Use of `granules` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/ous/collection/C1200187767-EDF_OPS?
     granules=G1200187775-EDF_OPS,G1200245955-EDF_OPS"
```


### `temporal`

This provides clients with the ability to select a temporal subset of data to
include in the granule data extracted by the OPeNDAP query, only providing data
for the time of interest.

If not provided, the entire temporal extent of the granule will be used.

The following are examples of accepted `temporal` parameter usage:

* `temporal=2002-09-01T00:00:00Z,2016-07-03T00:00:00Z`
* `temporal[]=2002-09-01T00:00:00Z,2016-07-03T00:00:00Z`
* `temporal=2000-01-01T00:00:00Z,2002-10-01T00:00:00Z&temporal=2010-07-01T00:00:00Z,2016-07-03T00:00:00Z`
* `temporal[]=2000-01-01T00:00:00Z,2002-10-01T00:00:00Z&temporal[]=2010-07-01T00:00:00Z,2016-07-03T00:00:00Z`

> Use of `temporal` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/ous/collection/C1200187767-EDF_OPS?
     variables=V1200241812-EDF_OPS,V1200241817-EDF_OPS&
     temporal=2002-09-01T00:00:00Z,2016-07-03T00:00:00Z"
```


### `variables`

This provides clients with the ability to limit the granule data extracted
by the OPeNDAP query to just the variables in which you are interested.

If not provided, all variables associated in the collection metadata will
be used.

The following are examples of accepted `variables` parameter usage:

* `variables=V1200241812-EDF_OPS`
* `variables=V1200241812-EDF_OPS,V1200241817-EDF_OPS`
* `variables=V1200241812-EDF_OPS&variables=V1200241817-EDF_OPS`
* `variables[]=V1200241812-EDF_OPS&variables[]=V1200241817-EDF_OPS`

> Use of `variables` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/ous/collection/C1200187767-EDF_OPS?
     variables=V1200241812-EDF_OPS,V1200241817-EDF_OPS"
```

### `page-size` 
Number of results per page - default is 10, max is 2000
The following are examples of accepted `page-size` parameter usage:

* `page-size=5`
* `page_size=5`

### `page-num` 
The page number to return

The following are examples of accepted `page-num` parameter usage:

* `page-num=2`
* `page_num=2`

## WCS-Style Parameters

The original implementation of the OPeNDAP URL translation service was not a
part of the CMR, and thus did not take into consideration the internal
standards adopted by both the CMR and EDSC code bases. Instead, it was
decided to follow the model of
[WCS](http://mapserver.org/ogc/wcs_server.html#wcs-2-0-kvp-request-parameters).
As such, the following parameters are also available, as an alternative to the
"classic" CMR/EDSC parameters:

* `coverage`
* `format`
* `dap-version`
* `rangesubset`
* `subset`
* `timeposition`
* `page-size`
* `page-num`

These are show in more detail in the sub-sections below.

Note that WCS-style and CMR/EDSC-style parameters may not be mixed in the same
request; you must chose one or the other, not both.


### `coverage`

This allows the client to select the granules to include (or exclude) in their
OPeNDAP queries.

If not provided, all granules for the given collection will be used.

The following are examples of accepted `coverage` parameter usage:

* `coverage=G1200187775-EDF_OPS`
* `coverage=G1200187775-EDF_OPS,G1200245955-EDF_OPS`

> Use of `coverage` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/ous/collection/C1200187767-EDF_OPS?
     coverage=G1200187775-EDF_OPS,G1200245955-EDF_OPS"
```


### `format`

This allows the client to ask OPeNDAP to provide files in the given format.

If not provided, the default of `nc` (NetCDF) is used. Supported formats
depend upon the target OPeNDAP server.

The following is an example of accepted `format` parameter usage:

* `format=nc`

> Use of `format` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/ous/collection/C1200187767-EDF_OPS?format=nc"
```


### `dap-version`

This allows the client to request OPeNDAP links in [DAP4](https://docs.opendap.org/index.php/DAP4:_Specification_Volume_1) or [DAP2](https://earthdata.nasa.gov/esdis/esco/standards-and-references/data-access-protocol-2) format.

`dap-version` is supported since API v3. It will be ignored when used with API versions before v3.

The valid values of `dap-version` parameter is either 4 or 2. If not provided, the default value of `4` (DAP4) is used.

The following is an example of accepted `dap-version` parameter usage:

* `dap-version=2`

> Use of `dap-version` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/ous/collection/C1200187767-EDF_OPS?dap-version=2"
```


### `rangesubset`

This provides clients with the ability to limit the granule data extracted
by the OPeNDAP query to just the variables in which you are interested.

If not provided, all variables associated in the collection metadata will
be used.

The following are examples of accepted `rangesubset` parameter usage:

* `rangesubset=V1200241812-EDF_OPS`
* `rangesubset=V1200241812-EDF_OPS,V1200241817-EDF_OPS`

> Use of `rangesubset` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/ous/collection/C1200187767-EDF_OPS?
     rangesubset=V1200241812-EDF_OPS,V1200241817-EDF_OPS"
```


### `subset`

This provides clients with the ability to select the spatial data to include
in the granule data extracted by the OPeNDAP query to an area of interest.

If not provided, the entire spatial extent of the granule will be used.

The following are examples of accepted `subset` parameter usage:

* `subset=lat(56.109375,67.640625)`
* `subset=lon(-9.984375,19.828125)`

Note that both are needed in order to define a bounding box.

> Use of `subset` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/ous/collection/C1200187767-EDF_OPS?
     variables=V1200241812-EDF_OPS,V1200241817-EDF_OPS&
     subset=lat(56.109375,67.640625)&subset=lon(-9.984375,19.828125)"
```

Note that spatial subsetting on non-gridded data is not supported. If
`subset` is provided against a collection whose granules
are not gridded, the spatial subsetting information will be dropped and one
or more warning messages will be returned in the payload.


### `timeposition`

This provides clients with the ability to select a temporal subset of data to
include in the granule data extracted by the OPeNDAP query, only providing data
for the time of interest.

If not provided, the entire temporal extent of the granule will be used.

The following is an example of accepted `timeposition` parameter usage:

* `timeposition=2002-09-01T00:00:00Z,2016-07-03T00:00:00Z`
* `timeposition=2000-01-01T00:00:00Z,2002-10-01T00:00:00Z&timeposition=2010-07-01T00:00:00Z,2016-07-03T00:00:00Z`

> Use of `timeposition` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/ous/collection/C1200187767-EDF_OPS?
     variables=V1200241812-EDF_OPS,V1200241817-EDF_OPS&
     timeposition=2002-09-01T00:00:00Z,2016-07-03T00:00:00Z"
```

### `page-size` 
Number of results per page - default is 10, max is 2000
The following are examples of accepted `page-size` parameter usage:

* `page-size=5`
* `page_size=5`

### `page-num` 
The page number to return

The following are examples of accepted `page-num` parameter usage:

* `page-num=2`
* `page_num=2`

## Forthcoming

The following are coming soon:

* `POST /service-bridge/ous/collection/:concept-id`
* `POST /service-bridge/ous/collections`


# SES Resources

## Collection Resources

The Size Estimation Service offers a REST API for estimating the download sizes of subsetted
collection-based granule queries. The usage is almost identical to the OUS resources, the
primary different being the path segment:

* `GET /service-bridge/size-estimate/collection/:concept-id`


## Parameters

Two parameters are required in a query string to this resource:

* `granules` which is comma-separated for two or more granules, or `granules[]`,
  which is repeated for each granule in the query string
* `variables(or variable_aliases)`  which is comma-separated for two or more variables(or variable-aliases),
or `variables[](or variable_aliases[]`, which is repeated for each variable(or variable_alias) in the query string.
Note: values for variable_aliases could be aliases or the group nodes of the aliases. Both `alias=<alias>`
and `alias=<alias>/*` are searched in CMR variable search to take both alias and group nodes into account.

A `service_id` parameter is optional; if not provided, the service type of `opendap` is assumed.
otherwise, the service type will be retrieved using the service_id through OUS call to CMR.

A `format` parameter is optional; if not provided, the format of `nc` (NetCDF3) is assumed for
`opendap` service type and the format of `native` is assumed for `esi` service type.
Currently supported `format` values are (case insensitive):

For `opendap` service type:

* `dods` (binary)
* `nc`
* `nc4`
* `ascii`

For `esi` service type:

* `nc` (or `netcdf`)
* `nc4` (or `netcdf4-cf`,`netcdf4`,`netcdf-4`)
* `tabular_ascii`
* `native`
* `geotiff`
* `shapefile`

All parameters behave as documented above in the OUS sections on `granules`, `variables`, and
`format`, so see those sections for more details.

A final parameter that is unique to the size estimation service is `total-granule-input-bytes`:
this is required when passing `nc4` or `ascii` as the format values. This parameter represents
the size of the granules when no subsetting operation is being performed.

## Examples

Some example usage is given in the adjacent panel to the right; towards the bottom you will see
examples for spatial subsetting sizing estimates.

> Get a size estimate for one granule and one variable:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/size-estimate/collection/C1200288493-EDF_DEV01?
     granules=G1200288503-EDF_DEV01&
     variables=V1200296418-EDF_DEV01"
```

> Result:

```shell
{
  "hits": 1,
  "took": 0.595,
  "items": [
    {
      "bytes": 11526291,
      "mb": 10.992327690124512,
      "gb": 0.010734695009887218
    }
  ],
  "warnings": null
}
```

> Since we didn't provide a format, the default of `nc` is assumed.

> Get a size estimate for one granule and multiple variables in DODS (binary) format:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/size-estimate/collection/C1200288493-EDF_DEV01?
     granules=G1200288503-EDF_DEV01&
     variables=V1200296418-EDF_DEV01,V1200296176-EDF_DEV01,V1200296424-EDF_DEV01&
     format=dods"
```

> Result:

```shell
{
  "hits": 1,
  "took": 0.379,
  "items": [
    {
      "bytes": 40320000,
      "mb": 38.4521484375,
      "gb": 0.037550926208496094
    }
  ],
  "warnings": null
}
```
> Get a size estimate for multiple granules and multiple variables in DODS (binary) format:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/size-estimate/collection/C1200288493-EDF_DEV01?
     granules=G1200288503-EDF_DEV01,G1200288508-EDF_DEV01,G1200288512-EDF_DEV01&
     variables=V1200296418-EDF_DEV01,V1200296176-EDF_DEV01,V1200296424-EDF_DEV01&
     format=dods"
```

> Result:

```shell
{
  "hits": 1,
  "took": 0.364,
  "items": [
    {
      "bytes": 120960000,
      "mb": 115.3564453125,
      "gb": 0.11265277862548828
    }
  ],
  "warnings": null
}
```

> And, as stated, if you prefer the array type parameters, you may use those as well
to the same effect:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%BASE_URL%%service-bridge/size-estimate/collection/C1200288493-EDF_DEV01?
     granules\[\]=G1200288503-EDF_DEV01&
     granules\[\]=G1200288508-EDF_DEV01&
     granules\[\]=G1200288512-EDF_DEV01&
     variables\[\]=V1200296418-EDF_DEV01&
     variables\[\]=V1200296176-EDF_DEV01&
     variables\[\]=V1200296424-EDF_DEV01&
     format=dods"
```

> Result:

```shell
{
  "hits": 1,
  "took": 0.364,
  "items": [
    {
      "bytes": 120960000,
      "mb": 115.3564453125,
      "gb": 0.11265277862548828
    }
  ],
  "warnings": null
}
```


> For spatial subsetting, simply include the `bounding-box` parameter, per the
documentation above (see the "bounding-box" subheading in the "OUS Resources"
section):

```shell


```


# Admin Resources

## Cache

Administrative users may use a token to manage the authorization cache:

* `GET    /service-bridge/cache/auth`
* `DELETE /service-bridge/cache/auth`
* `GET    /service-bridge/cache/auth/:item-key`
* `DELETE /service-bridge/cache/auth/:item-key`

Likewise, administrators have the ability to manage the concept cache:

* `GET    /service-bridge/cache/concept`
* `DELETE /service-bridge/cache/concept`
* `GET    /service-bridge/cache/concept/:item-key`
* `DELETE /service-bridge/cache/concept/:item-key`


## Health

Even though the `health` resources are classified as "admin", they are
available for use by anyone.

* `GET     /service-bridge/health`
* `OPTIONS /service-bridge/health`


## Ping

Administrative users may use a token to access the following:

* `GET     /service-bridge/ping`
* `POST    /service-bridge/ping`
* `OPTIONS /service-bridge/ping`


# Testing Resources

The following resources are provided for use in various tests:

* `GET /testing/401`
* `GET /testing/403`
* `GET /testing/404`
* `GET /testing/405`
* `GET /testing/500`
* `GET /testing/503`


# Errors Messages

## Dependent Services

CMR Service-Bridge will not only report its own errors, but errors encountered when
making calls to other services upon which it depends. Those errors are not
listed below, but will be appended to the array of errors in the response body.

## CMR Service-Bridge Warnings

Warning messages will generally be included in a successful response, and thus
will usually have an HTTP status of 200.

### Gridded Data

HTTP Status | Warning Message
----------- |---------------------------------
200         | The variables ___, ___, ... are not gridded.
200         | The bounding information for the query has been removed.


## CMR Service-Bridge Errors

### Authorization

HTTP Status | Error Message
----------- |----------------------------------------------------
403         | You do not have permissions to access that resource.
403         | An ECHO token is required to access this resource.

### URL Generation

#### Client-Side

HTTP Status | Error Message
----------- |----------------------------------------------------
400         | The request includes a dataset whose processing level is not supported.
400         | Problematic processing level ___ for collection ___.
400         | The service pattern computed was empty. Is there a service associated with the given collection? Does the UMM-S record in question have values for the pattern fields?
400         | One or more of the parameters provided were invalid.
400         | The values provided for latitude are not within the valid range of -90 degrees through 90 degrees.
400         | The values provided for longitude are not within the valid range of -180 degrees through 180 degrees.

#### Server-Side

HTTP Status | Error Message
----------- |----------------------------------------------------
500         | There was a problem extracting a service data file from the granule.
500         | There was a problem creating URLs from granule file data: couldn't match default service pattern %s to service ___.
500         | Problematic granules: [...].
500         | There was a problem extracting a data URL from the granule's service data file.
500         | There was a problem extracting granule metadata.
500         | There was a problem extracting service metadata.
500         | There was a problem extracting variable metadata.

### General

HTTP Status | Error Message
----------- |----------------------------------------------------
400-599     | HTTP Error status code: ___.
400         | This capability is not currently implemented.
400         | This capability is not currently supported.
