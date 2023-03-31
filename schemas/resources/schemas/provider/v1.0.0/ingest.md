## <a name="provider"></a> Provider

#### <a name="provider-info"></a> /providers/&lt;provider-id&gt;

### <a name="create-provider"></a> POST - Create a Provider

Providers can be created by sending an HTTP POST with the metadata to
the URL `%CMR-ENDPOINT%/providers`. The response will return the saved metadata.
The contents of the metadata is passed in the body of the request.

```
    curl -XPOST \
        -H "Content-Type:application/json" \
        -H "Authorization: Bearer XXXX" \
        "%CMR-ENDPOINT%/providers/" \
        -d @sampleProvider.json
```

* Subsequent ingests to an Provider will **not** result in updates to it's metadata.
* Users must have `INGEST_MANAGEMENT`

#### Successful Response in JSON

```
    {...tbd...}
```

### <a name="read-provider"></a> GET - Read all Provider

Providers can be read by sending an HTTP GET with no metadata to the URL
`%CMR-ENDPOINT%/providers`. The response will return the saved metadata for all
providers.

```
    curl \
        -H "Content-Type:application/json" \
        -H "Authorization: Bearer XXXX" \
        "%CMR-ENDPOINT%/providers"
```

#### Successful Response in JSON

```
    {...tbd...}
```

### <a name="update-provider"></a> PUT - Update a Provider

Providers can be updated by sending an HTTP PUT with the metadata to
the URL `%CMR-ENDPOINT%/providers/\<provider-id\>`. The response will return the saved metadata.
The contents of the metadata is passed in the body of the request.

```
    curl -XPUT \
        -H "Content-Type:application/json" \
        -H "Authorization: Bearer XXXX" \
        "%CMR-ENDPOINT%/providers/PROV1" \
        -d @sampleProvider.json
```

#### Successful Response in JSON

```
    {
        "provider-id":"TCHERRY_D",
        "short-name":"TCHERRY_D",
        "cmr-only":false,
        "small":false,
        "consortiums":"CEOS FEDEO CWIC",
        "metadata":
        {"MetadataSpecification":{"Name":"UMM-P","Version":"1.0.0","URL":"https://cdn.earthdata.nasa.gov/umm/provider/v1.0.0"},
            "ProviderId":"TCHERRY_D",
            "DescriptionOfHolding":"No real data, just a bunch of tests",
            "Organizations":[
                {   "ShortName":"TCHERRY_D",
                    "LongName":"Thomas Cherry Provider D",
                    "Roles":["PUBLISHER"],
                    "URLValue":"https://cmr.earthdata.nasa.gov/search"}
                ],
            "ContactPersons":[{"Roles":["PROVIDER MANAGMENT","TECHNICAL CONTACT"],"LastName":"Cherry","UserName":"tcherry","ContactInformation":{"HoursOfService":"8-5 East Cost time, M-F","ContactInstruction":"slack works best","Addresses":[{"StreetAddresses":["123 Fake Street"],"City":"Springfield","StateProvince":"DC","Country":"Merica","PostalCode":"90210"}],"ContactMechanisms":[{"Type":"Email","Value":"thomas.a.cherry@nasa.gov"},{"Type":"Slack","Value":"tcherry"}]}}],
            "ContactGroups":[{"Roles":["DEVELOPER"],"GroupName":"team-search"}],
            "Consortiums":["CEOS","FEDEO","CWIC"]
        }
    }
```

### <a name="delete-provider"></a> DELETE - Delete a Provider

Not currently allowed. Contact CMR support.

<!--
Provider metadata can be deleted by sending an HTTP DELETE to the URL
`%CMR-ENDPOINT%/providers`.

```
    curl -XDELETE \
        -H "Cmr-Pretty: true" \
        -H "Authorization: Bearer XXXX" \
        %CMR-ENDPOINT%/providers/PROV1/provider/prov-id-here"
```

#### Successful Response in JSON

```
    {...tbd...}
```

-->