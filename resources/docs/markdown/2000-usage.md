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

The following calls are supported

* `GET     /opendap/ous/collection/:concept-id`

Example usage:

```
curl -H "Echo-Token: `cat ~/.cmr/tokens/sit`" \
     http://localhost:3012/opendap/ous/collection/C1200187767-EDF_OPS | \
     jq .
```
```json
{
  "hits": 2,
  "took": 0.621,
  "items": [
    "https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/2002/AIRS.2002.09.04.L3.RetStd001.v6.0.9.0.G13208020620.hdf.nc",
    "https://f5eil01.edn.ecs.nasa.gov/opendap/DEV01//FS2/AIRS/AIRX3STD.006/2016.07.01/AIRS.2016.07.01.L3.RetStd001.v6.0.31.0.G16187132305.hdf.nc"
  ]
}
```

The following are coming soon:

* `POST    /opendap/ous/collection/:concept-id`
* `OPTIONS /opendap/ous/collection/:concept-id`
* `POST    /opendap/ous/collections`


## Health Endpoint

* `GET     /opendap/health`
* `OPTIONS /opendap/health`


## Ping

Administrative users may use a token to access the following:

* `GET     /opendap/ping`
* `POST    /opendap/ping`
* `OPTIONS /opendap/ping`
