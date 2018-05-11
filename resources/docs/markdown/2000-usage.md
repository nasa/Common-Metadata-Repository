# Usage


**Contents**

* REST API Overview
* OPeNDAP URL Service
  * Collection Resource
* Miscellaneous Resources
  * Health
  * Ping


## REST API Overview

TBD


## OPeNDAP URL Service

This is the part of the REST API responsible for creating OPeNDAP-compatible
query URLs (intended to be sent to a deployed OPeNDAP service).

### Authorized Access

CMR OPeNDAP uses

### Collection Resource

The following calls are supported

* `GET /opendap/ous/collection/:concept-id`

This can be used in the following manner:

```
curl --silent \
     -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS" | \
     jq .
```
```json
{
  "hits": 2,
  "took": 0.621,
  "items": [
    "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006..."
  ]
}
```

#### Parameters

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

##### `format`

This allows the client to ask OPeNDAP to provide files in the given format.

If not provided, the default of `nc` (NetCDF) is used. Supported formats
depend upon the target OPeNDAP server.

Parameter example:

```
format=nc
```

Example usage:

```
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?format=nc"
```

##### `granules`

This allows the client to select the granules to include (or exclude) in their
OPeNDAP queries.

If not provided, all granules for the given collection will be used. If used
in conjunction with the `exclude-granules` parameter, then all granules
_except_ the ones passed will be used in the OPeNDAP query.


The following are examples of accepted `granules` parameter usage:

```
granules=G1200187775-EDF_OPS
granules=G1200187775-EDF_OPS,G1200245955-EDF_OPS
granules=G1200187775-EDF_OPS&granules=G1200245955-EDF_OPS
granules[]=G1200187775-EDF_OPS&granules[]=G1200245955-EDF_OPS
```

Example usage:

```
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?granules=G1200187775-EDF_OPS,G1200245955-EDF_OPS"
```

##### `exclude-granules`

This allows clients to perform the inverse of a granule search: all granules
_except_ the ones passed. Granules may either be pass

If not provided, a regular granule search is performed.

Parameter examples:

```
exclude-granules=true&granules=G1200187775-EDF_OPS
```

Note that the `granules` parameter here may take any of the accepted forms
shown in the previous section.

Example usage:

```
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?exclude-granules=true&granules=G1200187775-EDF_OPS"
```

##### `variables`

This proivdes clients with the ability to limit the granule data extracted
by the OPeNDAP query to just the variables in which they are interested.

If not provided, all variables associated in the collection metadata will
be used.

The following are examples of accepted `variables` parameter usage:

```
variables=V1200241812-EDF_OPS
variables=V1200241812-EDF_OPS,V1200241817-EDF_OPS
variables=V1200241812-EDF_OPS&variables=V1200241817-EDF_OPS
variables[]=V1200241812-EDF_OPS&variables[]=V1200241817-EDF_OPS
```

Example usage:

```
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?variables=V1200241812-EDF_OPS,V1200241817-EDF_OPS"
```

##### `bounding-box`

This proivdes clients with the ability to select the spatial data to include
in the granule data extracted by the OPeNDAP query to an area of interest.

If not provided, the entire spatial extent of the granule will be used.

Parameter example:

```
bounding-box=-9.984375,56.109375,19.828125,67.640625
```

Example usage:

```
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%ous/collection/C1200187767-EDF_OPS?variables=V1200241812-EDF_OPS,V1200241817-EDF_OPS&bounding-box=-9.984375,56.109375,19.828125,67.640625"
```

### Forthcoming

The following are coming soon:

* `POST /opendap/ous/collection/:concept-id`
* `POST /opendap/ous/collections`


## Miscellaneous Resources

### Health

* `GET     /opendap/health`
* `OPTIONS /opendap/health`


### Ping

Administrative users may use a token to access the following:

* `GET     /opendap/ping`
* `POST    /opendap/ping`
* `OPTIONS /opendap/ping`
