### <a name="create-update-grid"></a> Create / Update a Grid

Grid concepts can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/grid/<native-id>?provider=<provider-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id). Filename is the json document containing the record to be ingested

```
curl -v -XPOST \
-H "Content-Type:application/vnd.nasa.cmr.umm+json" \
-H "Authorization: Bearer XXXX" \
"%CMR-ENDPOINT%/grid/sampleNativeId?provider=CMR_ONLY" \
-d @sampleGrid.json
```

#### Successful Response in XML
```
<?xml version="1.0" encoding="UTF-8"?><result><concept-id>GRD1200442274-PROV1</concept-id><revision-id>1</revision-id><warnings></warnings><existing-errors></existing-errors></result>%
```
Subsequet ingests to a grid with this native Id will result in updates to this grid as well as increment the revision-id
#### Successful Response in JSON

By passing the option `-H "Accept: application/json"` to `curl`, one may
get a JSON response:

  {"concept-id":"GRD1200442274-PROV1","revision-id":1,"warnings":null,"existing-errors":null}

### <a name="delete-grid"></a> Delete a Grid

Grid metadata can be deleted by sending an HTTP DELETE to the URL `%CMR-ENDPOINT%/grid/<native-id>?provider=<provider-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.


  curl -i -XDELETE \
    -H "Authorization: Bearer XXXX" \
    %CMR-ENDPOINT%/grid/sampleNative23Id?provider=PROV1"

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>GRD1200442274-PROV1</concept-id>
  <revision-id>2</revision-id>
</result>
```
#### Successful Response in JSON

  {"concept-id":"GRD1200442274-CMR_ONLY","revision-id":2,"warnings":null,"existing-errors":null}
