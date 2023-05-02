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

__Example__

```
    curl -H "Cmr-Pretty: true" "%CMR-ENDPOINT%/providers"
```

__Sample response__

```
    {
      "MetadataSpecification": {
        "Name":"UMM-P",
        "Version": "1.0.0",
        "URL": "https://cdn.earthdata.nasa.gov/umm/provider/v1.0.0"},
      "ProviderId": "MATRIX_A",
      "DescriptionOfHolding": "No real data, just a bunch of tests",
      "Organizations": [
        {"ShortName": "MATRIX_A",
          "LongName": "Matrix instance A",
          "Roles": ["PUBLISHER"],
          "URLValue": "https://cmr.earthdata.nasa.gov/search"
        }],
      "ContactPersons": [{
        "Roles":["PROVIDER MANAGMENT", "TECHNICAL CONTACT"],
        "LastName": "Smith",
        "ContactInformation": {
          "HoursOfService": "8-5 East Coast time, M-F",
          "ContactInstruction": "slack works best",
          "Addresses":[{
            "StreetAddresses": ["123 Fake Street"],
            "City": "Springfield",
            "StateProvince": "DC",
            "Country": "USA",
            "PostalCode" : "90210"
          }],
          "ContactMechanisms": [
            {"Type": "Email", "Value": "agent.smith@example.gov"},
            {"Type": "Slack", "Value": "asmith"}
          ]
        }
      }],
      "ContactGroups": [{
        "Roles":["DEVELOPER"],
        "GroupName": "team-search"
      }],
      "Consortiums": ["CEOS", "FEDEO", "CWIC"]
    }
```
