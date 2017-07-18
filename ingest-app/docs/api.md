## API Documentation

See the [CMR Data Partner User Guide](https://wiki.earthdata.nasa.gov/display/CMR/CMR+Data+Partner+User+Guide) for a general guide to utilizing the CMR Ingest API as a data partner.
See the [CMR Client Partner User Guide](https://wiki.earthdata.nasa.gov/display/CMR/CMR+Client+Partner+User+Guide) for a general guide to developing a CMR client.
Join the [CMR Client Developer Forum](https://wiki.earthdata.nasa.gov/display/CMR/CMR+Client+Developer+Forum) to ask questions, make suggestions and discuss topics like future CMR capabilities.

### Metadata Ingest API Overview

  * /providers/\<provider-id>/validate/collection/\<native-id>
    * [POST - Validate collection metadata.](#validate-collection)
  * /providers/\<provider-id>/collections/\<native-id>
    * [PUT - Create or update a collection.](#create-update-collection)
    * [DELETE - Delete a collection.](#delete-collection)
  * /providers/\<provider-id>/validate/granule/\<native-id>
    * [POST - Validate granule metadata.](#validate-granule)
  * /providers/\<provider-id>/granules/\<native-id>
    * [PUT - Create or update a granule.](#create-update-granule)
    * [DELETE - Delete a granule.](#delete-granule)
  * /providers/\<provider-id>/variables/\<native-id>
    * [PUT - Create or update a variable.](#create-update-variable)
    * [DELETE - Delete a variable.](#delete-variable)
  * /translate/collection
    * [POST - Translate collection metadata.](#translate-collection)

***

## <a name="api-conventions"></a> API Conventions

This defines conventions used across the Ingest API.

### <a name="headers"></a> Headers

This defines common headers on the ingest API.

#### <a name="content-type-header"></a> Content-Type Header

Content-Type is a standard HTTP header that specifies the content type of the body of the request. Ingest supports the following content types for ingesting metadata.

|       Content-Type       |    Description    |    Concept Types    |
| ------------------------ | ----------------- | ------------------- |
| application/dif10+xml    | DIF 10            | collection          |
| application/dif+xml      | DIF 9             | collection          |
| application/echo10+xml   | ECHO 10           | collection, granule |
| application/iso19115+xml | ISO 19115 (MENDS) | collection          |
| application/iso:smap+xml | ISO 19115 SMAP    | collection, granule |
| application/vnd.nasa.cmr.umm+json| UMM JSON  | collection          |

Note: UMM JSON accepts an additional version parameter for both Content-Type: and Accept: headers. Like charset, it is appended with a semicolon (;). If no version is appended, the latest version is assumed.

```
application/vnd.nasa.cmr.umm+json;version=1.1
```

#### <a name="echo-token-header"></a> Echo-Token Header

All Ingest API operations require specifying a token obtained from URS or ECHO. The token should be specified using the `Echo-Token` header.

#### <a name="accept-header"></a> Accept Header

The Accept header specifies the format of the response message. The Accept header will default to XML for the normal Ingest APIs. `application/json` can be specified if you prefer responses in JSON.

#### <a name="cmr-revision-id-header"></a> Cmr-Revision-Id Header

The revision id header allows specifying the [revision id](#revision-id) to use when saving the concept. If the revision id specified is not the latest a HTTP Status code of 409 will be returned indicating a conflict.

#### <a name="cmr-concept-id-header"></a> Cmr-Concept-Id (or Concept-Id) Header

The concept id header allows specifying the [concept id](#concept-id) to use when saving a concept. This should normally not be sent by clients. The CMR should normally generate the concept id. The header Concept-Id is an alias for Cmr-Concept-Id.

#### <a name="validate-keywords-header"></a> Cmr-Validate-Keywords Header

If this header is set to true then ingest will validate that collection keywords match [known keywords from GCMD KMS](http://gcmd.nasa.gov/learn/keyword_list.html). The following fields are validated.

* Platforms - short name and long name
* Instruments - short name and long name
* Projects - short name and long name
* Science Keywords - category, topic, term, variable level 1, variable level 2, variable level 3.

Note that when multiple fields are present the combination of keywords are validated to match a known combination.

#### <a name="validate-umm-c-header"></a> Cmr-Validate-Umm-C Header

If this header is set to true, collection metadata is validated against the UMM-C JSON schema. It also uses the UMM-C Specification for parsing the metadata and checking business rules. This is temporary header for testing. Eventually the CMR will enforce this validation by default.

#### <a name="skip-sanitize-umm-c-header"></a> Cmr-Skip-Sanitize-Umm-C Header

If this header is set to true, translation to UMM JSON will not add default values to the converted UMM when the required fields are missing. This may cause umm schema validation failure if skip-umm-validation is not set to true. This header can not be set to true when translating to formats other than UMM JSON.

#### <a name="user-id"></a> User-Id Header

The user id header allows specifying the user-id to use when saving or deleting a collection concept. This header is currently ignored for granule concepts. If user-id header is not specified, user id is retrieved using the token supplied during the ingest.

***

### <a name="responses"></a> Responses

### <a name="response-headers"></a> Response Headers

#### <a name="CMR-Request-Id-header"></a> cmr-request-id

This header returns the unique id assigned to the request. This can be used to help debug client errors. The value is a long string of the form

    828ef0b8-a876-4579-85db-3cc9d1b5f6e5

#### <a name="http-status-codes"></a> HTTP Status Codes

| Status Code |                                               Description                                                                          |
| ----------- | -----------------------------------------------------------------------------------------------------------------------------------|
|         200 | Successful update/delete                                                                                                           |
|         201 | Successful create                                                                                                                  |
|         400 | Bad request. The body will contain errors.                                                                                         |
|         404 | Not found. This could be returned either because the URL isn't known by ingest or the item wasn't found.                           |
|         409 | Conflict. This is returned when a revision id conflict occurred while saving the item.                                             |
|         415 | Unsupported Media Type. The body will return an error message that contains the list of supported ingest formats.                  |
|         422 | Unprocessable entity. Ingest understood the request, but the concept failed ingest validation rules. The body will contain errors. |
|         500 | Internal error. Contact CMR Operations if this occurs.                                                                             |
|         503 | Internal error because a service dependency is not available.                                                                      |

#### <a name="successful-responses"></a> Successful Responses

Successful ingest responses will return an HTTP Status code of 201 for create and 200 for update/delete, and a body containing the [CMR Concept Id](#concept-id) of the item that was created, updated or deleted along with the [revision id](#revision-id).

UMM-C schema validation errors are returned as warnings in the response by default. When Cmr-Validate-Umm-C request header is set to true, the ingest request will fail when there are any UMM-C validation errors.

    {"concept-id":"C12345-PROV","revision-id":1,"warnings":"object has missing required properties ([\"ProcessingLevel\"])"}

#### <a name="error-response"></a> Error Responses

Requests could fail for several reasons when communicating with the CMR as described in the [HTTP Status Codes](#http-status-codes).

##### <a name="general-errors"></a> General Errors

Ingest validation errors can take one of two shapes. General error messages will be returned as a list of error messages like the following:

```
<errors>
   <error>Parent collection for granule [SC:AE_5DSno.002:30500511] does not exist.</error>
</errors>
```

##### <a name="umm-ialidation-errors"></a> UMM Validation Errors

UMM Validation errors will be returned with a path within the metadata to the failed item. For example the following errors would be returned if the first and second spatial areas were invalid. The path is a set of UMM fields in camel case separated by a `/`. Numeric indices are used to indicate the index of an item within a list that failed.

```
<errors>
   <error>
      <path>SpatialCoverage/Geometries/1</path>
      <errors>
         <error>Spatial validation error: The shape contained duplicate points. Points 2 [lon=180 lat=-90] and 3 [lon=180 lat=-90] were considered equivalent or very close.</error>
      </errors>
   </error>
   <error>
      <path>SpatialCoverage/Geometries/0</path>
      <errors>
         <error>Spatial validation error: The polygon boundary points are listed in the wrong order (clockwise vs counter clockwise). Please see the API documentation for the correct order.</error>
      </errors>
   </error>
</errors>
```

Error messages can also be returned in JSON by setting the Accept header to application/json.

```
{
  "errors" : [ {
    "path" : [ "Platforms", 1, "Instruments", 1, "Composed Of" ],
    "errors" : [ "Composed Of must be unique. This contains duplicates named [S2]." ]
  }, {
    "path" : [ "Platforms", 1, "Instruments", 0, "Composed Of" ],
    "errors" : [ "Composed Of must be unique. This contains duplicates named [S1]." ]
  }, {
    "path" : [ "Platforms", 1, "Instruments" ],
    "errors" : [ "Instruments must be unique. This contains duplicates named [I1]." ]
  }, {
    "path" : [ "Platforms" ],
    "errors" : [ "Platforms must be unique. This contains duplicates named [P1]." ]
  } ]
}
```

***


### <a name="cmr-ids"></a> CMR Ids

This documents different identifiers used in the CMR.

#### <a name="provider-id"></a> Provider Id

A provider id identifies a provider and is composed of a combination of upper case letters, digits, and underscores. Example: LPDAAC_ECS

#### <a name="native-id"></a> Native Id

The native id is the id that a provider client uses to refer to a granule or collection in the URL. For example a provider could create a new collection with native id "cloud_sat_5" in provider "PROV" by sending a HTTP PUT request to `/providers/PROV/collections/cloud_sat_5`. The native id must be unique within a provider. Two collections could not share a native id for example. The native id doesn't have to matche an id in the metadata but providers are encouraged to use something like entry id or entry title for their native ids.

#### <a name="revision-id"></a> CMR Revision Id

Every update or deletion of a concept is stored separately as a separate revision in the CMR database. Deletion revisions are called tombstones. The CMR uses this to improve caching, synchronization, and to maintain an audit log of changes to concepts. Every revision is given a separate id starting with 1 for the first revision.

##### Example CMR Revision Ids

Here's a table showing an example set of revisions for one collection.

| Concept Id | CMR Revision Id | Metadata | Deleted |
| ---------- | --------------- | -------- | ------- |
| C1-PROV1   |        1        | ...      | false   |
| C1-PROV1   |        2        | ...      | false   |
| C1-PROV1   |        3        | null     | true    |
| C1-PROV1   |        4        | ...      | false   |

The table shows one collection with 4 revisions. It was created and then updated. The third revision was a deletion. The last revision was when the collection was recreated.

#### <a name="concept-id"></a> CMR Concept Id

A concept is any type of metadata that is managed by the CMR. Collections and granules are the current concept types the CMR manages. The concept id is the unique identifier of concepts in the CMR.

The format of the concept id is:

    <letter> <unique-number> "-" <provider-id>

An example concept id is C179460405-LPDAAC_ECS. The letter identifies the concept type. G is for granule. C is for collection. The [provider id](#provider-id) is the upper case unique identifier for a provider.

***

## <a name="metadata-ingest"></a> Metadata Ingest

### <a name="validate-collection"></a> Validate Collection

Collection metadata can be validated without having to ingest it. The validation performed is schema validation, UMM validation, and inventory specific validations. Keyword validation can be enabled with the [keyword validation header](#validate-keywords-header). It returns status code 200 with a list of any warnings on successful validation, status code 400 with a list of validation errors on failed validation. Warnings would be returned if the ingested record passes native XML schema validation, but not UMM-C validation.

```
curl -i -XPOST -H "Content-type: application/echo10+xml" -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1/validate/collection/sampleNativeId15 -d \
"<Collection>
  <ShortName>ShortName_Larc</ShortName>
  <VersionId>Version01</VersionId>
  <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
  <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
  <DeleteTime>2015-05-23T22:30:59</DeleteTime>
  <LongName>LarcLongName</LongName>
  <DataSetId>LarcDatasetId</DataSetId>
  <Description>A minimal valid collection</Description>
  <Orderable>true</Orderable>
  <Visible>true</Visible>
</Collection>"
```


### <a name="create-update-collection"></a> Create / Update a Collection

Collection metadata can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/providers/<provider-id>/collections/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id). The metadata that is uploaded is validated for XML well-formedness, XML schema validation, and against UMM validation rules. Keyword validation can be enabled with the [keyword validation header](#validate-keywords-header). If there is a need to retrieve the native-id of an already-ingested collection for updating, requesting the collection via the search API in UMM-JSON format will provide the native-id.

```
curl -i -XPUT -H "Content-type: application/echo10+xml" -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1/collections/sampleNativeId15 -d \
"<Collection>
  <ShortName>ShortName_Larc</ShortName>
  <VersionId>Version01</VersionId>
  <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
  <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
  <DeleteTime>2015-05-23T22:30:59</DeleteTime>
  <LongName>LarcLongName</LongName>
  <DataSetId>LarcDatasetId</DataSetId>
  <Description>A minimal valid collection</Description>
  <Orderable>true</Orderable>
  <Visible>true</Visible>
</Collection>"
```

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>C1200000000-PROV1</concept-id>
  <revision-id>1</revision-id>
</result>
```

#### Successful Response in JSON

```
{"concept-id":"C1200000000-PROV1","revision-id":1}
```

### <a name="delete-collection"></a> Delete a Collection

Collection metadata can be deleted by sending an HTTP DELETE the URL `%CMR-ENDPOINT%/providers/<provider-id>/collections/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

    curl -i -XDELETE -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1/collections/sampleNativeId15

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>C1200000000-PROV1</concept-id>
  <revision-id>2</revision-id>
</result>
```

#### Successful Response in JSON

```
{"concept-id":"C1200000000-PROV1","revision-id":2}
```

***

### <a name="validate-granule"></a> Validate Granule

Granule metadata can be validated without having to ingest it. The validation performed is schema validation, UMM validation, and inventory specific validations. It returns status code 200 on successful validation, status code 400 with a list of validation errors on failed validation.

A collection is required when validating the granule. The granule being validated can either refer to an existing collection in the CMR or the collection can be sent in a multi-part HTTP request.

#### Validate Granule Referencing Existing Collection

This shows how to validate a granule that references an existing collection in the database.

```
curl -i -XPOST -H "Content-type: application/echo10+xml" -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1/validate/granule/sampleGranuleNativeId33 -d \
"<Granule>
   <GranuleUR>SC:AE_5DSno.002:30500511</GranuleUR>
   <InsertTime>2009-05-11T20:09:16.340Z</InsertTime>
   <LastUpdate>2014-03-19T09:59:12.207Z</LastUpdate>
   <Collection>
     <DataSetId>LarcDatasetId</DataSetId>
   </Collection>
   <Orderable>true</Orderable>
</Granule>"
```

#### Validate Granule With Parent Collection

Granule validation also allows the parent collection to be sent along with the granule as well. This allows validation of a granule that may not have a parent collection ingested. The granule and collection XML are sent over HTTP using form multi-part parameters. The collection and granule XML are specified with the parameter names "collection" and "granule".

Here's an example of validating a granule along with the parent collection using curl. The granule is in the granule.xml file and collection is in collection.xml.

    curl -i -XPOST -H "Echo-Token: XXXX" \
    -F "granule=<granule.xml;type=application/echo10+xml" \
    -F "collection=<collection.xml;type=application/echo10+xml" \
    "%CMR-ENDPOINT%/providers/PROV1/validate/granule/sampleGranuleNativeId33"

### <a name="create-update-granule"></a> Create / Update a Granule

Granule metadata can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/providers/<provider-id>/granules/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id). Once a granule is created to reference a parent collection, the granule cannot be changed to reference a different collection as its parent collection during granule update.

    curl -i -XPUT -H "Content-type: application/echo10+xml" -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1/granules/sampleGranuleNativeId33 -d \
    "<Granule>
       <GranuleUR>SC:AE_5DSno.002:30500511</GranuleUR>
       <InsertTime>2009-05-11T20:09:16.340Z</InsertTime>
       <LastUpdate>2014-03-19T09:59:12.207Z</LastUpdate>
       <Collection>
         <DataSetId>LarcDatasetId</DataSetId>
       </Collection>
       <Orderable>true</Orderable>
    </Granule>"

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>G1200000001-PROV1</concept-id>
  <revision-id>1</revision-id>
</result>
```

#### Successful Response in JSON

```
{"concept-id":"G1200000001-PROV1","revision-id":1}
```
### <a name="delete-granule"></a> Delete a Granule

Granule metadata can be deleted by sending an HTTP DELETE the URL `%CMR-ENDPOINT%/providers/<provider-id>/granules/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

    curl -i -XDELETE -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1/granules/sampleGranuleNativeId33

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>G1200000001-PROV1</concept-id>
  <revision-id>2</revision-id>
</result>
```

#### Successful Response in JSON

```
{"concept-id":"G1200000001-PROV1","revision-id":2}
```


### <a name="create-update-variable"></a> Create / Update a Variable

Variable metadata can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/providers/<provider-id>/variables/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id).

```
curl -i -XPUT \
-H "Content-type: application/vnd.nasa.cmr.umm+json" \
-H "Echo-Token: XXXX" \
%CMR-ENDPOINT%/providers/PROV1/variables/sampleVariableNativeId33 -d \
"{\"ValidRange\":{},
  \"Dimensions\":\"11\",
  \"Scale\":\"1.0\",
  \"Offset\":\"0.0\",
  \"FillValue\":\"-9999.0\",
  \"Units\":\"m\",
  \"ScienceKeywords\":[{\"Category\":\"sk-A\",
                        \"Topic\":\"sk-B\",
                        \"Term\":\"sk-C\"}],
  \"Name\":\"A-name\",
  \"VariableType\":\"SCIENCE_VARIABLE\",
  \"LongName\":\"A long UMM-Var name\",
  \"DimensionsName\":\"H2OFunc\",
  \"DataType\":\"float32\"}"
```

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>V1200000012-PROV1</concept-id>
  <revision-id>1</revision-id>
</result>
```

#### Successful Response in JSON

By passing the option `-H "Accept: application/json"` to `curl`, one may
get a JSON response:

```
{"concept-id":"V1200000012-PROV1","revision-id":1}
```

### <a name="delete-variable"></a> Delete a Variable

Variable metadata can be deleted by sending an HTTP DELETE the URL `%CMR-ENDPOINT%/providers/<provider-id>/variables/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

```
curl -i -X DELETE \
-H "Echo-Token: XXXX" \
%CMR-ENDPOINT%/providers/PROV1/variables/sampleVariableNativeId33
```

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>V1200000012-PROV1</concept-id>
  <revision-id>2</revision-id>
</result>
```

#### Successful Response in JSON

```
{"concept-id":"V1200000012-PROV1","revision-id":2}
```


## <a name="translate-collection"></a> Translate Collection Metadata

Collection metadata can be translated between metadata standards using the translate API in Ingest. This API also supports the UMM JSON format which represents UMM as JSON. The request specifies the metadata standard being sent using the Content-Type header. Metadata is sent inside the body of the request. The output format is specified via the Accept header.

To disable validation of the parsed UMM metadata against the UMM spec, pass `skip_umm_validation=true` as a query parameter.

Example: Translate ECHO10 metadata to UMM JSON

```
curl -i -XPOST -H "Content-Type: application/echo10+xml" -H "Accept:  application/vnd.nasa.cmr.umm+json;version=1.2" %CMR-ENDPOINT%/translate/collection?skip_umm_validation=true -d \
"<Collection>
  <ShortName>ShortName_Larc</ShortName>
  <VersionId>Version01</VersionId>
  <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
  <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
  <DeleteTime>2015-05-23T22:30:59</DeleteTime>
  <LongName>LarcLongName</LongName>
  <DataSetId>LarcDatasetId</DataSetId>
  <Description>A minimal valid collection</Description>
  <Orderable>true</Orderable>
  <Visible>true</Visible>
</Collection>"
```

Example output:

```
{
  "Abstract" : "A minimal valid collection",
  "EntryId" : {
    "Id" : "ShortName_Larc_Version01"
  },
  "EntryTitle" : "LarcDatasetId"
}
```

Example: Translate ECHO10 metadata to ISO19115-2

```
curl -i -XPOST -H "Content-Type: application/echo10+xml" -H "Accept: application/iso19115+xml" %CMR-ENDPOINT%/translate/collection -d \
"<Collection>
  <ShortName>ShortName_Larc</ShortName>
  <VersionId>Version01</VersionId>
  <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
  <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
  <DeleteTime>2015-05-23T22:30:59</DeleteTime>
  <LongName>LarcLongName</LongName>
  <DataSetId>LarcDatasetId</DataSetId>
  <Description>A minimal valid collection</Description>
  <Orderable>true</Orderable>
  <Visible>true</Visible>
</Collection>"
```

Example output:

```
<?xml version="1.0" encoding="UTF-8"?>
<gmi:MI_Metadata xmlns:eos="http://earthdata.nasa.gov/schema/eos"
    xmlns:gco="http://www.isotc211.org/2005/gco"
    xmlns:gmd="http://www.isotc211.org/2005/gmd"
    xmlns:gmi="http://www.isotc211.org/2005/gmi"
    xmlns:gml="http://www.opengis.net/gml/3.2"
    xmlns:gmx="http://www.isotc211.org/2005/gmx"
    xmlns:gsr="http://www.isotc211.org/2005/gsr"
    xmlns:gss="http://www.isotc211.org/2005/gss"
    xmlns:gts="http://www.isotc211.org/2005/gts"
    xmlns:srv="http://www.isotc211.org/2005/srv"
    xmlns:swe="http://schemas.opengis.net/sweCommon/2.0/"
    xmlns:xlink="http://www.w3.org/1999/xlink"
    xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <gmd:identificationInfo>
        <gmd:MD_DataIdentification>
            <gmd:citation>
                <gmd:CI_Citation>
                    <gmd:title>
                        <gco:CharacterString>LarcDatasetId</gco:CharacterString>
                    </gmd:title>
                    <gmd:identifier>
                        <gmd:MD_Identifier>
                            <gmd:code>
                                <gco:CharacterString>ShortName_Larc_Version01</gco:CharacterString>
                            </gmd:code>
                        </gmd:MD_Identifier>
                    </gmd:identifier>
                </gmd:CI_Citation>
            </gmd:citation>
        </gmd:MD_DataIdentification>
    </gmd:identificationInfo>
</gmi:MI_Metadata>
```
## <a name="bulk-update"></a> Collection Bulk Update

The bulk update API is used perform the same collection update to multiple concepts in one call. Bulk update is currently for collections only, granules are not supported.

Bulk update is initiated through an ingest POST endpoint with the concept ids to update, the update type, the update field, and update information. The metadata is converted to the latest version of UMM, if not the native format, updated according to the parameters, and saved as the latest version of UMM-JSON, **making the native format of the collection now UMM-JSON**. Previous revisions of the collection are retained in the original native format. In the UMM-JSON metadata, the Metadata Date of type "UPDATE" will be set to the current date. Please note that when we apply bulk update on a collection, regardless if there are actual changes, a new revision is created.

Updated collections are validated through ingest validation and the updates will not be saved if the updated collection is not valid UMM or fails business rule validation. The error will be recorded in the individual collection status, which can be queried via the status endpoint. Collection validation warnings will not prevent saving the updated collection and the warnings will be recorded in the individual collection status.

Bulk update currently supports updating the following fields:

  * Science Keywords
  * Location Keywords
  * Data Centers
  * Instruments
  * Platforms

Instruments are nested within platforms so instrument updates are applied to all platforms in the collection.

The following update types are supported:

  * Add to existing - the update value is added to the existing list. An update value is required.
  * Clear all and replace - clear the list and replace with the update value.
  * Find and replace - replace any instance in the list that matches the find value with the update value.
  * Find and update - merge update value into any instance in the list that matches the find value.
  * Find and remove - remove any instance from the list that matches the find value.

Update types that include a find will match on the fields supplied. For example, for a science keyword update with a find value of {"Category": "EARTH SCIENCES"}, any science keyword with a category of "EARTH SCIENCES" will be considered a match regardless of the values of the science keyword topic, term, etc.

When a value is replaced by bulk update, only the field(s) specified in the update value will be replaced with the rest of the original value retained. For example, with a platform update value of {"ShortName": "A340-600"}, only the short name will be updated during a find and replace, while the long name, instruments, and other fields retain their values.

If multiple bulk updates are run at the same time with the same concept-ids, there is no guarantee of the order that the updates will be performed on a collection. For example, if a clear all and replace is initiated, then an add to existing on the same collection, the clear all and replace could happen after the add to existing. Because of this, it is best to not run bulk update operations in parallel on overlapping collections.

### Initiate Bulk Update

Bulk update can be initiated by sending an HTTP POST request to `%CMR-ENDPOINT%/providers/<provider-id>/bulk-update/collections`

The POST request takes the following parameters:

  * Concept-ids (required) - a list of concept ids to update
  * Update type (required) - choose from the enumeration: ADD_TO_EXISTING, CLEAR_ALL_AND_REPLACE, FIND_AND_REPLACE, FIND_AND_REMOVE, FIND_AND_UPDATE
  * Update field (required) - choose from the enumeration: SCIENCE_KEYWORDS, LOCATION_KEYWORDS, DATA_CENTERS, PLATFORMS, INSTRUMENTS
  * Update value (required when update type is ADD_TO_EXISTING, CLEAR_ALL_AND_REPLACE, FIND_AND_REPLACE, FIND_AND_UPDATE) - UMM-JSON representation of the update to make
  * Find value (required when update type is FIND_AND_REPLACE, FIND_AND_UPDATE or  FIND_AND_REMOVE) - UMM-JSON representation of the data to find

The return value includes a status code indicating that the bulk update was successfully initiated, any errors if not successful, and on success a task-id that can be used for querying the bulk update status. The bulk update will be run asynchronously and the status of the overall bulk update task as well as the status of individual collection updates can be queried using the task id.

Example: Initiate a bulk update of 3 collections. Find science keywords that have category "EARTH SCIENCE", Topic "HUMAN DIMENSIONS", Term "ENVIRONMENTAL IMPACTS", and "HEAVY METALS" and replace Variable Level 1 of those science keywords with "HEAVY METALS CONCENTRATION"

```
curl -i -XPOST -H "Cmr-Pretty:true" -H "Content-Type: application/json" -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1/bulk-update/collections -d
'{"concept-ids": ["C1200000005-PROV1","C1200000006-PROV1","C1200000007-PROV1"],
  "update-type": "FIND_AND_REMOVE",
  "update-field": "SCIENCE_KEYWORDS",
  "find-value": {
      "Category": "EARTH SCIENCE",
      "Topic": "HUMAN DIMENSIONS",
      "Term": "ENVIRONMENTAL IMPACTS",
      "VariableLevel1": "HEAVY METALS"},
  "update-value": {
      "VariableLevel1": "HEAVY METALS CONCENTRATION"}}'

<?xml version="1.0" encoding="UTF-8"?
<result>
    <status200</status>
    <task-id4</task-id>
</result>
```


### Query Bulk Update Status

The task ids and status of all bulk update tasks for a provider can be queried by sending an HTTP GET request to `%CMR-ENDPOINT%/providers/<provider-id>/bulk-update/collections/status`

This returns a list of: task id, status (IN_PROGRESS or COMPLETE), a status message, and the original request JSON body.

Example
```
curl -i -H "Echo-Token: XXXX" -H "Cmr-Pretty:true" https:// %CMR-ENDPOINT%/providers/PROV1/bulk-update/collections/status

<?xml version="1.0" encoding="UTF-8"?>
<result>
    <tasks>
        <task>
            <task-id>21</task-id>
            <status>COMPLETE</status>
            <status-message>Task completed with 1 collection update failures out of 5</status-message>
            <request-json-body>{"concept-ids": ["C12807-PROV1","C17995-PROV1","C18002-PROV1","C18016-PROV1"],"update-type": "FIND_AND_REMOVE","update-field": "SCIENCE_KEYWORDS","find-value": {"Category": "EARTH SCIENCE","Topic": "HUMAN DIMENSIONS","Term": "ENVIRONMENTAL IMPACTS","VariableLevel1": "HEAVY METALS CONCENTRATION"}}</request-json-body>
        </task>
        <task>
            <task-id>22</task-id>
            <status>COMPLETE</status>
            <status-message>Task completed with 1 collection update failures out of 3</status-message>
            <request-json-body>{"concept-ids": ["C13239-PROV1","C13276-PROV1","C13883-PROV1","C13286-PROV1"],"update-type": "CLEAR_ALL_AND_REPLACE","update-field": "SCIENCE_KEYWORDS","update-value": {"Category": "EARTH SCIENCE","Topic": "HUMAN DIMENSIONS","Term": "ENVIRONMENTAL IMPACTS","VariableLevel1": "HEAVY METALS CONCENTRATION"}}</request-json-body>
        </task>
        <task>
            <task-id>2</task-id>
            <status>COMPLETE</status>
            <status-message>All collection updates completed successfully.</status-message>
            <request-json-body>{"concept-ids": ["C12130-PROV1"],"update-type": "ADD_TO_EXISTING", "update-field": "SCIENCE_KEYWORDS","update-value": {"Category": "EARTH SCIENCE","Topic": "HUMAN DIMENSIONS","Term": "ENVIRONMENTAL IMPACTS","VariableLevel1": "HEAVY METALS CONCENTRATION"}}</request-json-body>
        </task>
    </tasks>
</result>
```

A more detailed status for an individual task can be queried by sending an HTTP GET request to `%CMR-ENDPOINT%/providers/<provider-id>/bulk-update/collections/status/<task-id>`

This returns the status of the bulk update task including the overall task status (IN_PROGRESS or COMPLETE), an overall task status message, the original request JSON body, and the status of each collection updated. The collection status includes the concept-id, the collection update status (PENDING, COMPLETE, FAILED), and a status message. FAILED indicates an error occurred either updating the collection or during collection validation. The error will be reported in the collection status message. If collection validation results in warnings, the warnings will be reported in the status message.

Example: Collection statuses with 1 failure and 1 warnings
```
curl -i -H "Echo-Token: XXXX" -H "Cmr-Pretty:true" %CMR-ENDPOINT%/providers/PROV1/bulk-update/collections/status/25

<?xml version="1.0" encoding="UTF-8"?>
<result>
    <task-status>COMPLETE</task-status>
    <status-message>Task completed with 1 collection update failures out of 5</status-message>
    <request-json-body>{"concept-ids": ["C11984-PROV1","C11991-PROV1","C119916-PROV1","C14432-PROV1","C20000-PROV1"],"update-type": "FIND_AND_REMOVE","update-field": "SCIENCE_KEYWORDS","find-value": {"Category": "EARTH SCIENCE","Topic": "HUMAN DIMENSIONS","Term": "ENVIRONMENTAL IMPACTS","VariableLevel1": "HEAVY METALS CONCENTRATION"}}</request-json-body>
    <collection-statuses>
        <collection-status>
            <concept-id>C11984-PROV1</concept-id>
            <status>COMPLETE</status>
        </collection-status>
        <collection-status>
            <concept-id>C11991-PROV1</concept-id>
            <status>COMPLETE</status>
        </collection-status>
        <collection-status>
            <concept-id>C119916-PROV1</concept-id>
            <status>COMPLETE</status>
        </collection-status>
        <collection-status>
            <concept-id>C14432-PROV1</concept-id>
            <status>FAILED</status>
            <status-message>/PublicationReferences/2 object instance has properties which are not allowed by the schema: ["_errors"]</status-message>
        </collection-status>
        <collection-status>
            <concept-id>C20000-PROV1</concept-id>
            <status>COMPLETE</status>
            <status-message>Collection was updated successfully, but translating the collection to UMM-C had the following issues: [:RelatedUrls 4 :URL] [http://gcmd.nasa.gov/r/d/[NOAA-NGDC]gov.noaa.ngdc.mgg.photos.G01372] is not a valid URL</status-message>
        </collection-status>
    </collection-statuses>
</result>
```
Bulk update status and results are available for 90 days.
