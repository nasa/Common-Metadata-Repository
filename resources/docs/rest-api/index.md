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

<aside class="warning">
  In many cases, copying and pasting the examples as-is will not work!
  Line separations have been added to the examples for visual clarity; to
  actually use these, you need to make sure extraneous newlines have been
  removed.
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

```
"Echo-Token: `cat ~/.cmr/tokens/sit`"
```

Both ECHO as well as URS/Earthdata Login tokens are supported.

Many of the examples in the documentation below show usage of the
Echo token.


# Versioned API

The CMR OPeNDAP REST API is versioned. By default, the most recent version of
the API is accessed when a request is submitted without specificing the
desired version.

To request a specific version of the REST API, you must send an additional
header in your request to CMR OPeNDAP.

> An `Accept` header is required to request a specific version of the API:

```
"Accept: application/vnd.cmr-opendap.v1+json"
```

> The following is also valid:

```
"Accept: application/vnd.cmr-opendap.v1"
```

All HTTP responses from the REST API also provide information on the version
used when processing the request. This can be useful for clients debugging
calls to the service.

> Examine the headers with a call to the service:

```shell
curl -i ...
```

> The response headers will include the version used by the service:

```shell
Cmr-Media-Type: cmr-opendap.v2; format=json
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

The following are examples of accepted `bounding-box` parameter usage:

* `bounding-box=-9.984375,56.109375,19.828125,67.640625`
* `bounding_box=-9.984375,56.109375,19.828125,67.640625`


> Use of `bounding-box` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?
     variables=V1200241812-EDF_OPS,V1200241817-EDF_OPS&
     bounding-box=-9.984375,56.109375,19.828125,67.640625"
```


### `exclude-granules`

This allows clients to perform the inverse of a granule search. If the
value of this parameter is set to `"true"`, then all granules _except_
the ones passed will be returned.

If not provided, a regular granule search is performed.

The following is an example of accepted `exclude-granules` parameter usage:

* `exclude-granules=true&granules=G1200187775-EDF_OPS`

Note that the `granules` parameter here may take any of the accepted forms
shown in the previous section.

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

Note that both are needed in order to define a bounding box.

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


### `timeposition`

This provides clients with the ability to select a temporal subset of data to
include in the granule data extracted by the OPeNDAP query, only providing data
for the time of interest.

If not provided, the entire temproal extent of the granule will be used.

The following is an example of accepted `timeposition` parameter usage:

* `timeposition=2002-09-01T00:00:00Z,2016-07-03T00:00:00Z`
* `timeposition=2000-01-01T00:00:00Z,2002-10-01T00:00:00Z&timeposition=2010-07-01T00:00:00Z,2016-07-03T00:00:00Z`

Note that both are needed in order to define a bounding box.

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

Administrative users may use a token to access the following:

* `GET    /opendap/cache`
* `DELETE /opendap/cache`
* `GET    /opendap/cache/:item-key`
* `DELETE /opendap/cache/:item-key`


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

# Errors

TBD
