## <a name="orderoption"></a> Order Option

#### <a name="provider-info-orderoption"></a> /providers/&lt;provider-id&gt;/grids/&lt;native-id&gt;

### <a name="create-update-orderoption"></a> Create / Update an Order Option

Order option concepts can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/orderoption/<native-id>?provider=<provider-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id). Filename is the json document containing the record to be ingested

```
curl -v -XPOST \
-H "Content-Type:application/vnd.nasa.cmr.umm+json" \
-H "Authorization: Bearer XXXX" \
"%CMR-ENDPOINT%/orderoption/sampleNativeId?provider=PROV1" \
-d @sampleOrderoption.json
```

#### Successful Response in XML
```
<?xml version="1.0" encoding="UTF-8"?><result><concept-id>OO1200000000-PROV1</concept-id><revision-id>1</revision-id><warnings></warnings><existing-errors></existing-errors></result>%
```
Subsequet ingests to a orderoption with this native Id will result in updates to this orderoption as well as increment the revision-id

#### Successful Response in JSON

By passing the option `-H "Accept: application/json"` to `curl`, one may
get a JSON response:

  {"concept-id":"OO1200000000-PROV1","revision-id":1,"warnings":null,"existing-errors":null}

### <a name="delete-orderoption"></a> Delete a Order Option

Order option metadata can be deleted by sending an HTTP DELETE to the URL `%CMR-ENDPOINT%/orderoption/<native-id>?provider=<provider-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

  curl -i -XDELETE \
    -H "Authorization: Bearer XXXX" \
    %CMR-ENDPOINT%/orderoption/sampleNative23Id?provider=PROV1"

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>OO1200000000-PROV1</concept-id>
  <revision-id>1</revision-id>
</result>
```
#### Successful Response in JSON

  {"concept-id":"OO1200443608-PROV1","revision-id":1,"warnings":null,"existing-errors":null}

Attempting to delete an already delete an already deleted orderoption will return
the following error message
#### Unsuccessful Response in XML

<?xml version="1.0" encoding="UTF-8"?>
<errors>
    <error>Concept with native-id [sampleNative23Id] and concept-id [OO1200000000-PROV1] is already deleted.</error>
</errors>

#### Unsuccessful Response in JSON

"errors": [
        "Concept with native-id [sampleNative23Id] and concept-id [OO1200000000-PROV1] is already deleted."
    ]
