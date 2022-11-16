## <a name="data-quality-summary"></a> Data Quality Summary

#### <a name="provider-info-data-quality-summary"></a> /providers/&lt;provider-id&gt;/data-quality-summaries/&lt;native-id&gt;

### <a name="create-update-data-quality-summary"></a> Create / Update a Data Quality Summary

Data Quality Summary concepts can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/providers/<provider-id>/data-quality-summaries/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id). The contents of the metadata is passed in the body of the request.

```
    curl -XPOST \
        -H "Cmr-Pretty: true" \
        -H "Content-Type:application/vnd.nasa.cmr.umm+json" \
        -H "Authorization: Bearer XXXX" \
        "%CMR-ENDPOINT%/providers/PROV1/data-quality-summaries/sampleNativeId" \
        -d @sampleDataqualitysummary.json
```

#### Successful Response in XML

```
    <?xml version="1.0" encoding="UTF-8"?>
    <result>
        <concept-id>DQ1200000000-PROV1</concept-id>
        <revision-id>1</revision-id>
        <warnings></warnings>
        <existing-errors></existing-errors>
    </result>
```
Subsequent ingests to the Data Quality Summary record will result in updates to it's metadata as well as increment the revision-id of the record.

#### Successful Response in JSON

By passing the option `-H "Accept: application/json"` to `curl`, one may
get a JSON response:

```
    {"concept-id":"DQ1200000000-PROV1","revision-id":1,"warnings":null,"existing-errors":null}
```

### <a name="delete-data-quality-summary"></a> Delete a Data Quality Summary

Data Quality Summary metadata can be deleted by sending an HTTP DELETE to the URL `%CMR-ENDPOINT%/providers/<provider-id>/data-quality-summaries/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

```
    curl -XDELETE \
        -H "Cmr-Pretty: true" \
        -H "Authorization: Bearer XXXX" \
        %CMR-ENDPOINT%/providers/PROV1/data-quality-summaries/sampleNative23Id"
```

#### Successful Response in XML

```
    <?xml version="1.0" encoding="UTF-8"?>
    <result>
        <concept-id>DQ1200000000-PROV1</concept-id>
        <revision-id>2</revision-id>
    </result>
```

#### Successful Response in JSON

```
    {"concept-id":"DQ1200000000-PROV1","revision-id":2,"warnings":null,"existing-errors":null}
```

Attempting to delete an already deleted record will return
the following error message

#### Unsuccessful Response in XML

```
    <?xml version="1.0" encoding="UTF-8"?>
    <errors>
        <error>Concept with native-id [sampleNative23Id] and concept-id [DQ1200000000-PROV1] is already deleted.</error>
    </errors>
```

#### Unsuccessful Response in JSON

```
    "errors": [
            "Concept with native-id [sampleNative23Id] and concept-id [DQ1200000000-PROV1] is already deleted."
        ]
```
