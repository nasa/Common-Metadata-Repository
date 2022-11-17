## <a name="grid"></a> Grid

#### <a name="provider-info-grid"></a> /providers/&lt;provider-id&gt;/grids/&lt;native-id&gt;

### <a name="create-update-grid"></a> PUT - Create / Update a Grid

Grid concepts can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/providers/<provider-id>/grids/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id). The contents of the metadata is passed in the body of the request.

```
    curl -XPOST \
        -H "Cmr-Pretty: true" \
        -H "Content-Type:application/vnd.nasa.cmr.umm+json" \
        -H "Authorization: Bearer XXXX" \
        "%CMR-ENDPOINT%/providers/PROV1/grids/sampleNativeId" \
        -d @sampleGrid.json
```

#### Successful Response in XML

```
    <?xml version="1.0" encoding="UTF-8"?>
    <result>
        <concept-id>GRD1200442274-PROV1</concept-id>
        <revision-id>1</revision-id>
        <warnings></warnings>
        <existing-errors></existing-errors>
    </result>
```

Subsequent ingests to an Grid record will result in updates to it's metadata as well as increment the revision-id of the record.
#### Successful Response in JSON

By passing the option `-H "Accept: application/json"` to `curl`, one may
get a JSON response:

```
    {"concept-id":"GRD1200442274-PROV1","revision-id":1,"warnings":null,"existing-errors":null}
```

### <a name="delete-grid"></a> DELETE - Delete a Grid

Grid metadata can be deleted by sending an HTTP DELETE to the URL `%CMR-ENDPOINT%/providers/<provider-id>/grids/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

```
    curl -XDELETE \
        -H "Cmr-Pretty: true" \
        -H "Authorization: Bearer XXXX" \
        %CMR-ENDPOINT%/providers/PROV1/grids/sampleNative23Id"
```

#### Successful Response in XML

```
    <?xml version="1.0" encoding="UTF-8"?>
    <result>
      <concept-id>GRD1200442274-PROV1</concept-id>
      <revision-id>2</revision-id>
    </result>
```

#### Successful Response in JSON

```
    {"concept-id":"GRD1200442274-PROV1","revision-id":2,"warnings":null,"existing-errors":null}
```
