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
  * /collections/\<collection-concept-id>/\<collection-revision-id>/variables/\<native-id>
    * [PUT - Create or update a variable with assoication.](#create-update-variable)
  * /providers/\<provider-id>/variables/\<native-id>
    * [PUT - Update a variable.](#create-update-variable)
    * [DELETE - Delete a variable.](#delete-variable)
  * /providers/\<provider-id>/services/\<native-id>
    * [PUT - Create or update a service.](#create-update-service)
    * [DELETE - Delete a service.](#delete-service)
  * /providers/\<provider-id>/tools/\<native-id>
    * [PUT - Create or update a tool.](#create-update-tool)
    * [DELETE - Delete a tool.](#delete-tool)
  * /providers/\<provider-id>/subscriptions
   *  [POST - Create a subscription without specifying a native-id.](#create-subscription)
  * /providers/\<provider-id>/subscriptions/\<native-id>
    * [POST - Create a subscription with a provided native-id.](#create-subscription)
    * [PUT - Create or Update a subscription.](#update-subscription)
    * [DELETE - Delete a subscription.](#delete-subscription)
    * [Subscription Access Control](#subscription-access-control)
  * /translate/collection
    * [POST - Translate collection metadata.](#translate-collection)
  * /translate/granule
    * [POST - Translate granule metadata.](#translate-granule)
  * /providers/<provider-id>/bulk-update/collections
    * [POST - Collection bulk update](#collection-bulk-update)
  * /providers/<provider-id>/bulk-update/granules
    * [POST - Granule bulk update](#granule-bulk-update)

***

## <a name="api-conventions"></a> API Conventions

This defines conventions used across the Ingest API.

### <a name="headers"></a> Headers

This defines common headers on the ingest API.

#### <a name="content-type-header"></a> Content-Type Header

Content-Type is a standard HTTP header that specifies the content type of the body of the request. Ingest supports the following content types for ingesting metadata.

|       Content-Type                |    Description    |    Concept Types    |
| --------------------------------- | ----------------- | ------------------- |
| application/dif10+xml             | DIF 10            | collection          |
| application/dif+xml               | DIF 9             | collection          |
| application/echo10+xml            | ECHO 10           | collection, granule |
| application/iso19115+xml          | ISO 19115 (MENDS) | collection          |
| application/iso:smap+xml          | ISO 19115 SMAP    | collection, granule |
| application/vnd.nasa.cmr.umm+json | UMM JSON          | collection, granule, variable, service, subscription, tool |

Note: UMM JSON accepts an additional version parameter for both `Content-Type` and `Accept` headers. Like charset, it is appended with a semicolon (;). If no version is appended, the latest version is assumed.

For an example, the following means version 1.14 of the UMM JSON format:

```
application/vnd.nasa.cmr.umm+json;version=1.14
```

Note: For all values of `Content-Type`, data sent using POST or PUT should not be URL encoded.

#### <a name="echo-token-header"></a> Echo-Token Header

All Ingest API operations require specifying a token obtained from URS or ECHO. The token should be specified using the `Echo-Token` header.

#### <a name="authorization-header"></a> Authorization Header

The token can alternatively be specified using the `Authorization: Bearer` header, and by specifying a Bearer token.

#### <a name="accept-header"></a> Accept Header

The Accept header specifies the format of the response message. The Accept header will default to XML for the normal Ingest APIs. `application/json` can be specified if you prefer responses in JSON.

#### <a name="cmr-revision-id-header"></a> Cmr-Revision-Id Header

The revision id header allows specifying the [revision id](#revision-id) to use when saving the concept. If the revision id specified is not the latest a HTTP Status code of 409 will be returned indicating a conflict.

#### <a name="cmr-concept-id-header"></a> Cmr-Concept-Id (or Concept-Id) Header

The concept id header allows specifying the [concept id](#concept-id) to use when saving a concept. This should normally not be sent by clients. The CMR should normally generate the concept id. The header Concept-Id is an alias for Cmr-Concept-Id.

#### <a name="validate-keywords-header"></a> Cmr-Validate-Keywords Header

If this header is set to true, ingest will validate that the UMM-C collection keywords match known keywords from the GCMD KMS. The following fields are validated.

* [Platforms](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/platforms?format=csv) - short name, long name, and type
* [Instruments](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/instruments?format=csv) - short name and long name
* [Projects](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/projects?format=csv) - short name and long name
* [Science Keywords](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/sciencekeywords?format=csv) - category, topic, term, variable level 1, variable level 2, variable level 3.
* [Location Keywords](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/locations?format=csv) - category, type, subregion 1, subregion 2, subregion 3.
* [Data Centers](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/providers?format=csv) - short name
* [Directory Names](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/idnnode?format=csv) - short name
* [ISO Topic Categories](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/isotopiccategory?format=csv) - iso topic category
* [Data Format](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/granuledataformat?format=csv) - Archival and Distribution File Format, and GetData Format


Note that when multiple fields are present the combination of keywords are validated to match a known combination.

#### <a name="validate-umm-c-header"></a> Cmr-Validate-Umm-C Header

If this header is set to true, collection metadata is validated against the UMM-C JSON schema. It also uses the UMM-C Specification for parsing the metadata and checking business rules. This is temporary header for testing. Eventually the CMR will enforce this validation by default.

#### <a name="skip-sanitize-umm-c-header"></a> Cmr-Skip-Sanitize-Umm-C Header

If this header is set to true, translation to UMM JSON will not add default values to the converted UMM when the required fields are missing. This may cause umm schema validation failure if skip-umm-validation is not set to true. This header can not be set to true when translating to formats other than UMM JSON.

#### <a name="user-id"></a> User-Id Header

The user id header allows specifying the user-id to use when saving or deleting a collection concept. This header is currently ignored for granule concepts. If user-id header is not specified, user id is retrieved using the token supplied during the ingest.

#### <a name="x-request-id"></a> X-Request-Id Header

This provides standard X-Request-Id support to allow user to pass in some random ID which will be logged on the server side for debugging purpose.

#### <a name="cmr-request-id"></a> CMR-Request-Id Header

This header serves the same purpose as X-Request-Id header. It's kept to support legacy systems.

***

### <a name="responses"></a> Responses

### <a name="response-headers"></a> Response Headers

#### <a name="CMR-Request-Id-header"></a> cmr-request-id

This header returns the value passed in through CMR-Request-Id request header or X-Request-Id request header or a unique id generated for the client request when no value is passed in, This can be used to help debug client errors. The generated value is a long string of the form

    828ef0b8-a876-4579-85db-3cc9d1b5f6e5

#### <a name="X-Request-Id-header"></a> x-request-id

This header returns the value passed in through CMR-Request-Id or X-Request-Id request header or a unique id generated for the client request when no value is passed in, This can be used to help debug client errors. The generated value is a long string of the form

    828ef0b8-a876-4579-85db-3cc9d1b5f6e5

Note: X-Request-Id response header always contains the same value as the CMR-Request-Id response header.

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

#### <a name="native-id"></a> CMR Native Id

A native-id is an identifier, unique per provider, used to identify concepts within CMR. The native-id is a string with no specific pattern.

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

Note: When a collection is deleted, all the associaitons will be deleted(tombstoned) too. With the new requirement that a variable can not exist without an association with a collection, since each variable can only be associated with one collection, all the variables associated with the deleted collection will be deleted too.

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

A new variable ingest endpoint is provided to ensure that variable association is created at variable ingest time.
Variable concept can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/collections/<collection-concept-id>/<collection-revision-id>/variables/<native-id>`.  `<collection-revision-id>` is optional. The response will include the [concept id](#concept-id),[revision id](#revision-id), variable-association and associated-item.

Note:

1. There is no more fingerprint check at variable's ingest/update time because the existing fingerprint is obsolete. The new variable uniqueness is defined by variable name and the collection it's associated with and is checked at variable association creation time.
2. When using the new variable ingest endpoint to update a variable, the association will be updated too. There can be one and only one association for each variable, with or without collection revision info. This decision is based on the feedback from NSIDC that there is no need for a variable to be associated with multiple revisions of a collection. When a new association info is passed in, the old one will be replaced, when the exact same association info is passed in, a new revision of the old association is created.
3. MeasurementNames must conform to values specified by the KMS list: [Measurement Names](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/MeasurementName?format=csv).

```
curl -i -XPUT \
-H "Content-type: application/vnd.nasa.cmr.umm+json" \
-H "Echo-Token: XXXX" \
%CMR-ENDPOINT%/collections/C1200000005-PROV1/1/variables/sampleVariableNativeId33 -d \
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
<?xml version="1.0" encoding="UTF-8"?><result>
    <concept-id>V1200000006-PROV1</concept-id>
    <revision-id>1</revision-id>
    <variable-association>
        <concept-id>VA1200000007-CMR</concept-id>
        <revision-id>1</revision-id>
    </variable-association>
    <associated-item>
        <concept-id>C1200000005-PROV1</concept-id>
        <revision-id>1</revision-id>
    </associated-item>
</result>

```

#### Successful Response in JSON

By passing the option `-H "Accept: application/json"` to `curl`, one may
get a JSON response:

```
{
  "concept-id" : "V1200000006-PROV1",
  "revision-id" : 1,
  "variable-association" : {
    "concept-id" : "VA1200000007-CMR",
    "revision-id" : 1
  },
  "associated-item" : {
    "concept-id" : "C1200000005-PROV1",
    "revision-id" : 1
  }
}

```

Variable concept can continue to be updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/providers/<provider-id>/variables/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id).

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

Variable concept can be deleted by sending an HTTP DELETE the URL `%CMR-ENDPOINT%/providers/<provider-id>/variables/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

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

### <a name="create-update-service"></a> Create / Update a Service

Service concept can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/providers/<provider-id>/services/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id).

```
curl -i -XPUT \
-H "Content-type: application/vnd.nasa.cmr.umm+json" \
-H "Echo-Token: XXXX" \
%CMR-ENDPOINT%/providers/PROV1/services/service123 -d \
"{\"Name\": \"AIRX3STD\",  \"Type\": \"OPeNDAP\",  \"Version\": \"1.9\",  \"Description\": \"AIRS Level-3 retrieval product created using AIRS IR, AMSU without HSB.\",  \"OnlineResource\": {    \"Linkage\": \"https://acdisc.gesdisc.eosdis.nasa.gov/opendap/Aqua_AIRS_Level3/AIRX3STD.006/\",    \"Name\": \"OPeNDAP Service for AIRS Level-3 retrieval products\",    \"Description\": \"OPeNDAP Service\"  },  \"ServiceOptions\": {\"SubsetType\": [\"Spatial\", \"Variable\"],    \"SupportedProjections\": [\"Geographic\"], \"SupportedFormats\": [\"netCDF-3\", \"netCDF-4\", \"Binary\", \"ASCII\"]}}"
```

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>S1200000015-PROV1</concept-id>
  <revision-id>1</revision-id>
</result>
```

#### Successful Response in JSON

By passing the option `-H "Accept: application/json"` to `curl`, one may
get a JSON response:

```
{"concept-id":"S1200000015-PROV1","revision-id":1}
```

### <a name="delete-service"></a> Delete a Service

Service metadata can be deleted by sending an HTTP DELETE to the URL `%CMR-ENDPOINT%/providers/<provider-id>/services/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

```
curl -i -X DELETE \
-H "Echo-Token: XXXX" \
%CMR-ENDPOINT%/providers/PROV1/services/service123
```

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>S1200000015-PROV1</concept-id>
  <revision-id>2</revision-id>
</result>
```

#### Successful Response in JSON

```
{"concept-id":"S1200000015-PROV1","revision-id":2}
```

### <a name="create-update-tool"></a> Create / Update a Tool

Tool concept can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/providers/<provider-id>/tools/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id).

```
curl -i -XPUT \
-H "Content-type: application/vnd.nasa.cmr.umm+json" \
-H "Echo-Token: XXXX" \
%CMR-ENDPOINT%/providers/PROV1/tools/tool123 -d \
"{\"Name\": \"USGS_TOOLS_LATLONG\", \"LongName\": \"WRS-2 Path/Row to Latitude/Longitude Converter\", \"Type\": \"Downloadable Tool\", \"Version\": \"1.0\", \"Description\": \"The USGS WRS-2 Path/Row to Latitude/Longitude Converter allows users to enter any Landsat path and row to get the nearest scene center latitude and longitude coordinates.\", \"URL\": { \"URLContentType\": \"DistributionURL\", \"Type\": \"DOWNLOAD SOFTWARE\", \"Description\": \"Access the WRS-2 Path/Row to Latitude/Longitude Converter.\", \"URLValue\": \"http://www.scp.byu.edu/software/slice_response/Xshape_temp.html\" }, \"ToolKeywords\" : [{ \"ToolCategory\": \"EARTH SCIENCE SERVICES\", \"ToolTopic\": \"DATA MANAGEMENT/DATA HANDLING\", \"ToolTerm\": \"DATA INTEROPERABILITY\", \"ToolSpecificTerm\": \"DATA REFORMATTING\" }], \"Organizations\" : [ { \"Roles\": [\"SERVICE PROVIDER\"], \"ShortName\": \"USGS/EROS\",    \"LongName\": \"US GEOLOGICAL SURVEY EARTH RESOURCE OBSERVATION AND SCIENCE (EROS) LANDSAT CUSTOMER SERVICES\", \"URLValue\": \"http://www.usgs.gov\" } ], \"MetadataSpecification\": { \"URL\": \"https://cdn.earthdata.nasa.gov/umm/tool/v1.0\", \"Name\": \"UMM-T\", \"Version\": \"1.0\" }"
```

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>TL1200000015-PROV1</concept-id>
  <revision-id>1</revision-id>
</result>
```

#### Successful Response in JSON

By passing the option `-H "Accept: application/json"` to `curl`, one may
get a JSON response:

```
{"concept-id":"TL1200000015-PROV1","revision-id":1}
```

### <a name="delete-tool"></a> Delete a Tool

Tool metadata can be deleted by sending an HTTP DELETE to the URL `%CMR-ENDPOINT%/providers/<provider-id>/tools/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

```
curl -i -X DELETE \
-H "Echo-Token: XXXX" \
%CMR-ENDPOINT%/providers/PROV1/tools/tool123
```

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>TL1200000015-PROV1</concept-id>
  <revision-id>2</revision-id>
</result>
```

#### Successful Response in JSON

```
{"concept-id":"TL1200000015-PROV1","revision-id":2}
```

### <a name="create-subscription"></a> Create a Subscription

Subscription concepts can be created by sending an HTTP POST or PUT with the metadata sent as data to the URL `%CMR-ENDPOINT%/providers/<provider-id>/subscriptions/<native-id>`. The response will include the [concept id](#concept-id) ,the [revision id](#revision-id), and a [native-id](#native-id).

If a native-id is not provided it will be generated. This is only supported for POST requests.
POST requests may only be used for creating subscriptions.

If a SubscriberId is not provided, then the user ID associated with the token used to ingest the subscription will be used as the SubscriberId.

If an EmailAddress is not provided, then the email address associated with the SubscriberId's Earthdata Login (URS) account will be used as the EmailAddress.

POST only may be used without a native-id at the following URL.
`%CMR-ENDPOINT%/providers/<provider-id>/subscriptions`

POST or PUT may be used with the following URL.
`%CMR-ENDPOINT%/providers/<provider-id>/subscriptions/<native-id>`

Query values should not be URL encoded.

### <a name="update-subscription"></a> Update a Subscription

Subscription concept can be updated by sending an HTTP POST or PUT with the metadata sent as data to the URL `%CMR-ENDPOINT%/providers/<provider-id>/subscriptions/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id).

If a native-id is provided in a POST, and a subscription already exists for that provider with the given native-id, the request will be rejected.

PUT requests should be used for updating subscriptions. Creation of subscriptions using PUT may be deprecated in the future. All PUT requests require a native-id to be part of the request URL.

Query values should not be URL encoded.

```
curl -i -XPUT \
-H "Content-type: application/vnd.nasa.cmr.umm+json" \
-H "Echo-Token: XXXX" \
%CMR-ENDPOINT%/providers/PROV1/subscriptions/subscription123 -d \
"{\"Name\": \"someSubscription\",  \"SubscriberId\": \"someSubscriberId\",  \"EmailAddress\": \"someaddress@gmail.com\",  \"CollectionConceptId\": \"C1234-PROV1.\",  \"Query\": \"polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78\"}"
```

```
curl -i -XPOST \
-H "Content-type: application/vnd.nasa.cmr.umm+json" \
-H "Echo-Token: XXXX" \
%CMR-ENDPOINT%/providers/PROV1/subscriptions -d \
"{\"Name\": \"someSubscription\",  \"SubscriberId\": \"someSubscriberId\",  \"EmailAddress\": \"someaddress@gmail.com\",  \"CollectionConceptId\": \"C1234-PROV1.\",  \"Query\": \"polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78\"}"
```

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>SUB1200000015-PROV1</concept-id>
  <revision-id>1</revision-id>
  <native-id>subscription123</native-id>
</result>
```
#### Successful Response in JSON

By passing the option `-H "Accept: application/json"` to `curl`, one may
get a JSON response:

```
{"concept-id":"SUB1200000015-PROV1","revision-id":1,"native-id":"subscription123"}
```

### <a name="delete-subscription"></a> Delete a Subscription

Subscription metadata can be deleted by sending an HTTP DELETE to the URL `%CMR-ENDPOINT%/providers/<provider-id>/subscriptions/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

```
curl -i -X DELETE \
-H "Echo-Token: XXXX" \
%CMR-ENDPOINT%/providers/PROV1/subscriptions/subscription123
```

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>SUB1200000015-PROV1</concept-id>
  <revision-id>2</revision-id>
</result>
```

#### Successful Response in JSON

```
{"concept-id":"SUB1200000015-PROV1","revision-id":2}
```
### <a name="subscription-access-control"></a> Subscription Access Control

Ingest permissions for subscriptions are granted through the provider via the INGEST_MANAGEMENT_ACL and SUBSCRIPTION_MANAGEMENT. In order to ingest/update/delete a subscription for a given provider, update permission has to be granted to the user through both INGEST_MANAGEMENT_ACL and SUBSCRIPTION_MANAGEMENT ACLs for the provider.

## <a name="translate-collection"></a> Translate Collection Metadata

Collection metadata can be translated between metadata standards using the translate API in Ingest. This API also supports the UMM JSON format which represents UMM as JSON. The request specifies the metadata standard being sent using the Content-Type header. Metadata is sent inside the body of the request. The output format is specified via the Accept header.

To disable validation of the parsed UMM metadata against the current UMM spec, pass `skip_umm_validation=true` as a query parameter.

Example: Translate an ECHO10 metadata to UMM JSON version 1.14

```
curl -i -XPOST \
  -H "Content-Type: application/echo10+xml" \
  -H "Accept: application/vnd.nasa.cmr.umm+json;version=1.14" \
  %CMR-ENDPOINT%/translate/collection\?skip_umm_validation\=true \
  -d \
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
  "SpatialExtent" : {
    "GranuleSpatialRepresentation" : "NO_SPATIAL"
  },
  "CollectionProgress" : "NOT PROVIDED",
  "ScienceKeywords" : [ {
    "Category" : "EARTH SCIENCE",
    "Topic" : "Not provided",
    "Term" : "Not provided"
  } ],
  "TemporalExtents" : [ {
    "RangeDateTimes" : [ {
      "BeginningDateTime" : "1970-01-01T00:00:00.000Z"
    } ]
  } ],
  "ProcessingLevel" : {
    "Id" : "Not provided"
  },
  "ShortName" : "ShortName_Larc",
  "EntryTitle" : "LarcDatasetId",
  "DataDates" : [ {
    "Date" : "2000-01-01T00:00:00.000Z",
    "Type" : "CREATE"
  }, {
    "Date" : "2000-01-01T00:00:00.000Z",
    "Type" : "UPDATE"
  }, {
    "Date" : "2015-05-23T22:30:59.000Z",
    "Type" : "DELETE"
  } ],
  "Abstract" : "A minimal valid collection",
  "Version" : "Version01",
  "DataCenters" : [ {
    "Roles" : [ "ARCHIVER" ],
    "ShortName" : "Not provided"
  } ],
  "Platforms" : [ {
    "ShortName" : "Not provided"
  } ],
  "ArchiveAndDistributionInformation" : {
    "FileArchiveInformation" : [ ],
    "FileDistributionInformation" : [ ]
  }
}
```

## <a name="translate-granule"></a> Translate Granule Metadata

Granule metadata can be translated between metadata standards using the translate API in Ingest. The request specifies the metadata standard being sent using the Content-Type header. Metadata is sent inside the body of the request. The output format is specified via the Accept header. The supported input formats are ECHO10, ISO SMAP and UMM-G. The supported output formats are ECHO10, ISO SMAP, UMM-G and ISO19115.

Example: Translate ECHO10 metadata to UMM-G

```
curl -i -XPOST \
  -H "Content-Type: application/echo10+xml" \
  -H "Accept: application/vnd.nasa.cmr.umm+json;version=1.6" \
  %CMR-ENDPOINT%/translate/granule \
  -d \
"<Granule>
  <GranuleUR>SC:AE_5DSno.002:30500512</GranuleUR>
  <InsertTime>2009-05-11T20:09:16.340Z</InsertTime>
  <LastUpdate>2014-03-19T09:59:12.207Z</LastUpdate>
  <Collection>
    <DataSetId>collection_test_2468</DataSetId>
  </Collection>
  <Orderable>true</Orderable>
 </Granule>"
```

Example output:

```
{
  "ProviderDates" : [ {
    "Date" : "2009-05-11T20:09:16.340Z",
    "Type" : "Insert"
  }, {
    "Date" : "2014-03-19T09:59:12.207Z",
    "Type" : "Update"
  } ],
  "CollectionReference" : {
    "EntryTitle" : "collection_test_2468"
  },
  "DataGranule" : { },
  "GranuleUR" : "SC:AE_5DSno.002:30500512",
  "MetadataSpecification" : {
    "URL" : "https://cdn.earthdata.nasa.gov/umm/granule/v1.6",
    "Name" : "UMM-G",
    "Version" : "1.6"
  }
}
```
## <a name="collection-bulk-update"></a> Collection Bulk Update

The collection bulk update API is used perform the same collection update to multiple concepts in one call.

Bulk update is initiated through an ingest POST endpoint with the concept ids to update, the update type, the update field, and update information. The metadata is converted to the latest version of UMM, if not the native format, updated according to the parameters, and saved as the latest version of UMM-JSON, **making the native format of the collection now UMM-JSON**. Previous revisions of the collection are retained in the original native format. In the UMM-JSON metadata, the Metadata Date of type "UPDATE" will be set to the current date. Please note that when we apply bulk update on a collection, regardless if there are actual changes, a new revision is created.

Updated collections are validated using business rule validations.  Updates will not be saved if the business validations fail. The error will be recorded in the individual collection status, which can be queried via the status endpoint. Collection validation warnings will not prevent saving the updated collection and the warnings will be recorded in the individual collection status.

Collection bulk update currently supports updating the following fields:

  * Science Keywords
  * Location Keywords
  * Data Centers
  * Instruments
  * Platforms

The following update types are supported:

  * Add to existing - the update value is added to the existing list. An update value is required.
  * Clear all and replace - clear the list and replace with the update value.
  * Find and replace - replace any instance in the list that matches the find value with the update value.
  * Find and update - merge update value into any instance in the list that matches the find value.
  * Find and update home page url - A special case for Find and update.
  * Find and remove - remove any instance from the list that matches the find value.

Bulk update post request takes the following parameters:

  * Concept-ids (required) - a list of concept ids to update, which need to be associated with the provider the bulk update is initiated with. If it is equal to ["ALL"], case insensitive, all the collections for the provider will be updated.
  * Name (optional) - a name used to identify a bulk update task. It needs to be unique within a provider.
  * Update type (required) - choose from the enumeration: `ADD_TO_EXISTING`, `CLEAR_ALL_AND_REPLACE`, `FIND_AND_REPLACE`, `FIND_AND_REMOVE`, `FIND_AND_UPDATE`, `FIND_AND_UPDATE_HOME_PAGE_URL`
  * Update field (required) - choose from the enumeration: `SCIENCE_KEYWORDS`, `LOCATION_KEYWORDS`, `DATA_CENTERS`, `PLATFORMS`, `INSTRUMENTS`
  * Update value (required for all update types except for `FIND_AND_REMOVE`) - UMM-JSON representation of the update to make. It could be an array of objects when update type is `ADD_TO_EXISTING`, `CLEAR_ALL_AND_REPLACE` and `FIND_AND_REPLACE`. For any other update types, it can only be a single object. Update value can contain null values for non-required fields which indicates that these non-required fields should be removed in the found objects.  
  * Find value (required for `FIND_AND_REPLACE`, `FIND_AND_UPDATE` and `FIND_AND_REMOVE` update types) - UMM-JSON representation of the data to find

Update types that include a FIND will match on the fields supplied in the find value. For example, for a science keyword update with a find value of `{"Category": "EARTH SCIENCE"}`, any science keyword with a category of "EARTH SCIENCE" will be considered a match regardless of the values of the science keyword topic, term, etc.  It's worth noting that find value can not contain nested fields. So for bulk update on PLATFORMS, for example, find value can only contain Type, ShortName and LongName, not the nested fields like Characteristics and Instruments. On the other hand, update value can contain all the valid fields including the nested fields. So, nested fields can be updated, they just can't be used to find the matches.   

The difference between `FIND_AND_UPDATE` and `FIND_AND_REPLACE` is `FIND_AND_REPLACE` will remove the matches and replace them entirely with the values specified in update value, while with `FIND_AND_UPDATE`, only the field(s) specified in the update value will be replaced, with the rest of the original value retained. For example, with a platform update value of `{"ShortName": "A340-600"}`, only the short name will be updated during a find and update, while the long name, instruments, and other fields retain their values. If a field specified in the update value doesn't exist in the matches, the field will be added.

`FIND_AND_UPDATE_HOME_PAGE_URL` is a special case for `FIND_AND_UPDATE`. It can only be used with Update field being `DATA_CENTERS`. It is the same as `FIND_AND_UPDATE` except that when update value contains ContactInformation, it doesn't replace the ContactInformation, instead it only replaces the data center HOME PAGE URL part with the new data center HOME PAGE URL specified in the RelatedUrls, if it exists, and leaves everything else in the ContactInformation untouched. If the new data center HOME PAGE URL is not present in the update value, the HOME PAGE URL of the found data centers will be removed.

Instruments are nested within platforms so instrument updates are applied to all platforms in the collection, when applying
`ADD_TO_EXISTING` and `CLEAR_ALL_AND_REPLACE` bulk updates to the instruments.

If multiple bulk updates are run at the same time with the same concept-ids, there is no guarantee of the order that the updates will be performed on a collection. For example, if a clear all and replace is initiated, then an add to existing on the same collection, the clear all and replace could happen after the add to existing. Because of this, it is best to not run bulk update operations in parallel on overlapping collections.

### Initiate Bulk Update

Bulk update can be initiated by sending an HTTP POST request to `%CMR-ENDPOINT%/providers/<provider-id>/bulk-update/collections`

The return value includes a status code indicating that the bulk update was successfully initiated, any errors if not successful, and on success a task-id that can be used for querying the bulk update status. The bulk update will be run asynchronously and the status of the overall bulk update task as well as the status of individual collection updates can be queried using the task id.

Example: Initiate a bulk update of 3 collections. Find platforms that have Type being "Aircraft" and replace the LongName and Characteristics of these platforms with "new long name" and new Characteristics in the update-value, or add the fields specified in the update-value if they don't exist in the matched platforms.

```
curl -i -XPOST -H "Cmr-Pretty:true" -H "Content-Type: application/json" -H "Echo-Token: XXXX" %CMR-ENDPOINT%/providers/PROV1/bulk-update/collections -d
'{"concept-ids": ["C1200000005-PROV1","C1200000006-PROV1","C1200000007-PROV1"],
  "name": "TEST NAME",
  "update-type": "FIND_AND_UPDATE",
  "update-field": "PLATFORMS",
  "find-value": {"Type": "Aircraft"},
  "update-value": {"LongName": "new long name",
                   "Characteristics": [{"Name": "nested field is allowed in update-value",
                                        "Description": "Orbital period in decimal minutes.",
                                        "DataType": "time/Direction (ascending)",
                                        "Unit": "Minutes",
                                        "Value": "96.7"}]}}'

<?xml version="1.0" encoding="UTF-8"?>
<result>
    <status>200</status>
    <task-id>4</task-id>
</result>
```


### Query Bulk Update Status

The task ids and status of all bulk update tasks for a provider can be queried by sending an HTTP GET request to `%CMR-ENDPOINT%/providers/<provider-id>/bulk-update/collections/status`

This returns a list of: created-at, name, task id, status (IN_PROGRESS or COMPLETE), a status message, and the original request JSON body.

Example
```
curl -i -H "Echo-Token: XXXX" -H "Cmr-Pretty:true" %CMR-ENDPOINT%/providers/PROV1/bulk-update/collections/status

<?xml version="1.0" encoding="UTF-8"?>
<result>
    <tasks>
        <task>
            <created-at>2017-10-24T17:00:03.000Z</created-at>
            <name>TEST NAME1</name>
            <task-id>21</task-id>
            <status>COMPLETE</status>
            <status-message>Task completed with 1 FAILED and 4 UPDATED out of 5 total collection update(s).</status-message>
            <request-json-body>{"concept-ids": ["C12807-PROV1","C17995-PROV1","C18002-PROV1","C18016-PROV1"],"update-type": "FIND_AND_REMOVE","update-field": "SCIENCE_KEYWORDS","find-value": {"Category": "EARTH SCIENCE","Topic": "HUMAN DIMENSIONS","Term": "ENVIRONMENTAL IMPACTS","VariableLevel1": "HEAVY METALS CONCENTRATION"}}</request-json-body>
        </task>
        <task>
            <created-at>2017-10-24T17:00:03.000Z</created-at>
            <name>TEST NAME2</name>
            <task-id>22</task-id>
            <status>COMPLETE</status>
            <status-message>Task completed with 1 FAILED and 2 UPDATED out of 3 total collection update(s).</status-message>
            <request-json-body>{"concept-ids": ["C13239-PROV1","C13276-PROV1","C13883-PROV1","C13286-PROV1"],"update-type": "CLEAR_ALL_AND_REPLACE","update-field": "SCIENCE_KEYWORDS","update-value": {"Category": "EARTH SCIENCE","Topic": "HUMAN DIMENSIONS","Term": "ENVIRONMENTAL IMPACTS","VariableLevel1": "HEAVY METALS CONCENTRATION"}}</request-json-body>
        </task>
        <task>
            <created-at>2017-10-24T17:00:03.000Z</created-at>
            <name>TEST NAME3</name>
            <task-id>2</task-id>
            <status>COMPLETE</status>
            <status-message>All collection updates completed successfully.</status-message>
            <request-json-body>{"concept-ids": ["C12130-PROV1"],"update-type": "ADD_TO_EXISTING", "update-field": "SCIENCE_KEYWORDS","update-value": {"Category": "EARTH SCIENCE","Topic": "HUMAN DIMENSIONS","Term": "ENVIRONMENTAL IMPACTS","VariableLevel1": "HEAVY METALS CONCENTRATION"}}</request-json-body>
        </task>
    </tasks>
</result>
```

A more detailed status for an individual task can be queried by sending an HTTP GET request to `%CMR-ENDPOINT%/providers/<provider-id>/bulk-update/collections/status/<task-id>`

This returns the status of the bulk update task including the overall task status (IN_PROGRESS or COMPLETE), an overall task status message, the original request JSON body, and the status of each collection updated. The collection status includes the concept-id, the collection update status (PENDING, UPDATED, SKIPPED, FAILED), and a status message. FAILED indicates an error occurred either updating the collection or during collection validation. SKIPPED indicates the update didn't happen because the find-value is not found in the collection during the find operations. The error will be reported in the collection status message. If collection validation results in warnings, the warnings will be reported in the status message.

Example: Collection statuses with 1 failure, 1 skip and 1 warnings
```
curl -i -H "Echo-Token: XXXX" -H "Cmr-Pretty:true" %CMR-ENDPOINT%/providers/PROV1/bulk-update/collections/status/25

<?xml version="1.0" encoding="UTF-8"?>
<result>
    <created-at>2017-10-24T17:00:03.000Z</created-at>
    <name>TEST NAME</name>
    <task-status>COMPLETE</task-status>
    <status-message>Task completed with 1 FAILED, 1 SKIPPED and 3 UPDATED out of 5 total collection update(s).</status-message>
    <request-json-body>{"concept-ids": ["C11984-PROV1","C11991-PROV1","C119916-PROV1","C14432-PROV1","C20000-PROV1"],"update-type": "FIND_AND_REMOVE","update-field": "SCIENCE_KEYWORDS","find-value": {"Category": "EARTH SCIENCE","Topic": "HUMAN DIMENSIONS","Term": "ENVIRONMENTAL IMPACTS","VariableLevel1": "HEAVY METALS CONCENTRATION"}}</request-json-body>
    <collection-statuses>
        <collection-status>
            <concept-id>C11984-PROV1</concept-id>
            <status>UPDATED</status>
        </collection-status>
        <collection-status>
            <concept-id>C11991-PROV1</concept-id>
            <status>UPDATED</status>
        </collection-status>
        <collection-status>
            <concept-id>C119916-PROV1</concept-id>
            <status>SKIPPED</status>
            <status-message>Collection with concept-id [C119916-PROV1] is not updated because no find-value found.</status-message>
        </collection-status>
        <collection-status>
            <concept-id>C14432-PROV1</concept-id>
            <status>FAILED</status>
            <status-message>/PublicationReferences/2 object instance has properties which are not allowed by the schema: ["_errors"]</status-message>
        </collection-status>
        <collection-status>
            <concept-id>C20000-PROV1</concept-id>
            <status>UPDATED</status>
            <status-message>Collection was updated successfully, but translating the collection to UMM-C had the following issues: [:RelatedUrls 4 :URL] [http://gcmd.nasa.gov/r/d/[NOAA-NGDC]gov.noaa.ngdc.mgg.photos.G01372] is not a valid URL</status-message>
        </collection-status>
    </collection-statuses>
</result>
```
Bulk update status and results are available for 90 days.

## <a name="granule-bulk-update"></a> Granule Bulk Update

The granule bulk update API is used perform the same granule update to multiple concepts in one call.

Bulk update is initiated through an ingest POST endpoint with the concept ids to update, the update type, the update field, and update information.

Updated granules are validated using business rule validations.  Updates will not be saved if the business validations fail. The error will be recorded in the individual granule status, which can be queried via the status endpoint. Granule validation warnings will not prevent saving the updated granule and the warnings will be recorded in the individual granule status.

Granule bulk update currently supports updating the following fields:

  * Related URLs

Bulk update post request takes a JSON file with the following parameters:

  * Concept-ids (required) - a list of concept ids to update, which need to be associated with the provider the bulk update is initiated with. If it is equal to ["ALL"], case insensitive, all the collections for the provider will be updated.
  * Name (optional) - a name used to identify a bulk update task. It needs to be unique within a provider. If no name is provided this will be defaulted to the task id that is created when a bulk update task is initiated.
  * Update type (required) - choose from the enumeration: `ADD_TO_EXISTING`, `CLEAR_ALL_AND_REPLACE`, `FIND_AND_REPLACE`, `FIND_AND_REMOVE`, `FIND_AND_UPDATE`
  * Update field (required) - choose from the enumeration: `RELATED URLS`
  * Update value (required for all update types except for `FIND_AND_REMOVE`) - UMM-JSON representation of the update to make. It could be an array of objects when update type is `ADD_TO_EXISTING`, `CLEAR_ALL_AND_REPLACE` and `FIND_AND_REPLACE`. For any other update types, it can only be a single object. Update value can contain null values for non-required fields which indicates that these non-required fields should be removed in the found objects.  
  * Find value (required for `FIND_AND_REPLACE`, `FIND_AND_UPDATE` and `FIND_AND_REMOVE` update types) - UMM-JSON representation of the data to find
