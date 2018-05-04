# Usage


**Contents**

* REST API
* JVM Library
* Client Library


## REST API Overview

TBD


## OPeNDAP URL Service

This is the part of the REST API responsible for creating OPeNDAP-compatible
query URLs (intended to be sent to a deployed OPeNDAP service).

### Collection Resource

The following calls are supported

* `GET /opendap/ous/collection/:concept-id`

This can be used in the following manner:

```
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     "%%OPENDAP_BASE_URL%%/opendap/ous/collection/C1200187767-EDF_OPS | \
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

##### `format`

This allows the client to ask OPeNDAP to provide files in the given format.

If not provided, the default of `nc` (NetCDF) is used. Supported formats
depend upon the target OPeNDAP server.

##### `granules`

TBD

##### `exclude-granules`

TBD

##### `variables`

TBD

##### `bounding-box`

TBD

### Forthingcoming

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
