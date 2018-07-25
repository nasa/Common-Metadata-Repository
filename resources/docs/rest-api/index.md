---
title: CMR OPeNDAP REST API Documentation

language_tabs: # must be one of https://git.io/vQNgJ
  - shell
  - javascript
  - clojure

toc_footers:
  - <a href='https://github.com/lord/slate'>Documentation Powered by Slate</a>

search: true
---

# REST API Overview

The REST API for CMR OPeNDAP aims to accomplish a few core goals:

* Provide a straight forward means of bridging the CMR and OPeNDAP deployments
* Provide operational insights into the CMR OPeNDAP REST service itself
* Do both of these as simply and in as scalable a manner as possible

The REST API is currently focused on generating OPeNDAP data access URLs,
allowing users to easily download subsetted files from OPeNDAP servers
based on metadata stored in the CMR and presented to end users by EDSC.


# About the Docs

This API documentation provides prose in the central column, using
the left collumn to highlight usage and code.

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

The following table provides a mapping between CMR OPeNDAP releases
and the API versions supported in each.

Release | REST API Versions | Status
------- | ----------------- | --------------
1.1.0   | v1, v2, v2.1      | In development
1.0.0   | v1, v2            | Released
0.1.0   | v1                | Released


# Versioned API

The CMR OPeNDAP REST API is versioned. By default, the most recent version of
the API is accessed when a request is submitted without specificing the
desired version. Currently, this is `v2.1`.

To request a specific version of the REST API, you must send an additional
header in your request to CMR OPeNDAP.

> An `Accept` header is required to request a specific version of the API:

```
"Accept: application/vnd.cmr-opendap.v2+json"
```

> The following is also valid:

```
"Accept: application/vnd.cmr-opendap.v2"
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
Cmr-Media-Type: cmr-opendap.v2; format=json
```

```clj
{...
 :cmr-media-type "cmr-opendap.v2; format=json"
 ...}
```

<aside class="warning">
  The most recent release of the CMR OPeNDAP API is always the default;
  if you experience unexpected issues and/or errors, this may be a result
  of using a new version of the API by default.
</aside>

<aside class="info">
  We recommend explicitly "pinning" your client requests to the latest
  release of the API so that you are always using exactly what you expect
  to be using. Then your use of new versions of the API will be an
  intentional decision.
</aside>


# Authorized Access

CMR OPeNDAP requires the use of tokens in order to provide access to
potentially protected collections, services, variables, and/or granules.

> A token needs to be included in each request, and this is done by sending
a special header:

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

Many of the examples in the documentation below show usage of the
Echo token.


# Responses

## Success

CMR OPeNDAP responses have pareticular formats, inherited from CMR response
data. A successful request will provide the results, the number of results
and, the time taken to process the request.

> Successful response body:

```
{
  "hits": ...,
  "took": ...,
  "items": [...]
  "warnings:" null
}
```

## Errors

When CMR OPeNDAP returns errors, it does so in a consistent format. In
particular, the body of the HTTP response is a JSON string (or stream)
representing an object with one key, `errors`, an associated value being
an array of the errors encountered.

> CMR OPeNDAP error format

```
{
  "errors": [...]
}
```

## Warnings

In either a success or error state, CMR OPeNDAP may also return a `warning`
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
  "warnings": [...],
  "items": [...]
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

This is the part of the REST API responsible for creating OPeNDAP-compatible
query URLs (intended to be sent to a deployed OPeNDAP service). The name
"OUS" (OPeNDAP URL Service) was used in the prototype for this service, and
that name has carried through here.


## Collection Resources

The CMR OPeNDAP REST API supports two different means of creating a subsetted
OPeNDAP URL for granules and/or variables in a collection: one returns a
standard JSON document; the other, more effectial means returns a JSON
document via HTTP streaming.

The resources are as follows:

* `GET /opendap/ous/collection/:concept-id`
* `GET /opendap/ous/streaming-collection/:concept-id`

> Example API usage if your client supports streaming:

```shell
curl --silent \
     -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/streaming-collection/C1200187767-EDF_OPS" | \
     jq .
```

> Example API usage if your client doesn't support streaming:

```shell
curl --silent \
     -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS" | \
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

In the sections below, the various parameters one can pass to the ous resource
are described. Each parameter is discussed in isolation, simply addressing it
in its own context. However, common usage will involve more than on parameter
acting at the same time. As such, here are some points to note:

* Currently CMR OPeNDAP supports only queries with one collection; as such,
  the collection query to CMR Search explicitly limits the results to one
  with the `page_size=1` parameter.
* When multiple variable concept ids are passed, these are sent to CMR Search
  in a single query for efficiency (as opposed to sending one variable request
  at a time).
* Similarly, multiple granules are queried at once with a single query to CMR
  Search.
* In the granule query to CMR Search, and spatial and temporal parameters that
  were passed to CMR OPeNDAP will be used to limit the granule results from
  CMR Search to just those in which the user is interested.


## CMR-Style Parameters

The example above will generate a URL for downloading all the granules and all
the variables across the globe for the given collection. To limit these, there
are various parameters supported:

* `bounding-box`
* `exclude-granules`
* `format`
* `granules`
* `temporal`
* `variables`

These may be used alone or in any combination. Details for usage are provided
below in separate sections.

Note that both hypens and underscores are supported in parameters that have
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
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?
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
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?
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
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?format=nc"
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
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?
     granules=G1200187775-EDF_OPS,G1200245955-EDF_OPS"
```


### `temporal`

This provides clients with the ability to select a temporal subset of data to
include in the granule data extracted by the OPeNDAP query, only providing data
for the time of interest.

If not provided, the entire temproal extent of the granule will be used.

The following are examples of accepted `temporal` parameter usage:

* `temporal=2002-09-01T00:00:00Z,2016-07-03T00:00:00Z`
* `temporal[]=2002-09-01T00:00:00Z,2016-07-03T00:00:00Z`
* `temporal=2000-01-01T00:00:00Z,2002-10-01T00:00:00Z&temporal=2010-07-01T00:00:00Z,2016-07-03T00:00:00Z`
* `temporal[]=2000-01-01T00:00:00Z,2002-10-01T00:00:00Z&temporal[]=2010-07-01T00:00:00Z,2016-07-03T00:00:00Z`

> Use of `temporal` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?
     variables=V1200241812-EDF_OPS,V1200241817-EDF_OPS&
     temporal=2002-09-01T00:00:00Z,2016-07-03T00:00:00Z"
```


### `variables`

This proivdes clients with the ability to limit the granule data extracted
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
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?
     variables=V1200241812-EDF_OPS,V1200241817-EDF_OPS"
```


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
* `rangesubset`
* `subset`
* `timeposition`

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
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?
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
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?format=nc"
```


### `rangesubset`

This proivdes clients with the ability to limit the granule data extracted
by the OPeNDAP query to just the variables in which you are interested.

If not provided, all variables associated in the collection metadata will
be used.

The following are examples of accepted `rangesubset` parameter usage:

* `rangesubset=V1200241812-EDF_OPS`
* `rangesubset=V1200241812-EDF_OPS,V1200241817-EDF_OPS`

> Use of `rangesubset` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?
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
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?
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

If not provided, the entire temproal extent of the granule will be used.

The following is an example of accepted `timeposition` parameter usage:

* `timeposition=2002-09-01T00:00:00Z,2016-07-03T00:00:00Z`
* `timeposition=2000-01-01T00:00:00Z,2002-10-01T00:00:00Z&timeposition=2010-07-01T00:00:00Z,2016-07-03T00:00:00Z`

> Use of `timeposition` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?
     variables=V1200241812-EDF_OPS,V1200241817-EDF_OPS&
     timeposition=2002-09-01T00:00:00Z,2016-07-03T00:00:00Z"
```


## Forthcoming

The following are coming soon:

* `POST /opendap/ous/collection/:concept-id`
* `POST /opendap/ous/collections`


# Admin Resources

## Cache

Administrative users may use a token to manage the authorization cache:

* `GET    /opendap/cache/auth`
* `DELETE /opendap/cache/auth`
* `GET    /opendap/cache/auth/:item-key`
* `DELETE /opendap/cache/auth/:item-key`

Likewise, administrators have the ability to manage the concept cache:

* `GET    /opendap/cache/concept`
* `DELETE /opendap/cache/concept`
* `GET    /opendap/cache/concept/:item-key`
* `DELETE /opendap/cache/concept/:item-key`


## Health

Even though the `health` resources are classified as "admin", they are
available for use by anyone.

* `GET     /opendap/health`
* `OPTIONS /opendap/health`


## Ping

Administrative users may use a token to access the following:

* `GET     /opendap/ping`
* `POST    /opendap/ping`
* `OPTIONS /opendap/ping`


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

CMR OPeNDAP will not only report its own errors, but errors encountered when
making calls to other services upon which it depends. Those errors are not
listed below, but will be appended to the array of errors in the response body.

## CMR OPeNDAP Warnings

Warning messages will generally be included in a successful reponse, and thus
will usually have an HTTP status of 200.

### Gridded Data

HTTP Status | Warning Message
----------- |---------------------------------
200         | The variables ___, ___, ... are not gridded.
200         | The bounding information for the query has been removed.


## CMR OPeNDAP Errors

### Authorization

HTTP Status | Error Message
----------- |----------------------------------------------------
403         | You do not have permissions to access that resource.
403         | An ECHO token is required to access this resource.

### URL Generation

#### Client-Side

HTTP Status | Error Message
----------- |----------------------------------------------------
400         | The requst includes a dataset whose processing level is not supported.
400         | Problematic processing level ___ for collection ___.
400         | The service pattern computed was empty. Is there a service associated with the given collection? Does the UMM-S record in question have values for the pattern fields?
400         | One or more of the parameters provided were invalid.
400         | The values provided for latitude are not within the valid range of -90 degrees through 90 degress.
400         | The values provided for longitude are not within the valid range of -180 degrees through 180 degress.

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
