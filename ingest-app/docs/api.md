## API Documentation

See the [CMR Data Partner User Guide](https://wiki.earthdata.nasa.gov/display/CMR/CMR+Data+Partner+User+Guide) for a general guide to utilizing the CMR Ingest API as a data partner.
See the [CMR Client Partner User Guide](https://wiki.earthdata.nasa.gov/display/CMR/CMR+Client+Partner+User+Guide) for a general guide to developing a CMR client.

### API Conventions

* [HTTP Headers](#headers)
* [Responses](#responses)
* [CMR Ids](#cmr-ids)

### Latest UMM Schema Versions

* [Latest UMM schema versions](#latest-umm-versions)

### Metadata Ingest API Overview

* [Collections](#collection)
    * [/providers/\<provider-id>/validate/collection/\<native-id>](#validate-collection-endpoint)
        * [POST - Validate collection metadata.](#validate-collection)
    * [/providers/\<provider-id>/collections/\<native-id>](#create-delete-collection-endpoint)
        * [PUT - Create or update a collection.](#create-update-collection)
        * [DELETE - Delete a collection.](#delete-collection)
    * [/collections/\<collection-concept-id>/\<collection-revision-id>/variables/\<native-id>](#create-var-coll-endpoint)
        * [PUT - Create or update a variable with association.](#create-update-variable)
* [Granules](#granule)
    * [/providers/\<provider-id>/validate/granule/\<native-id>](#validate-granule-endpoint)
        * [POST - Validate granule metadata.](#validate-granule)
    * [/providers/\<provider-id>/granules/\<native-id>](#create-delete-granule-endpoint)
        * [PUT - Create or update a granule.](#create-update-granule)
        * [DELETE - Delete a granule.](#delete-granule)
* [Variables](#variable)
    * [/providers/\<provider-id>/variables/\<native-id>](#variable-endpoint)
        * [PUT - Update a variable.](#create-update-variable)
        * [DELETE - Delete a variable.](#delete-variable)
* [Services](#service)
    * [/providers/\<provider-id>/services/\<native-id>](#service-endpoint)
        * [PUT - Create or update a service.](#create-update-service)
        * [DELETE - Delete a service.](#delete-service)
* [Tools](#tool)
    * [/providers/\<provider-id>/tools/\<native-id>](#tool-endpoint)
        * [PUT - Create or update a tool.](#create-update-tool)
        * [DELETE - Delete a tool.](#delete-tool)
* [Subscriptions](#subscription)
    * [/subscriptions](#subscription-endpoint)
        *  [POST - Create a subscription without specifying a native-id.](#create-subscription)
    * [/subscriptions/\<native-id>](#subscription-native-id-endpoint)
        * [POST - Create a subscription with a provided native-id.](#create-subscription)
        * [PUT - Create or Update a subscription.](#update-subscription)
        * [DELETE - Delete a subscription.](#delete-subscription)
        * [Subscription Access Control](#subscription-access-control)
* %GENERIC-TABLE-OF-CONTENTS%
* [Translations](#translate-collection)
    * [/translate/collection](#translate-collection-endpoint)
        * [POST - Translate collection metadata.](#translate-collection)
    * [/translate/granule](#translate-granule-endpoint)
        * [POST - Translate granule metadata.](#translate-granule)
* [Bulk Updates](#bulk-update-collection)
    * [/providers/\<provider-id\>/bulk-update/collections](#bulk-update-collection-endpoint)
        * [POST - Collection bulk update](#collection-bulk-update)
    * [/providers/\<provider-id\>/bulk-update/granules](#bulk-update-granule-endpoint)
        *  [POST - Granule bulk update](#granule-bulk-update)
    * [/granule-bulk-update/status](#bulk-update-granules-status-endpoint)
        * [GET - Granule bulk update status](#bulk-update-granules-status)
    * [/granule-bulk-update/status/\<task-id\>](#bulk-update-granules-task-id-endpoint)
        * [GET - Granule bulk update status by task-id](#bulk-update-granules-task-id)

--------------------------------------------------------------------------------

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

Note: UMM JSON accepts an additional version parameter for `Content-Type`. Like charset, it is appended with a semicolon (;). UMM JSON version is required.

For an example, the following means version 1.16.2 of the UMM JSON format:

    application/vnd.nasa.cmr.umm+json;version=1.16.2

Note: For all values of `Content-Type`, data sent using POST or PUT should not be URL encoded.

#### <a name="authorization-header"></a> Authorization Header
All Ingest API operations require specifying a token obtained from [Earthdata Login (EDL)](https://urs.earthdata.nasa.gov). The token should be specified using the `Authorization: Bearer` header followed by the EDL bearer token. For more information on obtaining an EDL bearer token, please reference the documentation [here](https://urs.earthdata.nasa.gov/documentation/for_users/user_token).

#### <a name="accept-header"></a> Accept Header

The `Accept` header specifies the format of the response message and defaults to XML for the normal Ingest APIs. `application/json` can be specified if the preferred responses is JSON.

UMM JSON accepts an additional version parameter for `Accept` header. Like charset, it is appended with a semicolon (;). If no UMM JSON version is provided, the latest version will be used.

For an example, the following means version 1.16.2 of the UMM JSON format:

    application/vnd.nasa.cmr.umm+json;version=1.16.2

#### <a name="cmr-pretty-header"></a> Cmr-Pretty Header

The `Cmr-Pretty` Header set to `true` or using the alias `&pretty=true` URL parameter will tell CMR to format the output with new lines and spaces for better readability by humans.

    curl -H "Cmr-Pretty: true" ...

#### <a name="cmr-pretty-header"></a> Cmr-Pretty Header

The `Cmr-Pretty` Header set to `true` or using the alias `&pretty=true` URL parameter will tell CMR to format the output with new lines and spaces for better readability by humans.

    curl -H "Cmr-Pretty: true" ...

#### <a name="cmr-revision-id-header"></a> Cmr-Revision-Id Header

The `Cmr-Revision-Id` header allows specifying the [revision id](#revision-id) to use when saving the concept. If the revision id specified is not the latest a HTTP Status code of 409 will be returned indicating a conflict.

#### <a name="cmr-concept-id-header"></a> Cmr-Concept-Id (or Concept-Id) Header

The `Cmr-Concept-Id` header allows specifying the [concept id](#concept-id) to use when saving a concept. This should normally not be sent by clients. The CMR should normally generate the concept id. The header `Concept-Id` is an alias for `Cmr-Concept-Id`.

#### <a name="validate-keywords-header"></a> Cmr-Validate-Keywords Header

If the `Cmr-Validate-Keywords` header is set to `true`, ingest will validate that the UMM-C collection keywords match known keywords from the GCMD KMS.

	curl -H "Cmr-Validate-Keywords: true" ...

The following fields are validated:

* [Platforms](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/platforms?format=csv) - short name, long name, and type
* [Instruments](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/instruments?format=csv) - short name and long name
* [Projects](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/projects?format=csv) - short name and long name
* [Science Keywords](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/sciencekeywords?format=csv) - category, topic, term, variable level 1, variable level 2, variable level 3.
* [Location Keywords](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/locations?format=csv) - category, type, subregion 1, subregion 2, subregion 3.
* [Data Centers](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/providers?format=csv) - short name
* [Directory Names](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/idnnode?format=csv) - short name
* [ISO Topic Categories](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/isotopiccategory?format=csv) - iso topic category
* [Data Format](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/dataformat?format=csv) - Archival and Distribution File Format, and GetData Format

**Note**: that when multiple fields are present the combination of keywords are validated to match a known combination.
**Note**: Among the validation fields above, [Platforms], [Instruments], [Projects], [Science Keywords], [Location Keywords] and [Data Centers] are also validated when the `Cmr-Validate-Keywords` header is not set to `true` except that  validation errors will be returned to users as warnings.


**Note**: the following fields are always checked:

* [Related URL Content Type](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/rucontenttype?format=csv) - Related URL Content Type, Type, and Subtype are all verified and must belong to the correct set and context. Example: A data center contact URL can have a URL Content Type of "DataContactURL" and a Type of "HOME PAGE" with no Subtype.
* [Granule Data Format](https://cmr.sit.earthdata.nasa.gov/search/keywords/granule-data-format) - Was previously checked against the JSON Schema, but starting with version 1.6.4 CMR will use KMS.

#### <a name="validate-umm-c-header"></a> Cmr-Validate-Umm-C Header

If the `Cmr-Validate-Umm-C` header is set to `true`, collection metadata is validated against the UMM-C JSON schema. It also uses the UMM-C Specification for parsing the metadata and checking business rules. This is temporary header for testing. Eventually the CMR will enforce this validation by default.

	curl -H "Cmr-Validate-Umm-C: true" ...

#### <a name="skip-sanitize-umm-c-header"></a> Cmr-Skip-Sanitize-Umm-C Header

If the `Cmr-Skip-Sanitize-Umm-C` header is set to `true`, translation to UMM JSON will not add default values to the converted UMM when the required fields are missing. This may cause umm schema validation failure if skip-umm-validation is not set to true. This header can not be set to true when translating to formats other than UMM JSON.

	curl -H "Cmr-Skip-Sanitize-Umm-C: true" ...

#### <a name="user-id"></a> User-Id Header

The `User-Id` header allows specifying the user-id to use when saving or deleting a collection concept. This header is currently ignored for granule concepts. If user-id header is not specified, user id is retrieved using the token supplied during the ingest.

#### <a name="x-request-id"></a> X-Request-Id Header

This provides standard `X-Request-Id` support to allow user to pass in some random ID which will be logged on the server side for debugging purpose.

#### <a name="cmr-request-id"></a> CMR-Request-Id Header

This header serves the same purpose as X-Request-Id header. It's kept to support legacy systems.

--------------------------------------------------------------------------------

### <a name="responses"></a> Responses

### <a name="response-headers"></a> Response Headers

#### <a name="CMR-Request-Id-header"></a> cmr-request-id

This header returns the value passed in through `CMR-Request-Id` request header or `X-Request-Id` request header or a unique id generated for the client request when no value is passed in, This can be used to help debug client errors. The generated value is a long string of the form

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

    {"concept-id" : "C1200000005-PROV1",
     "revision-id" : 1,
     "warnings" : null,
     "existing-errors" : null}

When there are existing errors allowed for progressive update, the response looks like the following:

    {"concept-id" : "C1200000005-PROV1",
     "revision-id" : 2,
     "warnings" : [ "After translating item to UMM-C the metadata had the following issue(s): [:TilingIdentificationSystems] Tiling Identification Systems must be unique. This contains duplicates named [MODIS Tile EASE, MISR].;; [:AdditionalAttributes 2] Value [6] is not a valid value for type [DATETIME].;; [:AdditionalAttributes 3] Value [GHRSST Level 2P Global Subskin Sea Surface Temperature from the Advanced Microwave Scanning Radiometer 2 on the GCOM-W satellite] is not a valid value for type [DATETIME]." ],
     "existing-errors" : [ "After translating item to UMM-C the metadata had the following existing error(s): [:TilingIdentificationSystems] Tiling Identification Systems must be unique. This contains duplicates named [MODIS Tile EASE, MISR].;; [:AdditionalAttributes 2] Value [6] is not a valid value for type [DATETIME].;; [:AdditionalAttributes 3] Value [GHRSST Level 2P Global Subskin Sea Surface Temperature from the Advanced Microwave Scanning Radiometer 2 on the GCOM-W satellite] is not a valid value for type [DATETIME]." ]}

Note: The delimiter for different warnings and existing-errors is ";; ".

#### <a name="error-response"></a> Error Responses

Requests could fail for several reasons when communicating with the CMR as described in the [HTTP Status Codes](#http-status-codes).

Ingest validation errors can take one of two forms in the following:

##### <a name="general-errors"></a> General Errors

General error messages will be returned as a list of error messages like the following:

```
<errors>
   <error>Parent collection for granule [SC:AE_5DSno.002:30500511] does not exist.</error>
</errors>

```

##### <a name="umm-validation-errors"></a> UMM Validation Errors

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
General error case (schema errors, json syntax errors etc.) when path is not applicable:
{
  "errors" : [ "Invalid JSON: Expected a ',' or '}' at 3457 [character 7 line 91]" ]
}

UMM validation case:
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

--------------------------------------------------------------------------------

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

--------------------------------------------------------------------------------

## <a name="metadata-ingest"></a> Metadata Ingest

### <a name="latest-umm-versions"></a> Latest UMM Schema Versions

The following are the latest acceptable UMM schema versions for metadata ingest:

* UMM-C: {{ umm-c }}
* UMM-G: {{ umm-g }}
* UMM-S: {{ umm-s }}
* UMM-T: {{ umm-t }}
* UMM-SUB: {{ umm-sub }}
* UMM-VAR: {{ umm-var }}

Other document versions:

%ALL-GENERIC-DOCUMENT-VERSIONS%

[//]: # "Note: The above version variables will be rendered at html generation time."

## <a name="collection"></a> Collection
### <a name="validate-collection"></a> Validate Collection
#### <a name="validate-collection-endpoint"></a> /providers/&lt;provider-id&gt;/validate/collection/&lt;native-id&gt;

Collection metadata can be validated without having to ingest it. The validation performed is schema validation, UMM validation, and inventory specific validations. Keyword validation can be enabled with the [keyword validation header](#validate-keywords-header). It returns status code 200 with a list of any warnings on successful validation, status code 400 with a list of validation errors on failed validation. Warnings would be returned if the ingested record passes native XML schema validation, but not UMM-C validation.

```
curl -XPOST -H "Content-type: application/echo10+xml" \
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/providers/PROV1/validate/collection/sampleNativeId15 \
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

### <a name="create-update-collection"></a> Create / Update a Collection
#### <a name="create-delete-collection-endpoint"></a> /providers/&lt;provider-id&gt;/collections/&lt;native-id&gt;

Collection metadata can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/providers/<provider-id>/collections/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id). The metadata that is uploaded is validated for XML well-formedness, XML schema validation, and against UMM validation rules. Keyword validation can be enabled with the [keyword validation header](#validate-keywords-header). If there is a need to retrieve the native-id of an already-ingested collection for updating, requesting the collection via the search API in UMM-JSON format will provide the native-id.

Note: we now provide progressive collection update feature through a new configuration parameter CMR_PROGRESSIVE_UPDATE_ENABLED, which is turned on by default. It allows a collection to be updated with non-schema related validation errors that are existing validation errors for the previous collection revision. Only newly introduced validation errors will fail the update. Schema validation errors always fail the update.

```
curl -XPUT \
  -H "Content-type: application/echo10+xml" \
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/providers/PROV1/collections/sampleNativeId15 \
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

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>C1200000000-PROV1</concept-id>
  <revision-id>1</revision-id>
</result>
```

#### Successful Response in JSON

	{"concept-id":"C1200000000-PROV1","revision-id":1}

### <a name="delete-collection"></a> Delete a Collection

Collection metadata can be deleted by sending an HTTP DELETE the URL `%CMR-ENDPOINT%/providers/<provider-id>/collections/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

	curl -XDELETE \
  		-H "Authorization: Bearer XXXX" \
  		%CMR-ENDPOINT%/providers/PROV1/collections/sampleNativeId15

Note: When a collection is deleted, all the associations will be deleted, also called tombstoned (tombstoned means to mark record as ready to be deleted but the actual deletion is scheduled for latter) too. With the new requirement that a variable can not exist without an association with a collection, since each variable can only be associated with one collection, all the variables associated with the deleted collection will be deleted too.

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>C1200000000-PROV1</concept-id>
  <revision-id>2</revision-id>
</result>
```

#### Successful Response in JSON

	{"concept-id":"C1200000000-PROV1","revision-id":2}
## <a name="granule"></a> Granule
### <a name="validate-granule"></a> Validate Granule
#### <a name="validate-granule-endpoint"></a> /providers/&lt;provider-id&gt;/validate/granule/&lt;native-id&gt;

Granule metadata can be validated without having to ingest it. The validation performed is schema validation, UMM validation, and inventory specific validations. It returns status code 200 on successful validation, status code 400 with a list of validation errors on failed validation.

A collection is required when validating the granule. The granule being validated can either refer to an existing collection in the CMR or the collection can be sent in a multi-part HTTP request.

#### Validate Granule Referencing Existing Collection

This shows how to validate a granule that references an existing collection in the database.

```
curl -XPOST \
  -H "Content-type: application/echo10+xml" \
  %CMR-ENDPOINT%/providers/PROV1/validate/granule/sampleGranuleNativeId33 \
  -d \
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

    curl -XPOST \
      -F "granule=<granule.xml;type=application/echo10+xml" \
      -F "collection=<collection.xml;type=application/echo10+xml" \
      "%CMR-ENDPOINT%/providers/PROV1/validate/granule/sampleGranuleNativeId33"

### <a name="create-update-granule"></a> Create / Update a Granule
#### <a name="create-delete-granule-endpoint"></a> /providers/&lt;provider-id&gt;/granules/&lt;native-id&gt;

Granule metadata can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/providers/<provider-id>/granules/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id). Once a granule is created to reference a parent collection, the granule cannot be changed to reference a different collection as its parent collection during granule update.

    curl -XPUT \
      -H "Content-type: application/echo10+xml" \
      -H "Authorization: Bearer XXXX" \
      %CMR-ENDPOINT%/providers/PROV1/granules/sampleGranuleNativeId33 \
      -d \
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

    {"concept-id":"G1200000001-PROV1","revision-id":1}

### <a name="delete-granule"></a> Delete a Granule

Granule metadata can be deleted by sending an HTTP DELETE the URL `%CMR-ENDPOINT%/providers/<provider-id>/granules/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

    curl -XDELETE \
      -H "Authorization: Bearer XXXX" \
      %CMR-ENDPOINT%/providers/PROV1/granules/sampleGranuleNativeId33

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>G1200000001-PROV1</concept-id>
  <revision-id>2</revision-id>
</result>
```

#### Successful Response in JSON

    {"concept-id":"G1200000001-PROV1","revision-id":2}

## <a name="variable"></a> Variable
### <a name="create-update-variable"></a> Create / Update a Variable

Create a UMM-V record and associate that variable to a collection. For associations of other UMM documents, see %CMR-ENDPOINT%/site/docs/search/api.html

#### <a name="create-var-coll-endpoint"></a> /collections/&lt;collection-concept-id&gt;/&lt;collection-revision-id&gt;/variables/&lt;native-id&gt;

A new variable ingest endpoint is provided to ensure that variable association is created at variable ingest time.
Variable concept can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/collections/<collection-concept-id>/<collection-revision-id>/variables/<native-id>`.  `<collection-revision-id>` is optional. The response will include the [concept id](#concept-id),[revision id](#revision-id), variable-association and associated-item.

Variable associations can also have custom data to describe or augment the relationship. CMR makes no use of this extra data, but clients may use the information to derive a meaning from the relationship. The extra "Association Data" can be any valid JSON. When providing Association Data, the API requires that the Variable Metadata and Association Data be sent together, in a JSON wrapper using the same PUT command and URL. The wrapper looks like this:

```
{"content": {
   "MetadataSpecification" : {
   "URL" : "https://cdn.earthdata.nasa.gov/umm/variable/v1.8.1",
   "Name" : "UMM-Var",
   "Version" : "1.8.1"},...},
 "data": {"XYZ": "XYZ", "allow-regridding": true}}
```

**Note**:

1. There is no more fingerprint check at variable's ingest/update time because the existing fingerprint is obsolete. The new variable uniqueness is defined by variable name and the collection it's associated with and is checked at variable association creation time.
2. When using the new variable ingest endpoint to update a variable, the association will be updated too. There can be one and only one association for each variable, with or without collection revision info. This decision is based on the feedback from NSIDC that there is no need for a variable to be associated with multiple revisions of a collection. When a new association info is passed in, the old one will be replaced, when the exact same association info is passed in, a new revision of the old association is created.
3. MeasurementNames must conform to values specified by the KMS list: [Measurement Names](https://gcmd.earthdata.nasa.gov/kms/concepts/concept_scheme/MeasurementName?format=csv).

Only Variable, no Data:

```
curl -XPUT \
  -H "Content-type: application/vnd.nasa.cmr.umm+json" \
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/collections/C1200000005-PROV1/1/variables/sampleVariableNativeId33 \
  -d \
"{
  \"AdditionalIdentifiers\" : [ {
    \"Identifier\" : \"air_temp\"
  }, {
    \"Description\" : \"AIRS_name\",
    \"Identifier\" : \"TAirSup\"
  } ],
  \"VariableType\" : \"SCIENCE_VARIABLE\",
  \"DataType\" : \"float32\",
  \"StandardName\" : \"air_temperature\",
  \"FillValues\" : [ {
    \"Type\" : \"SCIENCE_FILLVALUE\",
    \"Value\" : 9.969209968E36
  } ],
  \"Dimensions\" : [ {
    \"Name\" : \"atrack\",
    \"Size\" : 45,
    \"Type\" : \"ALONG_TRACK_DIMENSION\"
  }, {
    \"Name\" : \"xtrack\",
    \"Size\" : 30,
    \"Type\" : \"CROSS_TRACK_DIMENSION\"
  }, {
    \"Name\" : \"air_pres\",
    \"Size\" : 100,
    \"Type\" : \"PRESSURE_DIMENSION\"
  } ],
  \"Definition\" : \"Air temperature profile from SNDRSNIML2CCPRET_2\",
  \"Name\" : \"/air_temp\",
  \"ValidRanges\" : [ {
    \"Max\" : 400,
    \"Min\" : 100
  } ],
  \"MetadataSpecification\" : {
    \"Name\" : \"UMM-Var\",
    \"URL\" : \"https://cdn.earthdata.nasa.gov/umm/variable/v1.8.1\",
    \"Version\" : \"1.8.1\"
  },
  \"Units\" : \"Kelvin\",
  \"LongName\" : \"Air temperature profile\"
}"
```

Both Variable and Data:

```
curl -XPUT \
  -H "Content-type: application/vnd.nasa.cmr.umm+json" \
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/collections/C1200000005-PROV1/1/variables/sampleVariableNativeId33 \
  -d \
"{\"content\":
    {
      \"AdditionalIdentifiers\" : [ {
        \"Identifier\" : \"air_temp\"
      }, {
        \"Description\" : \"AIRS_name\",
        \"Identifier\" : \"TAirSup\"
      } ],
    \"VariableType\" : \"SCIENCE_VARIABLE\",
    \"DataType\" : \"float32\",
    \"StandardName\" : \"air_temperature\",
    \"FillValues\" : [ {
      \"Type\" : \"SCIENCE_FILLVALUE\",
      \"Value\" : 9.969209968E36
    } ],
    \"Dimensions\" : [ {
      \"Name\" : \"atrack\",
      \"Size\" : 45,
      \"Type\" : \"ALONG_TRACK_DIMENSION\"
    }, {
      \"Name\" : \"xtrack\",
      \"Size\" : 30,
      \"Type\" : \"CROSS_TRACK_DIMENSION\"
    }, {
      \"Name\" : \"air_pres\",
      \"Size\" : 100,
      \"Type\" : \"PRESSURE_DIMENSION\"
    } ],
    \"Definition\" : \"Air temperature profile from SNDRSNIML2CCPRET_2\",
    \"Name\" : \"/air_temp\",
    \"ValidRanges\" : [ {
      \"Max\" : 400,
      \"Min\" : 100
    } ],
    \"MetadataSpecification\" : {
      \"Name\" : \"UMM-Var\",
      \"URL\" : \"https://cdn.earthdata.nasa.gov/umm/variable/v1.8.1\",
      \"Version\" : \"1.8.1\"
    },
    \"Units\" : \"Kelvin\",
    \"LongName\" : \"Air temperature profile\"
  },
	\"data\": {\"XYZ\": \"XYZ\", \"allow-regridding\": true}}"
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

#### <a name="variable-endpoint"></a> /providers/&lt;provider-id&gt;/variables/&lt;native-id&gt;

```
curl -XPUT \
  -H "Content-type: application/vnd.nasa.cmr.umm+json" \
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/providers/PROV1/variables/sampleVariableNativeId33 \
  -d \
"{
  \"AdditionalIdentifiers\" : [ {
    \"Identifier\" : \"air_temp\"
  }, {
    \"Description\" : \"AIRS_name\",
    \"Identifier\" : \"TAirSup\"
  } ],
  \"VariableType\" : \"SCIENCE_VARIABLE\",
  \"DataType\" : \"float32\",
  \"StandardName\" : \"air_temperature\",
  \"FillValues\" : [ {
    \"Type\" : \"SCIENCE_FILLVALUE\",
    \"Value\" : 9.969209968E36
  } ],
  \"Dimensions\" : [ {
    \"Name\" : \"atrack\",
    \"Size\" : 45,
    \"Type\" : \"ALONG_TRACK_DIMENSION\"
  }, {
    \"Name\" : \"xtrack\",
    \"Size\" : 30,
    \"Type\" : \"CROSS_TRACK_DIMENSION\"
  }, {
    \"Name\" : \"air_pres\",
    \"Size\" : 100,
    \"Type\" : \"PRESSURE_DIMENSION\"
  } ],
  \"Definition\" : \"Air temperature profile from SNDRSNIML2CCPRET_2\",
  \"Name\" : \"/air_temp\",
  \"ValidRanges\" : [ {
    \"Max\" : 400,
    \"Min\" : 100
  } ],
  \"MetadataSpecification\" : {
    \"Name\" : \"UMM-Var\",
    \"URL\" : \"https://cdn.earthdata.nasa.gov/umm/variable/v1.8.1\",
    \"Version\" : \"1.8.1\"
  },
  \"Units\" : \"Kelvin\",
  \"LongName\" : \"Air temperature profile\"
}"
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

    {"concept-id":"V1200000012-PROV1","revision-id":1}

### <a name="delete-variable"></a> Delete a Variable

Variable concept can be deleted by sending an HTTP DELETE the URL `%CMR-ENDPOINT%/providers/<provider-id>/variables/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

```
curl -XDELETE \
  -H "Authorization: Bearer XXXX" \
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

    {"concept-id":"V1200000012-PROV1","revision-id":2}
## <a name="service"></a> Service

#### <a name="service-endpoint"></a> /providers/&lt;provider-id&gt;/services/&lt;native-id&gt;
### <a name="create-update-service"></a> Create / Update a Service

Service concept can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/providers/<provider-id>/services/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id).

```
curl -XPUT \
  -H "Content-type: application/vnd.nasa.cmr.umm+json" \
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/providers/PROV1/services/service123 \
  -d \
"{
  \"Name\": \"GESDISC_HL2SS\",
  \"LongName\": \"Harmony Level 2 Subsetting Service (HL2SS) for GES DISC\",
  \"Version\": \"1.3.1\",
  \"Type\": \"Harmony\",
  \"Description\": \"Endpoint for subsetting L2 Subsetter via Harmony\",
  \"URL\": {
    \"Description\": \"PROJECT HOME PAGE\",
    \"URLValue\": \"https://harmony.earthdata.nasa.gov\"
  },
  \"ServiceKeywords\": [
    {
      \"ServiceCategory\": \"EARTH SCIENCE SERVICES\",
      \"ServiceTopic\": \"DATA MANAGEMENT/DATA HANDLING\",
      \"ServiceTerm\": \"SUBSETTING/SUPERSETTING\"
    }
  ],
  \"ServiceOrganizations\": [
    {
      \"Roles\": [
        \"ORIGINATOR\"
      ],
      \"ShortName\": \"NASA/JPL/PODAAC\",
      \"LongName\": \"Physical Oceanography Distributed Active Archive Center, Jet Propulsion Laboratory, NASA\"
    }
  ],
  \"MetadataSpecification\": {
    \"URL\": \"https://cdn.earthdata.nasa.gov/umm/service/v1.5.0\",
    \"Name\": \"UMM-S\",
    \"Version\": \"1.5.0\"
  }
}"

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

    {"concept-id":"S1200000015-PROV1","revision-id":1}

### <a name="delete-service"></a> Delete a Service

Service metadata can be deleted by sending an HTTP DELETE to the URL `%CMR-ENDPOINT%/providers/<provider-id>/services/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

```
curl -XDELETE \
  -H "Authorization: Bearer XXXX" \
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

    {"concept-id":"S1200000015-PROV1","revision-id":2}
## <a name="tool"></a> Tool

#### <a name="tool-endpoint"></a> /providers/&lt;provider-id&gt;/tools/&lt;native-id&gt;
### <a name="create-update-tool"></a> Create / Update a Tool

Tool concept can be created or updated by sending an HTTP PUT with the metadata to the URL `%CMR-ENDPOINT%/providers/<provider-id>/tools/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id).

```
curl -XPUT \
  -H "Content-type: application/vnd.nasa.cmr.umm+json" \
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/providers/PROV1/tools/tool123 \
  -d \
"{
  \"Name\": \"GESDISC_HL2SS\",
  \"LongName\": \"Harmony Level 2 Subsetting Service (HL2SS) for GES DISC\",
  \"Version\": \"1.3.1\",
  \"Type\": \"Harmony\",
  \"Description\": \"Endpoint for subsetting L2 Subsetter via Harmony\",
  \"URL\": {
    \"Description\": \"PROJECT HOME PAGE\",
    \"URLValue\": \"https://harmony.earthdata.nasa.gov\"
  },
  \"ServiceKeywords\": [
    {
      \"ServiceCategory\": \"EARTH SCIENCE SERVICES\",
      \"ServiceTopic\": \"DATA MANAGEMENT/DATA HANDLING\",
      \"ServiceTerm\": \"SUBSETTING/SUPERSETTING\"
    }
  ],
  \"ServiceOrganizations\": [
    {
      \"Roles\": [
        \"ORIGINATOR\"
      ],
      \"ShortName\": \"NASA/JPL/PODAAC\",
      \"LongName\": \"Physical Oceanography Distributed Active Archive Center, Jet Propulsion Laboratory, NASA\"
    }
  ],
  \"MetadataSpecification\": {
    \"URL\": \"https://cdn.earthdata.nasa.gov/umm/service/v1.5.0\",
    \"Name\": \"UMM-S\",
    \"Version\": \"1.5.0\"
  }
}"
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

    {"concept-id":"TL1200000015-PROV1","revision-id":1}

### <a name="delete-tool"></a> Delete a Tool

Tool metadata can be deleted by sending an HTTP DELETE to the URL `%CMR-ENDPOINT%/providers/<provider-id>/tools/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

	curl -XDELETE \
  		-H "Authorization: Bearer XXXX" \
  		%CMR-ENDPOINT%/providers/PROV1/tools/tool123

#### Successful Response in XML

```
<?xml version="1.0" encoding="UTF-8"?>
<result>
  <concept-id>TL1200000015-PROV1</concept-id>
  <revision-id>2</revision-id>
</result>
```

#### Successful Response in JSON

    {"concept-id":"TL1200000015-PROV1","revision-id":2}
## <a name="subscription"></a> Subscription
### <a name="create-subscription"></a> Create a Subscription
#### <a name="subscription-endpoint"></a> /subscriptions

NOTE: The `%CMR-ENDPOINT%/providers/<provider-id>/subscriptions` API routes for subscription ingest are deprecated. Please switch to the new `%CMR-ENDPOINT%/subscriptions` API routes. All the examples below are using the new routes.

Subscription allows a user to register some query conditions in CMR and be notified via email when collections/granules matching the conditions are created or updated in CMR. There are two types of subscriptions (identified by the `Type` field of the subscription):

- collection subscription for users to be notified when collections are created/updated.
- granule subscription for users to be notified when granules are created/updated.

Subscription metadata is in JSON format and conforms to [UMM-Sub Schema](https://git.earthdata.nasa.gov/projects/EMFD/repos/unified-metadata-model/browse/subscription). There is a background job that processes the subscriptions periodically (configurable), to see if there are any collections/granules that are created/updated since the last time the subscription has been processed and notify the subscription user with any matches.

Subscription concepts can be created by sending an HTTP POST or PUT with the metadata sent as data to the URL `%CMR-ENDPOINT%/subscriptions/<native-id>`. The response will include the [concept id](#concept-id) ,the [revision id](#revision-id), and a [native-id](#native-id).

`Type` is a required field in subscription request body. The valid values of `Type` are: `"collection"` or `"granule"`. It indicates if the subscription is a collection subscription or granule subscription. Subscriptions of type granule must supply a requisite CollectionConceptId, and subscriptions of type collection cannot have a CollectionConceptId field.

If a native-id is not provided it will be generated. This is only supported for POST requests.
POST requests may only be used for creating subscriptions.

If a SubscriberId is not provided, then the user ID associated with the token used to ingest the subscription will be used as the SubscriberId.

EmailAddress was previously a required field, but this field is now deprecated. Instead, the email address associated with the SubscriberId's EarthData Login (URS) account will be used as the EmailAddress. If an EmailAddress is specified at subscription creation it will be ignored.

POST only may be used without a native-id at the following URL.
`%CMR-ENDPOINT%/subscriptions`

POST or PUT may be used with the following URL.
`%CMR-ENDPOINT%/subscriptions/<native-id>`

Query values should not be URL encoded. Instead, the query should consist of standard granule search parameters, separated by '&'. For example, a valid query string might look like:

    instrument=MODIS&sensor=1B&polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78

If the query provided is invalid for granule searching, subscription creation will fail with HTTP status response of 400, and an error message detailing which query parameters were invalid.

### <a name="update-subscription"></a> Update a Subscription
#### <a name="subscription-native-id-endpoint"></a> /subscriptions/&lt;native-id&gt;
Subscription concept can be updated by sending an HTTP POST or PUT with the metadata sent as data to the URL `%CMR-ENDPOINT%/subscriptions/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id).

If a native-id is provided in a POST, and a subscription already exists for that provider with the given native-id, the request will be rejected.

PUT requests should be used for updating subscriptions. Creation of subscriptions using PUT may be deprecated in the future. All PUT requests require a native-id to be part of the request URL.

```
curl -XPUT \
  -H "Content-type: application/vnd.nasa.cmr.umm+json" \
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/subscriptions/subscription123 \
  -d \
"{\"Name\": \"someSubscription\",
  \"SubscriberId\": \"someSubscriberId\",
  \"CollectionConceptId\": \"C1234-PROV1.\",
  \"Query\": \"polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78\"}"
```

```
curl -XPOST \
  -H "Content-type: application/vnd.nasa.cmr.umm+json" \
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/subscriptions \
  -d \
"{\"Name\": \"someSubscription\",
  \"SubscriberId\": \"someSubscriberId\",
  \"CollectionConceptId\": \"C1234-PROV1.\",
  \"Query\": \"polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78\"}"
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

    {"concept-id":"SUB1200000015-PROV1","revision-id":1,"native-id":"subscription123"}

### <a name="delete-subscription"></a> Delete a Subscription

Subscription metadata can be deleted by sending an HTTP DELETE to the URL `%CMR-ENDPOINT%/subscriptions/<native-id>`. The response will include the [concept id](#concept-id) and the [revision id](#revision-id) of the tombstone.

```
curl -XDELETE \
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/subscriptions/subscription123
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

    {"concept-id":"SUB1200000015-PROV1","revision-id":2}

### <a name="subscription-access-control"></a> Subscription Access Control

Ingest permissions for granule subscriptions are granted through the provider via the INGEST_MANAGEMENT_ACL and SUBSCRIPTION_MANAGEMENT. In order to ingest/update/delete a subscription for a given provider, update permission has to be granted to the user through both INGEST_MANAGEMENT_ACL and SUBSCRIPTION_MANAGEMENT ACLs for the provider.

For lack of a better ACL, ingest permissions for collection subscription are granted through the SYSTEM OBJECT TAG_GROUP ACL update permission.

%GENERIC-DOCS%
## <a name="translate-collection"></a> Translate Collection Metadata
#### <a name="translate-collection-endpoint"></a> /translate/collection

Collection metadata can be translated between metadata standards using the translate API in Ingest. This API also supports the UMM JSON format which represents UMM as JSON. The request specifies the metadata standard being sent using the Content-Type header. Metadata is sent inside the body of the request. The output format is specified via the Accept header.

To disable validation of the parsed UMM metadata against the current UMM spec, pass `skip_umm_validation=true` as a query parameter.

Example: Translate an ECHO10 metadata to UMM JSON version 1.16.2

```
curl -XPOST \
  -H "Content-Type: application/echo10+xml" \
  -H "Accept: application/vnd.nasa.cmr.umm+json;version=1.16.2" \
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
#### <a name="translate-granule-endpoint"></a> /translate/granule

Granule metadata can be translated between metadata standards using the translate API in Ingest. The request specifies the metadata standard being sent using the Content-Type header. Metadata is sent inside the body of the request. The output format is specified via the Accept header. The supported input formats are ECHO10, ISO SMAP and UMM-G. The supported output formats are ECHO10, ISO SMAP, UMM-G and ISO19115.

Example: Translate ECHO10 metadata to UMM-G

```
curl -XPOST \
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
#### <a name="bulk-update-collection-endpoint"></a> /providers/&lt;provider-id&gt;/bulk-update/collections

The collection bulk update API is used perform the same collection update to multiple concepts in one call.

Bulk update is initiated through an ingest POST endpoint with the concept ids to update, the update type, the update field, and update information. The metadata is converted to the latest version of UMM, if not the native format, updated according to the parameters, and saved as the latest version of UMM-JSON, **making the native format of the collection now UMM-JSON**. Previous revisions of the collection are retained in the original native format. In the UMM-JSON metadata, the Metadata Date of type "UPDATE" will be set to the current date. Please note that when we apply bulk update on a collection, regardless if there are actual changes, a new revision is created.

Updated collections are validated using business rule validations. Updates will not be saved if the business validations fail. The error will be recorded in the individual collection status, which can be queried via the status endpoint. Collection validation warnings will not prevent saving the updated collection and the warnings will be recorded in the individual collection status.

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

Update types that include a FIND will match on the fields supplied in the find value. For example, for a science keyword update with a find value of `{"Category": "EARTH SCIENCE"}`, any science keyword with a category of "EARTH SCIENCE" will be considered a match regardless of the values of the science keyword topic, term, etc. It's worth noting that find value can not contain nested fields. So for bulk update on PLATFORMS, for example, find value can only contain Type, ShortName and LongName, not the nested fields like Characteristics and Instruments. On the other hand, update value can contain all the valid fields including the nested fields. So, nested fields can be updated, they just can't be used to find the matches.

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
curl -XPOST \
  -H "Cmr-Pretty:true" \
  -H "Content-Type: application/json"
  -H "Authorization: Bearer XXXX" %CMR-ENDPOINT%/providers/PROV1/bulk-update/collections \
  -d
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
The list is ordered by task id, in descending order so that the newest update will show up on the top.

Example

```
curl \
  -H "Authorization: Bearer XXXX" \
  -H "Cmr-Pretty:true" \
  %CMR-ENDPOINT%/providers/PROV1/bulk-update/collections/status

<?xml version="1.0" encoding="UTF-8"?>
<result>
    <tasks>
        <task>
            <created-at>2017-10-24T17:00:03.000Z</created-at>
            <name>TEST NAME1</name>
            <task-id>3</task-id>
            <status>COMPLETE</status>
            <status-message>Task completed with 1 FAILED and 4 UPDATED out of 5 total collection update(s).</status-message>
            <request-json-body>{"concept-ids": ["C12807-PROV1","C17995-PROV1","C18002-PROV1","C18016-PROV1"],"update-type": "FIND_AND_REMOVE","update-field": "SCIENCE_KEYWORDS","find-value": {"Category": "EARTH SCIENCE","Topic": "HUMAN DIMENSIONS","Term": "ENVIRONMENTAL IMPACTS","VariableLevel1": "HEAVY METALS CONCENTRATION"}}</request-json-body>
        </task>
        <task>
            <created-at>2017-10-24T17:00:03.000Z</created-at>
            <name>TEST NAME2</name>
            <task-id>2</task-id>
            <status>COMPLETE</status>
            <status-message>Task completed with 1 FAILED and 2 UPDATED out of 3 total collection update(s).</status-message>
            <request-json-body>{"concept-ids": ["C13239-PROV1","C13276-PROV1","C13883-PROV1","C13286-PROV1"],"update-type": "CLEAR_ALL_AND_REPLACE","update-field": "SCIENCE_KEYWORDS","update-value": {"Category": "EARTH SCIENCE","Topic": "HUMAN DIMENSIONS","Term": "ENVIRONMENTAL IMPACTS","VariableLevel1": "HEAVY METALS CONCENTRATION"}}</request-json-body>
        </task>
        <task>
            <created-at>2017-10-24T17:00:03.000Z</created-at>
            <name>TEST NAME3</name>
            <task-id>1</task-id>
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
curl \
  -H "Authorization: Bearer XXXX" \
  -H "Cmr-Pretty:true" \
  %CMR-ENDPOINT%/providers/PROV1/bulk-update/collections/status/25

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
#### <a name="bulk-update-granule-endpoint"></a> /providers/&lt;provider-id&gt;/bulk-update/granules
The granule bulk update API is used perform the same granule update to multiple granule concepts in one call.

Granule bulk update is initiated through a POST to the ingest endpoint with the bulk update operation, the update field, and the updates, which is a list of granule URs and update values in the request body. See the [Granule Bulk Update JSON Schema](https://github.com/nasa/Common-Metadata-Repository/blob/master/ingest-app/resources/granule_bulk_update_schema.json) for the detailed format of granule bulk update request.

Updated granules are validated using business rule validations. Updates will not be saved if the business validations fail. The error will be recorded in the individual granule status, which can be queried via the status endpoint.

Granule Bulk Update speed is hardware dependent, but can typically update granules at a rate of 2,000 granules per minute in production. This value will fluctuate based on overall system load. Updates are processed on a first-come-first-serve basis, which could delay the granule updates in a given task from starting immediately when submitted.

There is no hard limit on the number of granules which can be included in a single request, but the JSON patch file provided with a request should be no larger than `20MB`. As a result, the length of granule URs in a given patch file, as well as the length and volume of links provided for each granule will dictate how many granules can be submitted in a single request.

If the number of granules in need of update update exceeds 250,000, we ask that you get in touch with the CMR team to schedule the pacing of these requests.

Granule bulk update currently supports updating with the following operations, update fields and metadata formats:

**operation: "UPDATE_FIELD", update-field: "OPeNDAPLink"**
Supported metadata formats:

* OPeNDAP url in OnlineResources for ECHO10 format
* OPeNDAP url in RelatedUrls for UMM-G format

There can only be ONE on-prem (on-premis) and/or ONE Hyrax-in-the-cloud OPeNDAP url in the granule metadata. The rule to determine if an OPeNDAP url is an on-prem or Hyrax-in-the-cloud url is to match the URL against this pattern: https://opendap.*.earthdata.nasa.gov/*. If the url matches, it is a Hyrax-in-the-cloud OPeNDAP url; if it does not match, it is an on-prem OPeNDAP url.
The OPeNDAP url value provided in the granule bulk update request can be comma-separated urls, but it can have two at most: one is an on-prem url and the other is a Hyrax-in-the-cloud url. The exact url type is determined by matching the url against the same pattern above. During an update, the Hyrax-in-the-cloud url will overwrite any existing Hyrax-in-the-cloud OPeNDAP url in the granule metadata, and the on-prem url will overwrite any existing on-prem OPeNDAP url in the granule metadata.

**operation: "UPDATE_FIELD", update-field: "S3Link"**
Supported metadata formats:

* S3 url in OnlineAccessURLs for ECHO10 format
* S3 url in RelatedUrls for UMM-G format

The S3 url value provided in the granule bulk update request can be comma-separated urls. Each url must start with s3:// (case-sensitive). This lowercase s3:// naming convention is to make the s3 links compatible with AWS S3 API. During bulk update, the provided S3 urls in the request will overwrite any existing S3 links already in the granule metadata.

Example: Add/update OPeNDAP url for 3 granules under PROV1.

```
curl -XPOST \
  -H "Cmr-Pretty:true" \
  -H "Content-Type: application/json"
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/providers/PROV1/bulk-update/granules \
  -d
'{ "name": "example of adding OPeNDAP link",
	"operation": "UPDATE_FIELD",
	"update-field":"OPeNDAPLink",
	"updates":[
             ["granule_ur1", "https://via.placeholder.com/150"],
             ["granule_ur2", "https://via.placeholder.com/160"],
             ["granule_ur3", "https://via.placeholder.com/170,https://opendap.earthdata.nasa.gov/foo"]
	]
}'
```

Example granule bulk update response:
```
<?xml version="1.0" encoding="UTF-8"?>
<result>
    <status>200</status>
    <task-id>5</task-id>
</result>
```

**operation: "UPDATE_FIELD", update-field: "Checksum"**
Supported metadata formats:
  - Checksum in <DataGranule> element for ECHO10 format

An `algorithm` can optionally be supplied with the new checksum `value` by specifying two values, comma-separated (`value,algorithm`). If an update is requested for a granule with no existing `<Checksum>` element, then specifying an `algorithm` is required. Any values beyond the first two for a given granule are ignored.

Example: Add/update checksum for 3 granules under PROV1. Granules 1 and 2 only receive checksum `value` updates, while granule 3 receives an update to checksum `value` *and* `algorithm`.

```
curl -XPOST \
  -H "Cmr-Pretty:true" \
  -H "Content-Type: application/json"
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/providers/PROV1/bulk-update/granules \
  -d
'{ "name": "example of updating granule checksums",
	"operation": "UPDATE_FIELD",
	"update-field":"Checksum",
	"updates":[
             ["granule_ur1", "92959a96fd69146c5fe7cbde6e5720f2"],
             ["granule_ur2", "925a89b43f3caff507db0a86d20a2428007"],
             ["granule_ur3", "a3dcb4d229de6fde0db5686dee47145d,SHA-256"]
	]
}'
```

Example granule bulk update response:
```
<?xml version="1.0" encoding="UTF-8"?>
<result>
    <status>200</status>
    <task-id>5</task-id>
</result>
```

**operation: "UPDATE_FIELD", update-field: "Size"**
Supported metadata formats:
  - <DataGranuleSizeInBytes> and <SizeMBDataGranule> inside <DataGranule> element for ECHO10 format

To update DataGranuleSizeInBytes, input an integer value, such as `22`. To update SizeMBDataGranule, input a double (decimal) value, such as `52.235`. If a file has an flat number value, such as exactly `25MB`, this should be input as `25.0`. Both values can be updated at once by supplying two values, comma seperated, as seen below. If more than one integer value, more than one double value, or any extraneous values are supplied, the granule update will fail.

Example: Add/update size values for 3 granules under PROV1. Granules 1 receives an update to `DataGranuleSizeInBytes`, granule 2 receives an update to `SizeMBDataGranule`, and granule 3 receives an update to both values.

```
curl -XPOST \
  -H "Cmr-Pretty:true" \
  -H "Content-Type: application/json"
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/providers/PROV1/bulk-update/granules \
  -d
'{ "name": "Example of updating sizes",
	"operation": "UPDATE_FIELD",
	"update-field":"Size",
	"updates":[
             ["granule_ur1", "156"],
             ["granule_ur2", "10.0"],
             ["granule_ur3", "8.675306,8675309"]
	]
}'
```

Example granule bulk update response:
```
<?xml version="1.0" encoding="UTF-8"?>
<result>
    <status>200</status>
    <task-id>5</task-id>
</result>
```

**operation: "UPDATE_FIELD", update-field: "Format"**
Supported metadata formats:
  - <DataFormat> element in ECHO10 format

To update DataFormat, simply supply a new string value - the example below shows three granules requested for a Format update:

```
curl -XPOST \
  -H "Cmr-Pretty:true" \
  -H "Content-Type: application/json"
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/providers/PROV1/bulk-update/granules \
  -d
'{ "name": "Example of updating format",
	"operation": "UPDATE_FIELD",
	"update-field":"Size",
	"updates":[
             ["granule_ur1", "HDF-EOS5"],
             ["granule_ur2", "ZIP"],
             ["granule_ur3", "netCDF"]
	]
}'
```

Example granule bulk update response:
```
<?xml version="1.0" encoding="UTF-8"?>
<result>
    <status>200</status>
    <task-id>5</task-id>
</result>
```

**operation: "UPDATE_FIELD", update-field: "MimeType"**
Supported metadata formats:
  - RelatedUrls in UMM-G
  - OnlineAccessURLs and OnlineResources in ECHO10

To update the MimeType value for RelatedUrls, an array of URLs and MimeTypes can be specified for each granule to specify the new MimeType for each RelatedUrl.

In ECHO10, MimeType for either OnlineResource or OnlineAccessURL links can be updated using the update syntax as for UMM-G..

```
curl -XPOST \
  -H "Cmr-Pretty:true" \
  -H "Content-Type: application/json"
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/providers/PROV1/bulk-update/granules \
  -d
'{ "name": "Example of updating RelatedUrl MimeTypes",
	"operation": "UPDATE_FIELD",
	"update-field":"MimeType",
	"updates":[{
		"GranuleUR": "Gran_With_Links_1"
		"Links": [
			{
				"URL": "www.example.com/1"
			 "MimeType": application/json
		 	},
			{
				"URL": "www.example.com/2"
			 "MimeType": application/xml
		 	}
		]
	},
	{
		"GranuleUR": "Gran_With_Links_2"
		"Links": [
			{
				"URL": "www.example.com/myimportantlink"
			 "MimeType": application/zip
		 	},
			{
				"URL": "www.example.com/myveryimportanlink"
			 "MimeType": application/tar
		 	}
		]
	}]
}'
```

**operation: "UPDATE_FIELD", update-field: "AdditionalFile"**
Supported metadata formats:
  - UMM-G File and FilePackage elements located under DataGranule/ArchiveAndDistributionInformation.

This update type can be used to update any of the values in a [File or FilePackage](https://git.earthdata.nasa.gov/projects/EMFD/repos/unified-metadata-model/browse/granule/v1.6.3/umm-g-json-schema.json#259) in the UMM-G schema. This includes:
	- Size and SizeUnit
	- SizeInBytes
	- Format, FormatType, and MimeType
	- Checksum (Value and Algorithm)

All values specified must conform to what is allowed by the UMM-G schema for any given field.

This type of Bulk Granule Updates has a unique format for its `updates` - for each granule, an array of Files can be specified, and each File can contain any combination of the elements above. The full schema can be found [here](https://github.com/nasa/Common-Metadata-Repository/blob/master/ingest-app/resources/granule_bulk_update_schema.json), and an example request can be found below:

```
curl -XPOST \
  -H "Cmr-Pretty:true" \
  -H "Content-Type: application/json"
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/providers/PROV1/bulk-update/granules \
  -d
'{
  "name": "Update FilePackages and Files",
  "operation": "UPDATE_FIELD",
  "update-field": "AdditionalFile",
  "updates": [
    {
      "GranuleUR": "Example_Granule_UR_1",
      "Files": [
        {
          "Name": "ZippedFilePackage",
          "SizeInBytes": 12000,
          "Size": 12,
          "SizeUnit": "KB",
          "Format": "ZIP"
        }, {
          "Name": "GranuleFileName1",
          "MimeType": "application/xml",
          "Checksum": {
            "Value": "92959a96fd69146c5fe",
            "Algorithm": "MD5"
          }
        }
      ]
    }, {
      "GranuleUR": "Example_Granule_UR_2",
      "Files": [
        {
          "Name": "GranuleZipFile",
          "SizeInBytes": 12000,
          "Size": 0,
        }
      ]
    }
  ]
}'
```
In the above request, `Example_Granule_UR_1` receives updates for two elements: The FilePackage `ZippedFilePackage` has SizeInBytes, Size/SizeUnit, and Format updated, while File `GranuleFileName1` has MimeType and Checksum updated.

Note that specifying whether an element is a File or FilePackage is unnecessary. Providing the `name` for an element is sufficient to locate and update it.

`Example_Granule_UR_2` also receives updates on the contained `GranuleZipFile`, on its Size/SizeUnit and SizeInBytes fields. This also displays a special use case for Size-related updates: When a file update is requested with Size `0`, then the Size and SizeUnit fields will be removed from the resulting file. The same applies for SizeInBytes, which will be removed on its own if a value of `0` is supplied.

There are several scenarios which will cause a granule update to fail:
 - Files with duplicate names are specified in the request patch file
 - A granule with existing duplicate file names is requested for update
 - A file is provided in the patch file with a file name which is not present in the granule

**operation: "APPEND_TO_FIELD", update-field: "OPeNDAPLink"**
supported metadata formats:

* OPeNDAPLink url in OnlineResources for ECHO10 format
* OPeNDAPLink url in RelatedUrls for UMM-G format

Append operations on OPeNDAPLink will behave as follows:

* OPeNDAP updates may contain a maximum of two URLs, separated by comma
  * At most, only one of each OPeNDAP URL type may be included (on-prem or Hyrax-in-the-cloud)
* If the granule contains no OPeNDAP urls the new URLs will be added
* If the granule does not contain a conflicting URL for the type (on-prem or Hyrax-in-the-cloud), the new value will be added.
* If the granule already contains URLs for both on-prem and cloud, the granule update will fail.

URLs matching the pattern: `https://opendap.*.earthdata.nasa.gov/*` will be determined to be Hyrax-in-the-cloud, otherwise it will be on-prem.

```
curl -XPOST \
  -H "Cmr-Pretty:true" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/providers/PROV1/bulk-update/granules \
  -d
'{ "name": "example of appending OPeNDAP links",
	"operation": "APPEND_TO_FIELD",
	"update-field":"OPeNDAPLink",
	"updates":[
             ["granule_ur1", "https://opendap.earthdata.nasa.gov/example"],
             ["granule_ur2", "https://on-prem.example.com"],
             ["granule_ur3", "https://opendap.earthdata.nasa.gov/example-2,https://on-prem.example-2.com"]
	]
}'
```

Example granule bulk update response:

```
{
 "status" : 200,
 "task-id": 6
}
```

**operation: "APPEND_TO_FIELD", update-field: "S3Link"**
supported metadata formats:
  - S3Link url in OnlineResources for ECHO10 format
  - S3Link url in RelatedUrls for UMM-G format

The S3 url value provided in the granule bulk update request can be comma-separated urls. Each url must start with s3:// (case-sensitive). This lowercase s3:// naming convention is to make the s3 links compatible with AWS S3 API. During bulk update, the provided S3 urls in the request will be updated by appending the new S3 links. Existing S3 links will be preserved.

If the URL passed to the update is already associated with the granule, the URL will not be duplicated or updated.

``` bash
curl -XPOST \
  -H "Cmr-Pretty:true" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/providers/PROV1/bulk-update/granules \
  -d
'{ "name": "example of appending S3 links",
	"operation": "APPEND_TO_FIELD",
	"update-field":"S3Link",
	"updates":[
             ["granule_ur1", "s3://example.com/bucket1"],
             ["granule_ur2", "s3://example.com/bucket2"],
             ["granule_ur3", "s3://example.com/bucket3-east,s3://example.com/bucket3-west"]
	]
}'
```

Example granule bulk update response:
```
{
 "status" : 200,
 "task-id": 4
}
```

**operation: "UPDATE_TYPE", update-field: "OPeNDAPLink"**
Supported metadata formats:

* OPeNDAP url in RelatedUrls for UMM-G format
* OPeNDAP url in OnlineResources for ECHO10 format

Input for this update type should be a list of granule URs. UMM-G Granules listed will have any `RelatedUrl`s with a *URL* containing the string `"opendap"` updated to include `"Type": "USE SERVICE API"` and `"Subtype": "OPENDAP DATA"`. Echo10 Granules will have any `OnlineResources` with a *type* containing `"opendap"` updated to to include `<Type>USE SERVICE API : OPENDAP DATA</Type>`.

As an alternative to identifying links via the `"opendap"` string method, a type string can be supplied with each granule UR as a tuple. If supplied, any UMM-G links with a *subtype* matching this input string will be updated instead. As before, the link will be updated to include `"Type": "USE SERVICE API"` and `"Subtype": "OPENDAP DATA"`. Any Echo10 links with a *type* matching this input will have their major typing updated to `USE SERVICE API`.

Examples for each update format are provided below. For the first update, each granule in the list will have any links containing the string `"opendap"` updated to the new Type and Subtype.

```
curl -XPOST \
  -H "Cmr-Pretty:true" \
  -H "Content-Type: application/json"
  -H "Authorization: XXXX" \
  %CMR-ENDPOINT%/providers/PROV1/bulk-update/granules \
  -d
'{ "name": "Update type and subtype for links containing the string 'opendap'",
	"operation": "UPDATE_TYPE",
	"update-field":"OPeNDAPLink",
	"updates":[
             "granule_ur1", "granule_ur2", "granule_ur3"
	]
}'
```

For this next update, each granule in the list will have any links with a subtype matching the supplied value updated to the new Type and Subtype

```
curl -XPOST \
  -H "Cmr-Pretty:true" \
  -H "Content-Type: application/json"
  -H "Authorization: XXXX" \
  %CMR-ENDPOINT%/providers/PROV1/bulk-update/granules \
  -d
'{ "name": "Update type and subtype for links containing a subtype matching the supplied value",
	"operation": "UPDATE_TYPE",
	"update-field":"OPeNDAPLink",
	"updates":[
             ["granule_ur1", "OPENDAP DATA"]
			 ["granule_ur2", "OPENDAP DATA"]
			 ["granule_ur3", "DIRECT DOWNLOAD"]
	]
}'
```

**operation: "UPDATE_FIELD", update-field: "OnlineResourceURL"**
supported metadata formats:
  - OnlineResource url in OnlineResources for ECHO10 format

This update operation will replace the URL value of an OnlineResource element with the OnlineResources of a granule. Multiple URLs may be updated on the same granule.
The original value to be replaced and a valid replacement link must be provided.

``` bash
curl -XPOST \
  -H "Cmr-Pretty: true" \
  -H "Content-Type: application/json" \
  -H "Authorization: XXXX" \
  %CMR-ENDPOINT%/providers/PROV1/bulk-update/granules \
  -d
'{
    "name": "Update OnlineResource URL values",
    "operation": "UPDATE_FIELD",
    "update-field": "OnlineResourceURL",
    "updates": [
        {
            "GranuleUR": "my-gran-ur",
            "Links": [
                { "from": "https://old-link", "to": "http://new-link" }
            ]
        }
    ]
}'
```
### <a name="bulk-update-granules-status"></a> Query Granule Bulk Update Status
#### <a name="bulk-update-granules-status-endpoint"></a> /granule-bulk-update/status

The task information of all granule bulk update tasks that has been applied on a provider can be retrieved by sending an HTTP GET request to `%CMR-ENDPOINT%/providers/<provider-id>/bulk-update/granules/status`

This returns a list of: name, task id, created-at, status (IN_PROGRESS or COMPLETE), a status message, and the original request JSON body.
The list is ordered by task id, in descending order so that the newest update will show up on the top.

The supported response formats are application/xml and application/json. The default is application/xml.

Example:

```
curl \
  -H "Authorization: Bearer XXXX" \
  -H "Cmr-Pretty:true" \
  %CMR-ENDPOINT%/providers/PROV1/bulk-update/granules/status

<?xml version="1.0" encoding="UTF-8"?>
<result>
    <tasks>
        <task>
            <created-at>2021-03-12T20:38:53.473Z</created-at>
            <name>add opendap links: 3</name>
            <task-id>3</task-id>
            <status>COMPLETE</status>
            <status-message>All granule updates completed successfully.</status-message>
            <request-json-body>{"name":"add opendap links","operation":"UPDATE_FIELD","update-field":"OPeNDAPLink","updates":[["SC:AE_5DSno.002:30500511","https://url30500511"],["SC:AE_5DSno.002:30500512","https://url30500512"]]}</request-json-body>
        </task>
        <task>
            <created-at>2021-03-12T20:38:53.448Z</created-at>
            <name>add opendap links: 2</name>
            <task-id>2</task-id>
            <status>COMPLETE</status>
            <status-message>All granule updates completed successfully.</status-message>
            <request-json-body>{"name":"add opendap links","operation":"UPDATE_FIELD","update-field":"OPeNDAPLink","updates":[["SC:AE_5DSno.002:30500518","https://url30500518"],["SC:coll2:30500519","https://url30500519"]]}</request-json-body>
        </task>
        <task>
            <created-at>2021-03-12T20:38:53.415Z</created-at>
            <name>add opendap links: 1</name>
            <task-id>1</task-id>
            <status>COMPLETE</status>
            <status-message>Task completed with 1 FAILED and 1 UPDATED out of 2 total granule update(s).</status-message>
            <request-json-body>{"operation":"UPDATE_FIELD","update-field":"OPeNDAPLink","updates":[["SC:coll3:30500514","https://url30500514"],["SC:non-existent","https://url30500515"]]}</request-json-body>
        </task>
    </tasks>
</result>
```

To get a detailed task status for a given granule bulk update task, user can send an HTTP GET request to `%CMR-ENDPOINT%/granule-bulk-update/status/<task-id>`
### <a name="bulk-update-granules-task-id"></a> Query Granule Bulk retrieve status by task-id
#### <a name="bulk-update-granules-task-id-endpoint"></a> /granule-bulk-update/status/&lt;task-id&gt;

This returns the status of the bulk update task including the overall task status (IN_PROGRESS or COMPLETE), the name of the task, and the time the task began processing. Additionally, there are optional query parameters which will increase the verbosity of this response:

`show_progress=true`
When specified, this parameter will return a progress message indicating the number of granules which have been processed out of the total number of granules in the request.

`show_granules=true`
This parameter will return the individual granule status for all granules in the request, which includes the granule-ur and the granule update status (PENDING, UPDATED, SKIPPED, or FAILED). FAILED indicates an error occurred either updating the granule or during granule validation. SKIPPED indicates the update didn't happen because the update operation does not apply to the granule. The error will be reported in the granule status message. Note that this parameter can cause the response to become much larger, scaling with the size of the original bulk update request.

`show_request=true`
This parameter will return the original json body used to initiate the bulk update. Note that this parameter, like before, can cause the response to become much larger, scaling with the size of the original bulk update request.

The only supported response format for granule bulk update task status is application/json.

Example of granule bulk update task status:

```
curl \
  -H "Authorization: Bearer XXXX" \
  -H "Cmr-Pretty:true" \
  %CMR-ENDPOINT%/granule-bulk-update/status/3?show_granules=true&show_request=true
```
```
{
  "status" : 200,
  "created-at" : "2021-03-12T20:38:53.473Z",
  "name" : "3: 3",
  "task-status" : "COMPLETE",
  "status-message" : "Task completed with 1 FAILED and 1 UPDATED out of 2 total granule update(s).",
  "request-json-body" : "{\"operation\":\"UPDATE_FIELD\",\"update-field\":\"OPeNDAPLink\",\"updates\":[[\"SC:coll3:30500514\",\"https://url30500514\"],[\"SC:non-existent\",\"https://url30500515\"]]}",
  "granule-statuses" : [ {
    "granule-ur" : "SC:coll3:30500514",
    "status" : "UPDATED"
  }, {
    "granule-ur" : "SC:non-existent",
    "status" : "FAILED",
    "status-message" : "Granule UR [SC:non-existent] in task-id [3] does not exist."
  } ]
}
```
Granule bulk update tasks and statuses are available for 90 days.

### Refresh Granule Bulk Update Status

By default the bulk granule update jobs are checked for completion every 5 minutes. However granule bulk update task statuses can be refreshed manually, provided the user has the ingest-management permission, with the following command.

```
curl -XPOST \
  -H "Authorization: Bearer XXXX" \
  %CMR-ENDPOINT%/granule-bulk-update/status
