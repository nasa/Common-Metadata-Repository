---
title: Subscription
description: Provides information on the definition and use of subscriptions.
---

## <a name="subscription"></a> Subscription

A subscription allows a user to be notified when specific collections/granules are created, updated, or deleted. The collections/granules specified can be filtered by query conditions.

There are two kinds of subscriptions: Batch Notification and Near-Real-Time Notification

<ul>
    <li>Batch Notification subscription notification processing is executed periodically, to see if there are any collections/granules that are created/updated since the last time the subscription has been processed and will notify the subscription user with any matches. Notification of updates is via the email address associated with the SubscriberId's EarthData Login (URS). </li>
    <ul>
        <li>There are two types of batch process subscriptions (identified by the "Type" field of the subscription):</li>
        <ul>
            <li>collection subscription for users to be notified when collections are created/updated, or </li>
            <li>granule subscription for users to be notified when granules are created/update</li>
        </ul>
    </ul>
    <li>Near-Real-Time (NRT) Notification subscriptions are processed on ingest and are only for granules. When a user subscribes, notifications are sent out via the provided notification endpoint, such as an AWS SQS messaging queue.
</ul>

### <a name="searching-for-subscriptions"></a> Searching for Subscriptions

Subscriptions can be searched for by sending a request to `%CMR-ENDPOINT%/subscriptions`. XML reference, JSON, and UMM JSON response formats are supported for subscriptions search.

Subscription search results are paged. See [Paging Details](#paging-details) for more information on how to page through subscription search results.

#### <a name="subscription-search-params"></a> Subscription Search Parameters

The following parameters are supported when searching for subscriptions.

#### Standard Parameters
* page_size
* page_num
* pretty

#### Subscription Matching Parameters

These parameters will match fields within a subscription. They are case insensitive by default. They support options specified. They also support searching with multiple values in the style of `name[]=key1&name[]=key2`. The values are ORed together.


  * name
    * options: pattern, ignore_case
  * provider
    * options: pattern, ignore_case
  * native_id
    * options: pattern, ignore_case
  * concept_id
  * subscriber_id
  * collection_concept_id
  * type
#### <a name="subscription-search-response"></a> Subscription Search Response

#### XML Reference
The XML reference response format is used for returning references to search results. It consists of the following fields:

|   Field    |                    Description                     |
| ---------- | -------------------------------------------------- |
| hits       | the number of results matching the search query    |
| took       | time in milliseconds it took to perform the search |
| references | identifying information about each search result   |

The `references` field may contain multiple `reference` entries, each consisting of the following fields:

|    Field    |                                                   Description                                                   |
| ----------- | --------------------------------------------------------------------------------------------------------------- |
| name        | the value of the Name field in subscription metadata.                                                                |
| id          | the CMR identifier for the result                                                                               |
| location    | the URL at which the full metadata for the result can be retrieved                                              |
| revision-id | the internal CMR version number for the result                                                                  |

__Example__

```
curl -i "%CMR-ENDPOINT%/subscriptions?name=someSub1&pretty=true"

HTTP/1.1 200 OK
Content-Type: application/xml; charset=UTF-8
Content-Length: 393

<?xml version="1.0" encoding="UTF-8"?>
<results>
    <hits>1</hits>
    <took>5</took>
    <references>
        <reference>
            <name>someSub1</name>
            <id>SUB1200000005-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/SUB1200000005-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
#### JSON
The JSON response includes the following fields.

* hits - How many total variables were found.
* took - How long the search took in milliseconds
* items - a list of the current page of subscriptions with the following fields
  * concept_id
  * revision_id
  * provider_id
  * native_id
  * type
  * name
  * subscriber_id
  * collection_concept_id

__Example__

```
curl -g -i "%CMR-ENDPOINT%/subscriptions.json?pretty=true"

HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8
Content-Length: 944

{
  "hits" : 4,
  "took" : 2,
  "items" : [ {
    "concept_id" : "SUB1200000005-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "subscription-1",
    "type" : "granule",
    "name" : "someSub1",
    "subscriber-id" : "someSubId1",
    "collection-concept-id" : "C1200000001-PROV1"
  }, {
    "concept_id" : "SUB1200000006-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "subscription-2",
    "type" : "collection",
    "name" : "someSub2",
    "subscriber-id" : "someSubId2",
  } ]
}
```
#### UMM JSON
The UMM JSON response contains meta-metadata of the subscription and the UMM fields.

__Example__

```
curl -g -i "%CMR-ENDPOINT%/subscriptions.umm_json?name=NSIDC_AtlasNorth&pretty=true"
HTTP/1.1 200 OK
Content-Type: application/vnd.nasa.cmr.umm_results+json;version=1.0; charset=utf-8

{
  "hits" : 1,
  "took" : 21,
  "items" : [ {
    "meta" : {
      "native-id" : "subscription-1",
      "provider-id" : "PROV1",
      "concept-type" : "subscription",
      "concept-id" : "SUB1200000005-PROV1",
      "creation-date" : "2020-04-01T19:52:44Z",
      "revision-date" : "2020-04-01T19:52:44Z",
      "user-id" : "ECHO_SYS",
      "deleted" : false,
      "revision-id" : 1,
      "format" : "application/vnd.nasa.cmr.umm+json"
    },
    "umm" : {
      "Name" : "NSIDC_AtlasNorth",
      "SubscriberId" : "someSubId1",
      "EmailAddress" : "sxu@gmail.com",
      "CollectionConceptId" : "C1200000001-PROV1",
      "Query" : "polygon=-18,-78,-13,-74,-16,-73,-22,-77,-18,-78"
    }
  } ]
}
```

#### <a name="retrieving-all-revisions-of-a-subscription"></a> Retrieving All Revisions of a Subscription

In addition to retrieving the latest revision for a subscription parameter search, it is possible to return all revisions, including tombstone (deletion marker) revisions, by passing in `all_revisions=true` with the URL parameters. The reference, JSON and UMM JSON response formats are supported for all revision searches. References to tombstone revisions do not include the `location` tag and include an additional tag, `deleted`, which always has content of "true".

    curl "%CMR-ENDPOINT%/subscriptions?concept_id=SUB1200000005-PROV1&all_revisions=true&pretty=true"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>3</hits>
        <took>9</took>
        <references>
            <reference>
                <name>someSub1</name>
                <id>SUB1200000005-PROV1</id>
                <deleted>true</deleted>
                <revision-id>3</revision-id>
            </reference>
            <reference>
                <name>someSub1</name>
                <id>SUB1200000005-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SUB1200000005-PROV1/2</location>
                <revision-id>2</revision-id>
            </reference>
            <reference>
                <name>someSub1</name>
                <id>SUB1200000005-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SUB1200000005-PROV1/1</location>
                <revision-id>1</revision-id>
            </reference>
        </references>
    </results>
```

#### <a name="sorting-subscription-results"></a> Sorting Subscription Results

By default, subscription results are sorted by name, then provider-id.

One or more sort keys can be specified using the sort_key[] parameter. The order used impacts searching. Fields can be prepended with a - to sort in descending order. Ascending order is the default but + (Note: + must be URL encoded as %2B) can be used to explicitly request ascending.

##### Valid Subscription Sort Keys
  * `name`
  * `provider`
  * `collection_concept_id`

Examples of sorting by name in descending (reverse alphabetical) and ascending orders (Note: the `+` must be escaped with %2B):

    curl "%CMR-ENDPOINT%/subscriptions?sort_key\[\]=-name"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>2</hits>
        <took>6</took>
        <references>
            <reference>
                <name>someSub2</name>
                <id>SUB1200000006-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SUB1200000006-PROV1/1</location>
                <revision-id>1</revision-id>
        </reference>
        <reference>
            <name>someSub1</name>
            <id>SUB1200000005-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/SUB1200000005-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
    curl "%CMR-ENDPOINT%/subscriptions?sort_key\[\]=%2Bname"

__Sample response__

```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <results>
        <hits>2</hits>
        <took>6</took>
        <references>
            <reference>
                <name>someSub1</name>
                <id>SUB1200000005-PROV1</id>
                <location>%CMR-ENDPOINT%/concepts/SUB1200000005-PROV1/1</location>
                <revision-id>1</revision-id>
        </reference>
        <reference>
            <name>someSub2</name>
            <id>SUB1200000006-PROV1</id>
            <location>%CMR-ENDPOINT%/concepts/SUB1200000006-PROV1/1</location>
            <revision-id>1</revision-id>
        </reference>
    </references>
</results>
```
### <a name="subscription-access-control"></a> Subscription Access Control

Search permission for subscription is granted through the provider via the SUBSCRIPTION_MANAGEMENT ACL. In order to be able to search for a subscription for a given provider, read permission has to be granted to the user through SUBSCRIPTION_MANAGEMENT ACL for the provider.