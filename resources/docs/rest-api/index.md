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


### Parameters

The example above will generate a URL for downloading all the granules and all
the variables across the globe for the given collection. To limit these, there
are various parameters supported:

* `format`
* `granules`
* `exclude-granules`
* `variables`
* `bounding-box`

These may be used alone or in any combination. Details for usage are provided
below in separate sections.

Note that both hypens and underscores are supported in parameters that have
them; you may use either, per your preference.


#### `format`

This allows the client to ask OPeNDAP to provide files in the given format.

If not provided, the default of `nc` (NetCDF) is used. Supported formats
depend upon the target OPeNDAP server.

Parameter example:

* `format=nc`

> Use of `format` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?format=nc"
```


#### `granules`

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
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?granules=G1200187775-EDF_OPS,G1200245955-EDF_OPS"
```


#### `exclude-granules`

This allows clients to perform the inverse of a granule search. If the
value of this parameter is all granules
_except_ the ones passed. Granules may either be pass

If not provided, a regular granule search is performed.

Parameter examples:

* `exclude-granules=true&granules=G1200187775-EDF_OPS`

Note that the `granules` parameter here may take any of the accepted forms
shown in the previous section.

> Use of `exclude-granules` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?exclude-granules=true&granules=G1200187775-EDF_OPS"
```


#### `variables`

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
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?variables=V1200241812-EDF_OPS,V1200241817-EDF_OPS"
```


#### `bounding-box`

This provides clients with the ability to select the spatial data to include
in the granule data extracted by the OPeNDAP query to an area of interest.

If not provided, the entire spatial extent of the granule will be used.

Parameter example:

* `bounding-box=-9.984375,56.109375,19.828125,67.640625`
* `bounding_box=-9.984375,56.109375,19.828125,67.640625`


> Use of `bounding-box` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?variables=V1200241812-EDF_OPS,V1200241817-EDF_OPS&bounding-box=-9.984375,56.109375,19.828125,67.640625"
```


### WCS-Style Parameters

The original implementation of the OPeNDAP URL translation service was not a
part of the CMR, and thus did not take into consideration the internal
standards adopted by both the CMR and EDSC code bases. Instead, it was
decided to follow the model of
[WCS](http://mapserver.org/ogc/wcs_server.html#wcs-2-0-kvp-request-parameters).
As such, the following parameters are also available, as an alternative to the
"classic" CMR/EDSC parameters:

* `format`
* `coverage`
* `rangesubset`
* `subset`

These are show in more detail in the sub-sections below.

Note that WCS-style and CMR/EDSC-style parameters may not be mixed in the same
request; you must chose one or the other, not both.


#### `format`

This allows the client to ask OPeNDAP to provide files in the given format.

If not provided, the default of `nc` (NetCDF) is used. Supported formats
depend upon the target OPeNDAP server.

Parameter example:

* `format=nc`

> Use of `format` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?format=nc"
```


#### `coverage`

This allows the client to select the granules to include (or exclude) in their
OPeNDAP queries.

If not provided, all granules for the given collection will be used.

The following are examples of accepted `coverage` parameter usage:

* `coverage=G1200187775-EDF_OPS`
* `coverage=G1200187775-EDF_OPS,G1200245955-EDF_OPS`

> Use of `coverage` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?coverage=G1200187775-EDF_OPS,G1200245955-EDF_OPS"
```


#### `rangesubset`

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
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?rangesubset=V1200241812-EDF_OPS,V1200241817-EDF_OPS"
```


#### `subset`

This provides clients with the ability to select the spatial data to include
in the granule data extracted by the OPeNDAP query to an area of interest.

If not provided, the entire spatial extent of the granule will be used.

Parameter example:

* `subset=lat(56.109375,67.640625)`
* `subset=lon(-9.984375,19.828125)`

Note that both are needed in order to define a bounding box.

> Use of `subset` in a query:

```shell
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?variables=V1200241812-EDF_OPS,V1200241817-EDF_OPS&subset=lat(56.109375,67.640625)&subset=lon(-9.984375,19.828125)"
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
