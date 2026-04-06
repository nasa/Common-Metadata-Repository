### <a name="tagging"></a> Tagging

Tagging allows arbitrary sets of collections to be grouped under a single namespaced value. The sets of collections can be recalled later when searching by tag fields.

Tags have the following fields:

* tag_key (REQUIRED): free text specifying the key of the tag. Tag key cannot contain `/` character. Tag key is case-insensitive, it is always saved in lower case. When it is specified as mixed case, CMR will convert it into lower case. It normally consists of the name of the organization or the project who created the tag followed by a dot and the name of the tag. For example, org.ceos.wgiss.cwic.quality. The maximum length for tag key is 1030 characters.
* description (OPTIONAL): a free text description of what this tag is and / or how it is used. The maximum length for description is 4000 characters.
* originator_id (REQUIRED): the Earthdata Login ID of the person who created the tag.

#### <a name="tag-access-control"></a> Tag Access Control

Access to tags is granted through the TAG_GROUP system object identity. Users can only create, update, or delete a tag if they are granted the appropriate permission. Associating and dissociating collections with a tag is considered an update.

#### <a name="creating-a-tag"></a> Creating a Tag

Tags are created by POSTing a JSON representation of a tag to `%CMR-ENDPOINT%/tags` along with a valid EDL bearer token. The user id of the user associated with the token will be used as the originator id. The response will contain a concept id identifying the tag along with the tag revision id.

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags -d \
'{
  "tag_key": "org.ceos.wgiss.cwic.quality",
  "description": "This is a sample tag."
 }'

HTTP/1.1 201 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 48

{"concept_id":"T1200000000-CMR","revision_id":1}
```

#### <a name="retrieving-a-tag"></a> Retrieving a Tag

A single tag can be retrieved by sending a GET request to `%CMR-ENDPOINT%/tags/<tag-key>` where `tag-key` is the tag key of the tag.

```
curl -i %CMR-ENDPOINT%/tags/org.ceos.wgiss.cwic.quality?pretty=true

HTTP/1.1 200 OK
Content-Length: 216
Content-Type: application/json;charset=ISO-8859-1

{
  "originator_id" : "mock-admin",
  "tag_key": "org.ceos.wgiss.cwic.quality",
  "description" : "This is a sample tag for indicating some data is high quality."
}
```

#### <a name="updating-a-tag"></a> Updating a Tag

Tags are updated by sending a PUT request with the JSON representation of a tag to `%CMR-ENDPOINT%/tags/<tag-key>` where `tag-key` is the tag key of the tag. The same rules apply when updating a tag as when creating it but in addition tag key and originator id cannot be modified. The response will contain the concept id along with the tag revision id.

```
curl -XPUT -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/org.ceos.wgiss.cwic.quality -d \
'{
  "tag_key": "org.ceos.wgiss.cwic.quality",
  "description": "This is a sample tag for indicating some data is high quality."
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 48

{"concept_id":"T1200000000-CMR","revision_id":2}
```

#### <a name="deleting-a-tag"></a> Deleting a Tag

Tags are deleted by sending a DELETE request to `%CMR-ENDPOINT%/tags/<tag-key>` where `tag-key` is the tag key of the tag. Deleting a tag creates a tombstone that marks the tag as deleted. The concept id of the tag and the revision id of the tombstone are returned from a delete request. Deleting a tag dissociates all collections with the tag.

```
curl -XDELETE -i  -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/org.ceos.wgiss.cwic.quality

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 48

{"concept_id":"T1200000000-CMR","revision_id":3}
```


#### <a name="tag-association"></a> Tag Association

A tag can be associated with collections through either a JSON query or a list of collection concept revisions.
Tag association by query only supports tagging the latest revision of collections.
Tag association by collections supports tagging any specified collection revisions.

Expected Response Status:
<ul>
<li>200 OK -- if all associations succeeded</li>
<li>207 MULTI-STATUS -- if some associations succeeded and some failed due to user error</li>
<li>400 BAD REQUEST -- if all associations failed due to user error</li>
</ul>

Expected Response Body:

The response body will consist of a list of tool association objects
Each association object will have:
<ul>
    <li>A `tagged_item` field</li>
    <ul>
        <li>The `tagged_item` field value has the collection concept id and the optional revision id that is used to identify the collection during tag association.</li>
    </ul>
    <li>Either a `tag_association` field with the tag association concept id and revision id when the tag association succeeded or an `errors` field with detailed error message when the tag association failed. </li>
</ul>

- IMPORTANT: The tag and the collections must exist before they can be associated together.


Here is am example of a tag association request and its response:

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/org.ceos.wgiss.cwic.native_id/associations -d \
'[{"concept_id": "C1200000005-PROV1", "data": "Global Maps of Atmospheric Nitrogen Deposition, 2016"},
  {"concept_id": "C1200000006-PROV1", "data": "Global Maps of Atmospheric Nitrogen Deposition"}]'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "tag_association":{
      "concept_id":"TA1200000009-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000005-PROV1"
    }
  },
  {
    "tag_association":{
      "concept_id":"TA1200000009-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000006-PROV1"
    }
  }
]
```

On occasions when tag association cannot be processed at all due to invalid input, tag association request will return failure status code 400 with the appropriate error message.

#### <a name="associating-collections-with-a-tag-by-query"></a> Associating Collections with a Tag by query

Tags can be associated with collections by POSTing a JSON query for collections to `%CMR-ENDPOINT%/tags/<tag-key>/associations/by_query` where `tag-key` is the tag key of the tag.
All collections found will be _added_ to the current set of associated collections with a tag.
Tag associations are maintained throughout the life of a collection.
If a collection is deleted and re-added it will maintain its tags.

Expected Response Status:
- If query delivers no match, it will still return 200 OK with no associations made

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/edsc.in_modaps/associations/by_query -d \
'{
  "condition": {"provider": "PROV1"}
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "tag_association":{
      "concept_id":"TA1200000009-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000000-PROV1"
    }
  },
  {
    "tag_association":{
      "concept_id":"TA1200000008-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000001-PROV1"
    }
  }
]
```

#### <a name="associating-collections-with-a-tag-by-concept-ids"></a> Associating Collections with a Tag by collection concept ids and optional revision ids

Tags can be associated with collections by POSTing a JSON array of collection concept-ids and optional revision ids to `%CMR-ENDPOINT%/tags/<tag-key>/associations` where `tag-key` is the tag key of the tag.
User can also provide arbitrary JSON data which is optional during tag association.
The max length of JSON data used for tag association is 32KB.
All referenced collections will be _added_ to the current set of associated collections with a tag.
Tag associations are maintained throughout the life of a collection.
If a collection is deleted and re-added it will maintain its tags.
If a tag is already associated with a collection without revision, it cannot be associated with a specific revision of that collection again, and vice versa.
Tags cannot be associated on tombstoned collection revisions.

Expected Response Status:
<ul>
    <li>200 OK -- if all associations succeeded</li>
    <li>207 MULTI-STATUS -- if some associations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all associations failed due to user error</li>
</ul>

```
curl -XPOST -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/gov.nasa.gcmd.review_status/associations -d \
'[{"concept_id": "C1200000005-PROV1", "revision_id": 2, "data": "APPROVED"},
  {"concept_id": "C1200000006-PROV1", "revision_id": 1, "data": "IN_REVIEW"},
  {"concept_id": "C1200000007-PROV1", "revision_id": 1, "data": "REVIEW_DISPUTED"}]'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "tag_association":{
      "concept_id":"TA1200000008-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000005-PROV1",
      "revision_id":2
    }
  },
  {
    "tag_association":{
      "concept_id":"TA1200000009-CMR",
      "revision_id":1
    },
    "tagged_item":{
      "concept_id":"C1200000006-PROV1",
      "revision_id":1
    }
  }
]
```

#### <a name="tag-dissociation"></a> Tag Dissociation

A tag can be dissociated from collections through either a JSON query or a list of collection concept revisions similar to tag association requests.
Tag dissociation by query only supports tag dissociation of the latest revision of collections.
Tag dissociation by collections supports tag dissociation from any specified collection revisions.
The tag dissociation response looks the same as tag association response.

Expected Response Status:
<ul>
    <li>200 OK -- if all dissociations succeeded</li>
    <li>207 MULTI-STATUS -- if some dissociations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all dissociations failed due to user error</li>
</ul>

Expected Response Body:

The response body will consist of a list of tool association objects
Each association object will have:
<ul>
    <li>A `tagged_item` field</li>
    <ul>
        <li>The `tagged_item` field is the collection concept id and the optional revision id that is used to identify the collection during tag dissociation.</li>
    </ul>
    <li>Either a `tag_association` field with the tag association concept id and revision id when the tag dissociation succeeded or an `errors` or `warnings` field with detailed message when the tag dissociation failed or inapplicable. </li>
</ul>

Here is a sample tag dissociation request and its response:

```
curl -XDELETE -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/edsc.in_modaps/associations -d \
'[{"concept_id": "C1200000005-PROV1"},
  {"concept_id": "C1200000006-PROV1"},
  {"concept_id": "C1200000007-PROV1"}]'

HTTP/1.1 207 MULTI-STATUS
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "status": 200,
    "tag_association":{
      "concept_id":"TA1200000008-CMR",
      "revision_id":2
    },
    "tagged_item":{
      "concept_id":"C1200000005-PROV1"
    }
  },
  {
    "status": 200,
    "warnings":[
      "Tag [edsc.in_modaps] is not associated with collection [C1200000006-PROV1]."
    ],
    "tagged_item":{
      "concept_id":"C1200000006-PROV1"
    }
  },
  {
    "status": 400,
    "errors":[
      "Collection [C1200000007-PROV1] does not exist or is not visible."
    ],
    "tagged_item":{
      "concept_id":"C1200000007-PROV1"
    }
  }
]
```

#### <a name="dissociating-collections-with-a-tag-by-query"></a> Dissociating a Tag from Collections by query

Tags can be dissociated from collections by sending a DELETE request with a JSON query for collections to `%CMR-ENDPOINT%/tags/<tag-key>/associations/by_query` where `tag-key` is the tag key of the tag. All collections found in the query will be _removed_ from the current set of associated collections.

Expected Response Status:
- If query delivers no match, it will still return 200 OK with no associations made

```
curl -XDELETE -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/edsc.in_modaps/associations/by_query -d \
'{
  "condition": {"provider": "PROV1"}
 }'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "tag_association":{
      "concept_id":"TA1200000007-CMR",
      "revision_id":2
    },
    "tagged_item":{
      "concept_id":"C1200000000-PROV1"
    }
  },
  {
    "tag_association":{
      "concept_id":"TA1200000008-CMR",
      "revision_id":2
    },
    "tagged_item":{
      "concept_id":"C1200000001-PROV1"
    }
  }
]
```

#### <a name="dissociating-collections-with-a-tag-by-concept-ids"></a> Dissociating a Tag from Collections by collection concept ids

Tags can be dissociated from collections by sending a DELETE request with a JSON array of collection concept-ids to
`%CMR-ENDPOINT%/tags/<tag-key>/associations` where `tag-key` is the tag key of the tag.

Expected Response Status:
<ul>
    <li>200 OK -- if all associations succeeded</li>
    <li>207 MULTI-STATUS -- if some associations succeeded and some failed due to user error</li>
    <li>400 BAD REQUEST -- if all associations failed due to user error</li>
</ul>

```
curl -XDELETE -i -H "Content-Type: application/json" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/tags/gov.nasa.gcmd.review_status/associations -d \
'[{"concept_id": "C1200000005-PROV1", "revision_id": 1},
  {"concept_id": "C1200000006-PROV1", "revision_id": 2}]'

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 168

[
  {
    "warnings":[
      "Tag [gov.nasa.gcmd.review_status] is not associated with the specific collection concept revision concept id [C1200000005-PROV1] and revision id [1]."
    ],
    "tagged_item":{
      "concept_id":"C1200000005-PROV1",
      "revision_id":1
    }
  },
  {
    "tag_association":{
      "concept_id":"TA1200000008-CMR",
      "revision_id":2
    },
    "tagged_item":{
      "concept_id":"C1200000006-PROV1",
      "revision_id":2
    }
  }
]
```

#### <a name="searching-for-tags"></a> Searching for Tags

Tags can be searched for by sending a request to `%CMR-ENDPOINT%/tags`.

Tag search results are paged. See [Paging Details](#paging-details) for more information on how to page through tag search results.

##### Tag Search Parameters

The following parameters are supported when searching for tags.

##### Standard Parameters:

* page_size
* page_num
* pretty

##### Tag Matching Parameters

These parameters will match fields within a tag. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `tag_key[]=key1&tag_key[]=key2`.

* tag_key
  * options: pattern
* originator_id
  * options: pattern

##### Tag Search Response

The response is always returned in JSON and includes the following parts.

* hits - How many total tags were found.
* took - How long the search took in milliseconds
* items - a list of the current page of tags with the following fields
  * concept_id
  * revision_id
  * tag_key
  * description
  * originator_id - The id of the user that created the tag.

##### Tag Search Example

```
curl -g -i "%CMR-ENDPOINT%/tags?pretty=true&tag_key=org\\.ceos\\.*&options[tag_key][pattern]=true"

HTTP/1.1 200 OK
Content-Type: application/json;charset=ISO-8859-1
Content-Length: 292

{
  "items" : [ {
    "concept_id" : "T1200000000-CMR",
    "revision_id" : 1,
    "tag_key" : "org.ceos.wgiss.cwic",
    "description" : "This is a sample tag.",
    "originator_id" : "mock-admin"
  } ],
  "took" : 5,
  "hits" : 1
}
```