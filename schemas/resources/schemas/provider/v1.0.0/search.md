### <a name="provider"></a> Provider

Provider metadata describes a provider of metadata in the CMR. Provider metadata
is stored in the JSON format [UMM-Provider Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/otherschemas/browse/provider).

#### <a name="searching-for-providers"></a> Searching for providers

Providers can be searched for by sending a request to `%CMR-ENDPOINT%/providers`.
UMM JSON response formats are the only supported format.

Providers search results are not paged.
<!--
See [Paging Details](#paging-details) for more information on how to page through Provider search results.
-->

##### <a name="provider-search-response"></a> Provider Search Response

##### JSON

The JSON response includes the following fields.

* hits - How many total Providers were found.
* took - How long the search took in milliseconds
* items - a list of the Providers

Request the list of all providers with their metadata.

__Example__

```
    curl -H "Cmr-Pretty: true" "%CMR-ENDPOINT%/providers"
```

__Sample response__

```
[ {
  "ProviderId" : "PROV1",
  "DescriptionOfHolding" : "sample provider, no data",
  "Organizations" : [ {
    "Roles" : [ "ORIGINATOR" ],
    "ShortName" : "PROV1",
    "URLValue" : "https://example.gov"
  } ],
  "Administrators" : [ "admin1" ],
  "MetadataSpecification" : {
    "Name" : "Provider",
    "URL" : "https://cdn.earthdata.nasa.gov/schemas/provider/v1.0.0",
    "Version" : "1.0.0"
  }
}, {
  "ProviderId" : "PROV2",
  "DescriptionOfHolding" : "sample provider, no data",
  "Organizations" : [ {
    "Roles" : [ "ORIGINATOR" ],
    "ShortName" : "PROV2",
    "URLValue" : "https://example.gov"
  } ],
  "Administrators" : [ "admin1" ],
  "MetadataSpecification" : {
    "Name" : "Provider",
    "URL" : "https://cdn.earthdata.nasa.gov/schemas/provider/v1.0.0",
    "Version" : "1.0.0"
  }
}, {
  "ProviderId" : "PROV3",
  "DescriptionOfHolding" : "sample provider, no data",
  "Organizations" : [ {
    "Roles" : [ "ORIGINATOR" ],
    "ShortName" : "PROV3",
    "URLValue" : "https://example.gov"
  } ],
  "Administrators" : [ "admin1" ],
  "MetadataSpecification" : {
    "Name" : "Provider",
    "URL" : "https://cdn.earthdata.nasa.gov/schemas/provider/v1.0.0",
    "Version" : "1.0.0"
  }
}]
```
Request metadata for a specific provider.

__Example__

```
    curl -H "Cmr-Pretty: true" "%CMR-ENDPOINT%/providers/<provider-id>"
```

__Sample response__

{
    "ProviderId": "PROV1",
    "DescriptionOfHolding": "sample provider, no data",
    "Organizations": [
        {
            "Roles": [
                "ORIGINATOR"
            ],
            "ShortName": "PROV1",
            "URLValue": "https://example.gov"
        }
    ],
    "Administrators": [
        "admin1"
    ],
    "MetadataSpecification": {
        "Name": "Provider",
        "URL": "https://cdn.earthdata.nasa.gov/schemas/provider/v1.0.0",
        "Version": "1.0.0"
    }
}

