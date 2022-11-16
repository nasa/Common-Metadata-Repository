## <a name="service-entry"></a> Service Entry

#### <a name="provider-info-service-entry"></a> /providers/&lt;provider-id&gt;/service-entries/&lt;native-id&gt;

### <a name="create-update-service-entry"></a> Create / Update a Service Entry

Service Entry concepts can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/providers/<provider-id>/service-entries/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id). The contents of the metadata is passed in the body of the request.

```
    curl -XPOST \
        -H "Cmr-Pretty: true" \
        -H "Content-Type:application/vnd.nasa.cmr.umm+json" \
        -H "Authorization: Bearer XXXX" \
        "%CMR-ENDPOINT%/providers/PROV1/service-entries/sampleNativeId" \
        -d @sampleServiceentry.json
```

#### Successful Response in XML

```
    <?xml version="1.0" encoding="UTF-8"?>
    <result>
        <concept-id>SE1200000000-PROV1</concept-id>
        <revision-id>1</revision-id>
        <warnings></warnings>
        <existing-errors></existing-errors>
    </result>
```

Subsequent ingests to an Service Entry record will result in updates to it's metadata as well as increment the revision-id of the record.

#### Successful Response in JSON

By passing the option `-H "Accept: application/json"` to `curl`, one may
get a JSON response:

```
    {"concept-id":"SE1200000000-PROV1","revision-id":1,"warnings":null,"existing-errors":null}
```

### <a name="delete-service-entry"></a> Delete a Service Entry

Service Entry metadata can be deleted by sending an HTTP DELETE to the URL `%CMR-ENDPOINT%/providers/<provider-id>/service-entries/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

```
    curl -XDELETE \
        -H "Cmr-Pretty: true" \
        -H "Authorization: Bearer XXXX" \
        %CMR-ENDPOINT%/providers/PROV1/service-entries/sampleNative23Id"
```

#### Successful Response in XML

```
    <?xml version="1.0" encoding="UTF-8"?>
    <result>
        <concept-id>SE1200000000-PROV1</concept-id>
        <revision-id>1</revision-id>
    </result>
```

#### Successful Response in JSON

```
    {"concept-id":"SE1200000000-PROV1","revision-id":2,"warnings":null,"existing-errors":null}
```

Attempting to delete an already deleted Service Entry will return the following error message

#### Unsuccessful Response in XML

```
    <?xml version="1.0" encoding="UTF-8"?>
    <errors>
        <error>Concept with native-id [sampleNative23Id] and concept-id [SE1200000000-PROV1] is already deleted.</error>
    </errors>
```

#### Unsuccessful Response in JSON

```
    "errors": [
        "Concept with native-id [sampleNative23Id] and concept-id [SE1200000000-PROV1] is already deleted."
    ]
```
