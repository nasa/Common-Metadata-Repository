## API Documentation

See the [CMR Data Partner User Guide](https://wiki.earthdata.nasa.gov/display/CMR/CMR+Data+Partner+User+Guide) for a general guide to utilizing the CMR Ingest API as a data partner.
See the [CMR Client Partner User Guide](https://wiki.earthdata.nasa.gov/display/CMR/CMR+Client+Partner+User+Guide) for a general guide to developing a CMR client.

### Access Control API Overview

  * [/groups](#groups)
    * [POST - Create Group](#create-group)
    * [GET - Search Groups](#search-groups)
  * /groups/:group-id
    * [GET - Retrieve a group](#retrieve-group)
    * [PUT - Update a group](#update-group)
    * [DELETE - Delete a group](#delete-group)
  * /groups/:group-id/members
    * [GET - Retrieve group members] (#retrieve-group-members)
    * [POST - Add group members] (#add-group-members)
    * [DELETE - Remove group members] (#remove-group-members)
  * [/acls](#acls)
    * [POST - Create ACL](#create-acl)
    * [GET - Search ACLs](#search-acls)
  * /acls/:acl-id
    * [GET - Retrieve an ACL](#retrieve-acl)
    * [PUT - Update an ACL](#update-acl)
    * [DELETE - Delete an ACL](#delete-acl)
  * /permissions
    * [GET - Check User Permissions](#get-permissions)
    * [POST - Check User Permissions](#get-permissions)
  * /s3-buckets
    * [GET - Check User Permissions to S3 Buckets](#get-s3-buckets)
  * /health
    * [GET - Get the health of the access control application.](#application-health)

***

## <a name="api-conventions"></a> API Conventions

This defines conventions used across the Access Control API.

### <a name="headers"></a> Headers

This defines common headers on the Access Control API.

#### <a name="content-type-header"></a> Content-Type Header

Content-Type is a standard HTTP header that specifies the content type of the body of the request. Access Control only supports `application/json` as the representation type for data.

#### <a name="authorization-header"></a> Authorization Header

All Access Control API operations require specifying a token obtained from [Earthdata Login (EDL)](https://urs.earthdata.nasa.gov). The token should be specified using the `Authorization: Bearer` header followed by the EDL bearer token. For more information on obtaining an EDL bearer token, please reference the documentation [here](https://urs.earthdata.nasa.gov/documentation/for_users/user_token).

#### <a name="cmr-revision-id-header"></a> Cmr-Revision-Id Header

The revision id header allows specifying the revision id to use when saving the concept. It is optional for all ACL updates. The update will be rejected when:
1. The revision id specified is not an integer.
2. The revision id specified <= current revision id of the acl - a HTTP Status code of 409 will be returned indicating a conflict.

***

### <a name="responses"></a> Responses

### <a name="response-headers"></a> Response Headers

#### <a name="CMR-Request-Id-header"></a> cmr-request-id

This header returns the unique id assigned to the request. This can be used to help debug client errors. The value is a long string of the form

    828ef0b8-a876-4579-85db-3cc9d1b5f6e5

#### <a name="http-status-codes"></a> HTTP Status Codes

| Status Code | Description                                                                                                                                |
|-------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| 200         | Success                                                                                                                                    |
| 400         | Bad request. The body will contain errors.                                                                                                 |
| 404         | Not found. This could be returned either because the URL isn't known by access control or the item wasn't found.                           |
| 409         | Conflict. This is returned when a revision id conflict occurred while saving the item.                                                     |
| 415         | Unsupported Media Type. The body will return an error message that contains the list of supported formats.                                 |
| 422         | Unprocessable entity. Access control understood the request, but the concept failed ingest validation rules. The body will contain errors. |
| 500         | Internal error. Contact CMR Operations if this occurs.                                                                                     |
| 503         | Internal error because a service dependency is not available.                                                                              |

#### <a name="successful-responses"></a> Successful Responses

Successful responses will return an HTTP Status code of 200 and a body containing the CMR Concept Id of the item that was updated or deleted along with the revision id.

    {"concept_id":"AG12345-PROV","revision_id":1}

## <a name="groups"></a> Groups

Groups are used to identify sets of users for the assignment of access privileges. Groups are either owned by a provider or the system. Provider level groups define sets of users access to that providers data. System level groups define sets of users for assigning system level access. Groups have the following fields:

* `name` - Required field that uniquely identifies a system group or a group within a provider.
* `provider_id` - Id of the provider that owns the group. If this isn't present then the group will be a system level group.
* `description` - Required field that describes the group.
* `members` - Optional. May be specified in create and update operations.
* `legacy_guid` - Internal use only. This is used for ECHO Kernel interoperability.

### <a name="create-group"></a> Create Group

Groups are created by POSTing a JSON representation of a group to `%CMR-ENDPOINT%/groups` along with a valid EDL bearer token. Optionally, a `managing_group_id` parameter can be used to specify the managing group of the newly created group; Its value is the concept id of the existing group that will able to update or delete the new group. The response will contain a concept id identifying the group along with the group revision id. Creating and updating a group is synchronous and the group will be visible in search immediately after the ingest response is returned.

#### Creating a System Level Group

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXX" %CMR-ENDPOINT%/groups -d \
'{
  "name": "Administrators",
  "description": "The group of users that manages the CMR.",
  "members": ["user1", "user2"]
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1

{"revision_id":1,"concept_id":"AG1200000000-CMR"}
```

#### Creating a Provider Level Group

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXX" %CMR-ENDPOINT%/groups -d \
'{
  "name": "Administrators",
  "provider_id": "PROV1",
  "description": "The group of users that manages PROV1s data holdings."
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1

{"revision_id":1,"concept_id":"AG1200000001-PROV1"}
```

### <a name="retrieve-group"></a> Retrieve Group

A single group can be retrieved by sending a GET request to `%CMR-ENDPOINT%/groups/<concept-id>` where `concept-id` is the concept id of the group returned when it was created.

```
curl -i -H "Authorization: Bearer XXXX" %CMR-ENDPOINT%/groups/AG1200000000-CMR?pretty=true

HTTP/1.1 200 OK
Content-Type: application/json

{
  "name" : "Administrators",
  "description" : "The group of users that manages PROV1s data holdings."
}
```

### <a name="update-group"></a> Update Group

Groups are updated by sending a PUT request with the JSON representation of a group to `%CMR-ENDPOINT%/groups/<concept-id>` where `concept-id` is the concept id of the group returned when it was created. The same rules apply when updating a group as when creating it but only the description can be modified. The response will contain the concept id along with the group revision id.

Only keys present in the update request will be updated. For example: if a `"members"` key is specified, then the group's members will be updated with the supplied value, otherwise the group's members will remain unchanged.

```
curl -XPUT -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/groups/AG1200000000-CMR -d \
'{
  "name": "Administrators",
  "description": "The group of users that manages the CMR and related systems."
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1

{"concept_id":"AG1200000000-CMR","revision_id":2}
```

### <a name="delete-group"></a> Delete Group

Groups are deleted by sending a DELETE request to `%CMR-ENDPOINT%/groups/<concept-id>` where `concept-id` is the concept id of the group returned when it was created. Deleting a group creates a tombstone that marks the group as deleted. The concept id of the group and the revision id of the tombstone are returned from a delete request.

```
curl -XDELETE -i  -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/groups/AG1200000000-CMR

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1

{"concept_id":"AG1200000000-CMR","revision_id":3}
```

### <a name="retrieve-group-members"></a> Retrieve Group Members

The members in a group can be retrieved by sending a GET request to `%CMR-ENDPOINT%/groups/<concept-id>/members` where `concept-id` is the concept id of the group returned when it was created.

```
curl -i -H "Authorization: Bearer XXXX" %CMR-ENDPOINT%/groups/AG1200000000-CMR/members?pretty=true

HTTP/1.1 200 OK
Content-Type: application/json

["user1", "user2", "user3"]
```

### <a name="add-group-members"></a> Add Group Members

The members in a group can be added by sending a POST request to `%CMR-ENDPOINT%/groups/<concept-id>/members` where `concept-id` is the concept id of the group returned when it was created. The body of the request should be a JSON array of Earthdata Login usernames. The concept id and updated revision number is returned.

```
curl -i -XPOST -H "Authorization: Bearer XXXX" -H "Content-Type: application/json" %CMR-ENDPOINT%/groups/AG1200000000-CMR/members -d
'["user1", "user2", "user3"]'

HTTP/1.1 200 OK
Content-Type: application/json

{"concept_id":"AG1200000000-CMR","revision_id":3}
```

### <a name="remove-group-members"></a> Remove Group Members

The members in a group can be removed by sending a DELETE request to `%CMR-ENDPOINT%/groups/<concept-id>/members` where `concept-id` is the concept id of the group returned when it was created. The body of the request should be a JSON array of Earthdata Login usernames to remove. The concept id and updated revision number is returned.

```
curl -i -XDELETE -H "Authorization: Bearer XXXX" -H "Content-Type: application/json" %CMR-ENDPOINT%/groups/AG1200000000-CMR/members -d
'["user1", "user2", "user3"]'

HTTP/1.1 200 OK
Content-Type: application/json

{"concept_id":"AG1200000000-CMR","revision_id":4}
```

### <a name="search-groups"></a> Search Groups

Groups can be searched for by sending a GET request to `%CMR-ENDPOINT%/groups`

##### Group Search Parameters

The following parameters are supported when searching for groups.

##### Standard Parameters:

* page_size
* page_num
* pretty
* include_members - boolean parameter that indicates if the members of the group should be returned.

##### Group Matching Parameters

These parameters will match fields within a group. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `provider[]=PROV1&provider[]=PROV2`.

* provider
  * options: ignore_case, pattern
  * This allows searching for either system level groups or groups owned by a specific provider. Use the provider id of "CMR" to search for system level groups.
* name
  * options: ignore_case, pattern
* legacy_guid
  * options: ignore_case, pattern
* member
  * options: pattern, and
  * Allows searching for groups by member. The Earthdata Login username should be provided. It is always searched case-insensitively.
  * The `and` option allows specifying when sending multiple members whether to find groups that contain at least one of the members passed in (`options[member][and]=false`) or all of the members passed in (`options[member][and]=true`). The option defaults to false.
* concept_id - matches group concept ids

##### Group Search Response

The response is always returned in JSON and includes the following parts.

* hits - How many total groups were found.
* took - How long the search took in milliseconds
* items - a list of the current page of groups with the following fields
  * concept_id
  * revision_id
  * name
  * description
  * provider_id - if a provider level group
  * member_count - The number of users in the group

##### Group Search Example

```
curl -i %CMR-ENDPOINT%/groups?pretty=true

HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
CMR-Hits: 4
CMR-Took: 5
CMR-Request-Id: 5689303f-574d-4edf-b2f1-5219dc0ae6c5
Content-Length: 702

{
  "hits" : 4,
  "took" : 5,
  "items" : [ {
    "member_count" : 2,
    "name" : "Administrators",
    "description" : "na",
    "revision_id" : 2,
    "concept_id" : "AG1200000002-PROV1",
    "provider_id" : "PROV1"
  }, {
    "name" : "Administrators",
    "member_count" : 2,
    "description" : "na",
    "revision_id" : 2,
    "concept_id" : "AG1200000000-CMR"
  }, {
    "name" : "Data Readers",
    "member_count" : 2,
    "description" : "na",
    "revision_id" : 2,
    "concept_id" : "AG1200000001-CMR"
  } ]
}
```

## <a name="acls"></a> Access Control Lists (ACLs)

Access Control Lists (ACLs) define permissions within the CMR. Everything is restricted in the CMR by default. ACLs give permission to specific users to take some action on some data.

Every ACL defines three parts like a simple sentence: subject, predicate, and object. The sentence "Science Users can view Provider FOO's granules" is an example. "Science Users" is the subject. "can view" is the predicate. "Provider FOO's granules" is the object.

ACLs are represented by JSON. If "Science Users" was an existing group defined in Provider FOO with concept id AG1234-FOO then an ACL granting that group access to view and order and guests permission to view FOO's granules would look like the following:

```
{
    "group_permissions": [{
        "group_id": "AG1234-FOO",          // Subject
        "permissions": ["read", "order"]   // Predicates
    }, {
        "user_type": "guest",              // Subject
        "permissions": ["read"]            // Predicates
    }],
    "catalog_item_identity": {             // Object
        "name": "All Granules",
        "provider_id": "FOO",
        "granule_applicable": true
    }
}
```

This is like a sentence saying "Science Users can view and order and Guests can view Provider FOO's granules."

The subject and predicate are in `group_permissions`. An ACL can identify multiple permissions per group. There can be multiple subjects and multiple predicates per subject in a single ACL.

By default, any provided `group_id` must be in the format of a CMR group concept_id. If the access-control configuration `allow-edl-groups` is set to `true`, then this validation will be bypassed.

Every ACL refers to an object called an "identity". The "identity" identifies what in the CMR is being granted permission by the the ACL. There are 4 kinds of identities:

* System Identities - Identifies a system level thing in the CMR.
* Provider Identities - Identifies a type of object owned by a specific provider.
* Single Instance Identities - Identifies a single instance of something in the CMR. Currently this only applies to managing specific groups.
* Catalog Item Identities - Identifies sets of catalog items (collections and granules) owned by a provider.

### ACL Uniqueness

ACLs are uniquely identified by their identity. There can only be one ACL to a specific identity. For example provider identities contain a provider id and a target. There can only be one ACL in the system for granting permissions to Provider FOO's Option Definitions. The rules for uniquely identifying each type is listed below.

* System Identities - Unique by target
* Provider Identities - Unique by provider id and target
* Single Instance Identities - Unique by target id (Group concept id)
* Catalog Item Identities - Unique by provider id and name.

### <a name="create-acl"></a> Create ACL

ACLs are created by POSTing a JSON representation of an ACL to `%CMR-ENDPOINT%/acls` along with a valid EDL bearer token. The response will contain a concept id identifying the ACL along with the ACL revision id.

#### Creating an ACL

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXX" %CMR-ENDPOINT%/acls -d \
'{
    "group_permissions": [{
        "group_id": "AG1234-FOO",
        "permissions": ["read", "order"]
    }, {
        "user_type": "guest",
        "permissions": ["read"]
    }],
    "catalog_item_identity": {
        "name": "All Granules",
        "provider_id": "FOO",
        "granule_applicable": true
    }
}'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1

{"revision_id":1,"concept_id":"ACL1200000000-CMR"}
```

### Grantable Permissions

For system, provider, and single instance identities, the grantable permissions are limited by the target of the ACL.

#### System Identity

| Target                               | Grantable Permissions        |
|--------------------------------------|------------------------------|
| SYSTEM_AUDIT_REPORT                  | read                         |
| METRIC_DATA_POINT_SAMPLE             | read                         |
| SYSTEM_INITIALIZER                   | create                       |
| ARCHIVE_RECORD                       | delete                       |
| ERROR_MESSAGE                        | update                       |
| TOKEN                                | read, delete                 |
| TOKEN_REVOCATION                     | create                       |
| EXTENDED_SERVICE_ACTIVATION          | create                       |
| ORDER_AND_ORDER_ITEMS                | read, delete                 |
| PROVIDER                             | create, delete               |
| TAG_GROUP                            | create, update, delete       |
| TAXONOMY                             | create                       |
| TAXONOMY_ENTRY                       | create                       |
| USER_CONTEXT                         | read                         |
| USER                                 | read, update, delete         |
| GROUP                                | create, read                 |
| ANY_ACL                              | create, read, update, delete |
| EVENT_NOTIFICATION                   | delete                       |
| EXTENDED_SERVICE                     | delete                       |
| SYSTEM_OPTION_DEFINITION             | create, delete               |
| SYSTEM_OPTION_DEFINITION_DEPRECATION | create                       |
| INGEST_MANAGEMENT_ACL                | read, update                 |
| SYSTEM_CALENDAR_EVENT                | create, update, delete       |
| DASHBOARD_ADMIN                      | create, read, update, delete |
| DASHBOARD_ARC_CURATOR                | create, read, update, delete |
| DASHBOARD_MDQ_CURATOR                | create, read, update, delete |

#### Provider Identity
| Target                          | Grantable Permissions        |
|---------------------------------|------------------------------|
| AUDIT_REPORT                    | read                         |
| OPTION_ASSIGNMENT               | create, read, delete         |
| OPTION_DEFINITION               | create, delete               |
| OPTION_DEFINITION_DEPRECATION   | create                       |
| DATASET_INFORMATION             | read                         |
| PROVIDER_HOLDINGS               | read                         |
| EXTENDED_SERVICE                | create, update, delete       |
| PROVIDER_ORDER                  | read                         |
| PROVIDER_ORDER_RESUBMISSION     | create                       |
| PROVIDER_ORDER_ACCEPTANCE       | create                       |
| PROVIDER_ORDER_REJECTION        | create                       |
| PROVIDER_ORDER_CLOSURE          | create                       |
| PROVIDER_ORDER_TRACKING_ID      | update                       |
| PROVIDER_INFORMATION            | update                       |
| PROVIDER_CONTEXT                | read                         |
| AUTHENTICATOR_DEFINITION        | create, delete               |
| PROVIDER_POLICIES               | read, update, delete         |
| USER                            | read                         |
| GROUP                           | create, read                 |
| PROVIDER_OBJECT_ACL             | create, read, update, delete |
| CATALOG_ITEM_ACL                | create, read, update, delete |
| INGEST_MANAGEMENT_ACL           | read, update                 |
| DATA_QUALITY_SUMMARY_DEFINITION | create, update, delete       |
| DATA_QUALITY_SUMMARY_ASSIGNMENT | create, delete               |
| PROVIDER_CALENDAR_EVENT         | create, update, delete       |
| DASHBOARD_DAAC_CURATOR          | create, read, update, delete |
| NON_NASA_DRAFT_USER             | create, read, update, delete |
| NON_NASA_DRAFT_APPROVER         | create, read, update, delete |
| SUBSCRIPTION_MANAGEMENT         | read, update                 |

#### Single Instance Identity

| Target           | Grantable Permissions |
|------------------|-----------------------|
| GROUP_MANAGEMENT | update, delete        |

### <a name="search-acls"></a> Search ACLs

ACLs can be searched for by sending a GET request to `%CMR-ENDPOINT%/acls`, or a POST to `%CMR-ENDPOINT%/acls/search`. A valid EDL bearer token is required to search ACLs. If no token is supplied then guest permissions are used.

##### ACL Search Parameters

The following parameters are supported when searching for ACLs.

##### Standard Parameters:

* page_size
* page_num
* pretty
* include_full_acl - boolean parameter that indicates if the full acl details should be included in the search response.
* include_legacy_group_guid - boolean parameter that indicates if legacy group guid should be returned in place of group concept id in full acl search response. It can only be used when include_full_acl parameter is true.

##### ACL Matching Parameters

* permitted_group
  * options: ignore_case
* identity_type
  * options: none (always case-insensitive)
  * The type must be one or more of the following
    * system
    * provider
    * single_instance
    * catalog_item
* target
  * options: none (always case-insensitive)
  * Matches ACLs which have the given object identity target
* target_id
  * options: none (case-sensitive)
  * Matches single_instance ACLs through specified group target_id. Only applies when the single_instance target is GROUP_MANAGEMENT
  * identity_type=single_instance parameter is required alongside this parameter
* permitted_user
  * options: none (always case-insensitive)
  * user is a URS user name corresponding to a member of a group that has access to an ACL
* provider
  * options: ignore_case
  * Matches ACLs which reference a provider through a catalog item identity or a provider identity
* group_permission
  * options: none (always case-insensitive)
  * This is a nested parameter that has subfields 'permitted_group' and 'permission'; it can contain both subfields or just one
* permitted_concept_id
  * Matches ACLs that grant permission to the collection or granule with the given concept id
  * For an ACL to grant permission to a collection, the collection_applicable field of the ACL should be true and all the collection identifier filters (entry title, temporal and access value) of the ACL should match the related fields of the collection; no collection identifier defined in the ACL makes the ACL match all collections when its collection_applicable field is true.
  * For an ACL to grant permission to a granule, the granule_applicable field of the ACL should be true and both the granule identifier filters (temporal and access value), and collection identifier filters (entry title, temporal and access value) of the ACL should match the related fields of the granule and its parent collection.
* id
  * options: none (case-sensitive)
  * Matches either ACL concept ID or ACL legacy GUID

##### ACL Search Response

The response is always returned in JSON and includes the following parts.

* hits - How many total ACLs were found.
* took - How long the search took in milliseconds
* items - a list of the current page of ACLs with the following fields
  * concept_id
  * revision_id
  * name - This will be the catalog item identity name or a string containing "<identity type> - <target>". For example "System - PROVIDER"
  * identity_type - String of "provider", "system", "single_instance", or "catalog_item"
  * location - A URL to retrieve the ACL
  * acl - full JSON of the ACL. Included if `include_full_acl=true` parameter is set.

##### ACL Search Examples

###### By permitted_group

```
curl -i "%CMR-ENDPOINT%/acls/search" -d "permitted_group[]=guest&permitted_group[]=registered&pretty=true"

HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
CMR-Hits: 4
CMR-Took: 5
CMR-Request-Id: 5689303f-574d-4edf-b2f1-5219dc0ae6c5
Content-Length: 702

{
  "hits" : 3,
  "took" : 6,
  "items" : [ {
    "revision_id" : 1,
    "concept_id" : "ACL1200000008-CMR",
    "identity_type" : "Catalog Item",
    "name" : "All Collections",
    "location" : "%CMR-ENDPOINT%/acls/ACL1200000008-CMR"
  }, {
    "revision_id" : 1,
    "concept_id" : "ACL1200000009-CMR",
    "identity_type" : "Catalog Item",
    "name" : "All Granules",
    "location" : "%CMR-ENDPOINT%/acls/ACL1200000009-CMR"
  }, {
    "revision_id" : 1,
    "concept_id" : "ACL1200000006-CMR",
    "identity_type" : "Group",
    "name" : "Group - AG1234-CMR",
    "location" : "%CMR-ENDPOINT%/acls/ACL1200000006-CMR"
  } ]
}
```

###### By identity_type

```
curl -i "%CMR-ENDPOINT%/acls/search" -d "identity_type[]=provider&identity_type[]=catalog_item&pretty=true"

HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
CMR-Hits: 2
CMR-Took: 5
CMR-Request-Id: 5689303f-574d-4edf-b2f1-5219dc0ae6c5
Content-Length: 702

{
  "hits" : 2,
  "took" : 4,
  "items" : [ {
    "revision_id" : 1,
    "concept_id" : "ACL1200000003-CMR",
    "identity_type" : "Catalog Item",
    "name" : "All Collections",
    "location" : "%CMR-ENDPOINT%/acls/ACL1200000003-CMR"
  }, {
    "revision_id" : 1,
    "concept_id" : "ACL1200000001-CMR",
    "identity_type" : "Provider",
    "name" : "Provider - PROV1 - AUDIT_REPORT",
    "location" : "%CMR-ENDPOINT%/acls/ACL1200000001-CMR"
  } ]
}
```

###### By permitted_user

```
curl -i "%CMR-ENDPOINT%/acls/search" -d "permitted_user=user1&pretty=true"

HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
CMR-Hits: 2
CMR-Took: 5
CMR-Request-Id: 5689303f-574d-4edf-b2f1-5219dc0ae6c5
Content-Length: 702

{
  "hits" : 2,
  "took" : 4,
  "items" : [ {
    "revision_id" : 1,
    "concept_id" : "ACL1200000003-CMR",
    "identity_type" : "Catalog Item",
    "name" : "All Collections",
    "location" : "%CMR-ENDPOINT%/acls/ACL1200000003-CMR"
  }, {
    "revision_id" : 1,
    "concept_id" : "ACL1200000001-CMR",
    "identity_type" : "Provider",
    "name" : "Provider - PROV1 - AUDIT_REPORT",
    "location" : "%CMR-ENDPOINT%/acls/ACL1200000001-CMR"
  } ]
}
```

###### By provider

```
curl -i "%CMR-ENDPOINT%/acls/search" -d "provider=prov1&pretty=true"

HTTP/1.1 200 OK
Date: Thu, 28 Jul 2016 13:16:36 GMT
Content-Type: application/json; charset=UTF-8
Access-Control-Expose-Headers: CMR-Hits, CMR-Request-Id
Access-Control-Allow-Origin: *
CMR-Hits: 3
CMR-Took: 10
CMR-Request-Id: 04d273f9-c0c5-4f74-a788-00837d2b80f3
Content-Length: 688
Server: Jetty(9.2.10.v20150310)

{
  "hits" : 3,
  "took" : 10,
  "items" : [ {
    "revision_id" : 1,
    "concept_id" : "ACL1200000001-CMR",
    "identity_type" : "Catalog Item",
    "name" : "Catalog_Item1_PROV1",
    "location" : "%CMR-ENDPOINT%/acls/ACL1200000001-CMR"
  }, {
    "revision_id" : 1,
    "concept_id" : "ACL1200000002-CMR",
    "identity_type" : "Catalog Item",
    "name" : "Catalog_Item2_PROV1",
    "location" : "%CMR-ENDPOINT%/acls/ACL1200000002-CMR"
  }, {
    "revision_id" : 1,
    "concept_id" : "ACL1200000000-CMR",
    "identity_type" : "Provider",
    "name" : "Provider - PROV1 - INGEST_MANAGEMENT_ACL",
    "location" : "%CMR-ENDPOINT%/acls/ACL1200000000-CMR"
  } ]
}
```

##### By group_permission

```
curl -i "%CMR-ENDPOINT%/acls/search" -d "group_permission[0][permitted_group]=guest&group_permission[0][permission]=create&pretty=true"

HTTP/1.1 200 OK
Date: Fri, 12 Aug 2016 18:42:42 GMT
Content-Type: application/json; charset=UTF-8
Access-Control-Expose-Headers: CMR-Hits, CMR-Request-Id
Access-Control-Allow-Origin: *
CMR-Hits: 1
CMR-Took: 5
CMR-Request-Id: 987912b3-be19-43a2-84b7-0b768da36eec
Content-Length: 257
Server: Jetty(9.2.10.v20150310)

{
  "hits" : 1,
  "took" : 4,
  "items" : [ {
    "revision_id" : 1,
    "concept_id" : "ACL1200000002-CMR",
    "identity_type" : "System",
    "name" : "System - SYSTEM_AUDIT_REPORT",
    "location" : "%CMR-ENDPOINT%/acls/ACL1200000002-CMR"
  } ]
}
```

###### By permitted_concept_id

```
curl -i "%CMR-ENDPOINT%/acls/search" -d "permitted_concept_id=C1200000003-PROV1&pretty=true"

HTTP/1.1 200 OK
Date: Thu, 03 Nov 2016 15:48:41 GMT
Content-Type: application/json; charset=UTF-8
Access-Control-Expose-Headers: CMR-Hits, CMR-Request-Id
Access-Control-Allow-Origin: *
CMR-Hits: 2
CMR-Took: 21
CMR-Request-Id: 461bfb0f-dc52-442a-a79f-d615909298ac
Content-Length: 456
Server: Jetty(9.2.10.v20150310)

{
  "hits" : 2,
  "took" : 12,
  "items" : [ {
    "revision_id" : 1,
    "concept_id" : "ACL1200000008-CMR",
    "identity_type" : "Catalog Item",
    "name" : "Access value 1",
    "location" : "%CMR-ENDPOINT%/acls/ACL1200000008-CMR"
  }, {
    "revision_id" : 1,
    "concept_id" : "ACL1200000007-CMR",
    "identity_type" : "Catalog Item",
    "name" : "Access value 1-10",
    "location" : "%CMR-ENDPOINT%/acls/ACL1200000007-CMR"
  } ]
}
```

###### With include_full_acl

```
curl -i "%CMR-ENDPOINT%/acls/search" -d "include_full_acl=true&pretty=true"

HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
CMR-Hits: 2
CMR-Took: 27
CMR-Request-Id: b3e38b33-eaf3-46ac-9f04-fa62eabb2c11

{
  "hits" : 2,
  "took" : 27,
  "items" : [ {
    "revision_id" : 1,
    "concept_id" : "ACL1200000000-CMR",
    "identity_type" : "Provider",
    "acl" : {
      "group_permissions" : [ {
        "group_id" : "AG1200000003-PROV1",
        "permissions" : [ "delete", "update" ]
      } ],
      "provider_identity" : {
        "provider_id" : "PROV1",
        "target" : "INGEST_MANAGEMENT_ACL"
      }
    },
    "name" : "Provider - PROV1 - INGEST_MANAGEMENT_ACL",
    "location" : "%CMR-ENDPOINT%/acls/ACL1200000000-CMR"
  }, {
    "revision_id" : 1,
    "concept_id" : "ACL1200000002-CMR",
    "identity_type" : "Catalog Item",
    "acl" : {
      "group_permissions" : [ {
        "user_type" : "guest",
        "permissions" : [ "read" ]
      } ],
      "catalog_item_identity" : {
        "name" : "guest read acl",
        "provider_id" : "PROV1",
        "collection_applicable" : true
      }
    },
    "name" : "guest read acl",
    "location" : "%CMR-ENDPOINT%/acls/ACL1200000002-CMR"
  } ]
}

```

### <a name="retrieve-acl"></a> Retrieve ACL

A single ACL can be retrieved by sending a GET request to `%CMR-ENDPOINT%/acls/<concept-id>` where `concept-id` is the concept id of the ACL returned when it was created.

Search parameter `include_legacy_group_guid`, which is a boolean parameter, can be used to indicate if legacy group guid should be returned in place of group concept id in the returned ACL.

```
curl -i -H "Authorization: Bearer XXXX" %CMR-ENDPOINT%/acls/ACL1200000000-CMR?pretty=true

HTTP/1.1 200 OK
Content-Type: application/json

{
  "group_permissions" : [ {
    "group_id" : "AG1234-FOO",
    "permissions" : [ "read", "order" ]
  }, {
    "user_type" : "guest",
    "permissions" : [ "read" ]
  } ],
  "catalog_item_identity" : {
    "name" : "All Granules",
    "provider_id" : "FOO",
    "granule_applicable" : true
  }
}
```

### <a name="update-acl"></a> Update ACL

An ACL can be updated by sending a PUT request to `%CMR-ENDPOINT%/acls/<concept-id>` where `concept-id` is the concept id of the ACL returned when it was created. `legacy-guid` field cannot be modified during update. The fields that uniquely identify an ACL cannot be updated, e.g. the `target` field of a system identity ACL. This limitation will eventually be removed as documented in CMR-3163.

```
curl -XPUT -i -H "Authorization: Bearer XXXX" %CMR-ENDPOINT%/acls/ACL1200000000-CMR -d \
'{
    "group_permissions": [{
        "group_id": "AG1234-FOO",
        "permissions": ["read", "order"]
    }, {
        "user_type": "guest",
        "permissions": ["read"]
    }],
    "catalog_item_identity": {
        "name": "All Granules",
        "provider_id": "FOO",
        "granule_applicable": true
    }
}'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1

{"revision_id":2,"concept_id":"ACL1200000000-CMR"}
```

### <a name="delete-acl"></a> Delete ACL

An ACL can be deleted with a DELETE request to `%CMR-ENDPOINT%/acls/<concept-id>` where `concept-id` is the concept id of the ACL returned when it was created.

```
curl -XDELETE -i -H "Authorization: Bearer XXXX" %CMR-ENDPOINT%/acls/ACL1200000000-CMR

HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 50

{"revision-id":3,"concept-id":"ACL1200000000-CMR"}
```

### <a name="get-permissions"></a> Checking User Permissions

You can check the permissions granted to a specific user or user type on specific concepts or system objects by making a GET request to `%CMR-ENDPOINT%/permissions`.

The response is a JSON object mapping target ids to arrays of permissions granted to the specified user for the respective concept.

Example GET request:

```
curl -g -i -H "Authorization: Bearer XXXX" "%CMR-ENDPOINT%/permissions?user_type=guest&concept_id[]=C1200000000-PROV1&concept_id[]=C1200000001-PROV1"

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1

{"C1200000000-PROV1": ["read"], "C1200000001-PROV1": []}
```

When requesting permissions for large sets of concept ids, you can make a POST request to `%CMR-ENDPOINT%/permissions` and pass the query parameters in URL encoded query string in the POST body.

Example POST request:

```
curl -i -XPOST -H "Content-Type: application/x-www-form-urlencoded" "%CMR-ENDPOINT%/permissions" -d \
  'user_type=guest&concept_id=C1200000003-PROV1&concept_id=C1200000004-PROV1'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1

"C1200000003-PROV1: ["read"], "C1200000004-PROV1": ["read"]}
```

#### Parameters

* One of:
  * `concept_id` - Must be a valid concept id, or else use `concept_id[]=...&concept_id[]=...` to specify multiple concepts.
  * `system_object` - A system object identity target, e.g. "GROUP"
  * `provider` AND `target` - A provider id and a provider object identity target, e.g. "PROVIDER_HOLDINGS"
  * `target_group_id` - A single instance object identity target id, i.e. group concept id
* And one of:
  * `user_id` - The user whose permissions will be computed.
  * `user_type` - Either "guest" or "registered".

### <a name="get-s3-buckets"></a> User Access to S3 Buckets

This endpoint will return a JSON list of S3 buckets a user has access to. If a list of providers is included in the request, the list of S3 buckets will be filtered to only include S3 buckets contained by collections within those providers.

#### Parameters

* Required:
  * `user_id` - The user whose available S3 buckets and object prefix names will be determined for.

* Optional
  * `provider[]` - A list of provider ids to filter the S3 bucket and object prefix names by. If not specified, all providers will be included.


Example request:
```
curl -i -H "Authorization: Bearer XXXX" "%CMR-ENDPOINT%/s3-buckets?user_id=user1

HTTP/1.1 200 OK
Content-Length: 81
Content-Type: application/json

["s3","s3://aws.example-1.com","s3://aws.example-2.com","s3://aws.example-3.com"]
```


Example request with providers specified:
```
curl -i -H "Authorization: Bearer XXXX" "%CMR-ENDPOINT%/s3-buckets?user_id=user1&provider[]=PROV2&provider[]=PROV3

HTTP/1.1 200 OK
Content-Length: 51
Content-Type: application/json

["s3://aws.example-2.com","s3://aws.example-3.com"]
```


### <a name="application-health"></a> Application Health

This will report the current health of the application. It checks all resources and services used by the application and reports their health status in the response body in JSON format. The report includes an "ok?" status and a "problem" field for each resource. The report includes an overall "ok?" status and health reports for each of a service's dependencies. It returns HTTP status code 200 when the application is healthy, which means all its interfacing resources and services are healthy; or HTTP status code 503 when one of the resources or services is not healthy. It also takes pretty parameter for pretty printing the response.

    curl -i -XGET %CMR-ENDPOINT%/health?pretty=true

Example healthy response body:

```
{
  "echo" : {
    "ok?" : true
  },
  "metadata-db" : {
    "ok?" : true,
    "dependencies" : {
      "oracle" : {
        "ok?" : true
      },
      "echo" : {
        "ok?" : true
      }
    }
  }
}
```

Example unhealthy response body:

```
{
  "echo" : {
    "ok?" : true
  },
  "metadata-db" : {
    "ok?" : false,
    "dependencies" : {
      "oracle" : {
        "ok?" : false,
        "problem" : "Exception occurred while getting connection: oracle.ucp.UniversalConnectionPoolException: Cannot get Connection from Datasource: java.sql.SQLRecoverableException: IO Error: The Network Adapter could not establish the connection"
      },
      "echo" : {
        "ok?" : true
      }
    }
  }
}
```
