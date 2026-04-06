%GENERIC-DOCS%

### <a name="community-usage-metrics"></a> Community Usage Metrics

Community usage metrics are metrics showing how many times a particular version of a collection has been accessed. Storing these metrics offers improved relevancy based on collection popularity. The metrics are obtained from the ESDIS Metrics System (EMS) and ingested into the system through this API.

#### <a name="updating-community-usage-metrics"></a> Updating Community Usage Metrics

Community usage metrics can be updated using the `%CMR-ENDPOINT%/community-usage-metrics` endpoint with a valid EDL bearer token or Launchpad token. The content is a CSV file obtained from the EMS. The 'Product', 'Version', and 'Hosts' columns are parsed from the CSV file and stored as 'short-name', 'version', and 'access-count' respectively in the CMR. Entries with the same Product (short-name) and Version will have the access count aggregated to form a total access count for that collection and version, stored as one entry in the CMR. The comprehensive parameter accepts a boolean value, true will cause a lookup verification on each line, false will try and short cut the lookup by checking first against the current metrics humanizer, defaults to false.

Note that when sending the data, use the --data-binary option so that the linebreaks in the CSV data are not removed. See the example below.

The response will contain a concept id and revision id identifying the set of community usage metrics.

```
curl -XPUT -i -H "Content-Type: text/csv" -H "Authorization: Bearer XXXXX" %CMR-ENDPOINT%/community-usage-metrics --data-binary <csv-file-location>

HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 48

{"concept_id":"H1200000000-CMR","revision_id":2}
```

#### <a name="retrieving-community-usage-metrics"></a> Retrieving Community Usage Metrics

The community usage metrics can be retrieved by sending a GET request to `%CMR-ENDPOINT%/community-usage-metrics`. The metrics are returned in JSON format.

```
curl -i %CMR-ENDPOINT%/community-usage-metrics?pretty=true

HTTP/1.1 200 OK
Content-Length: 224
Content-Type: application/json; charset=UTF-8

[ {
  "short-name" : "AMSR-L1A",
  "version" : "3",
  "access-count" : 100
}, {
  "short-name" : "MAPSS_MOD04_L2",
  "version" : "1",
  "access-count" : 85
} ]
```

### <a name="administrative-tasks"></a> Administrative Tasks

These tasks require an admin user token with the INGEST\_MANAGEMENT\_ACL with read or update
permission.

#### <a name="clear-the-cache"></a> Clear the cache

    curl -i -XPOST %CMR-ENDPOINT%/clear-cache

#### <a name="clear-scroll"></a> Clear scroll session

    curl -i -XPOST -H "Content-Type: application/json" %CMR-ENDPOINT%/clear-scroll -d '{ "scroll_id" : "xxxx"}'

It returns HTTP status code 204 when successful.

#### <a name="reset-the-application-to-the-initial-state"></a> Reset the application to the initial state

Every CMR application has a reset function to reset it back to it's initial state. Currently this only clears the cache so it is effectively the the same as the clear-cache endpoint.

    curl -i -XPOST %CMR-ENDPOINT%/reset

#### <a name="querying-caches"></a> Querying caches

Endpoints are provided for querying the contents of the various caches used by the application.
The following curl will return the list of caches:

    curl -i %CMR-ENDPOINT%/caches

The following curl will return the keys for a specific cache:

    curl -i %CMR-ENDPOINT%/caches/cache-name

This curl will return the value for a specific key in the named cache:

    curl -i %CMR-ENDPOINT%/caches/cache-name/cache-key

#### <a name="check-application-health"></a> Check application health

This will report the current health of the application. It checks all resources and services used by the application and reports their health in the response body in JSON format. For resources, the report includes an "ok?" status and a "problem" field if the resource is not OK. For services, the report includes an overall "ok?" status for the service and health reports for each of its dependencies. It returns HTTP status code 200 when the application is healthy, which means all its interfacing resources and services are healthy; or HTTP status code 503 when one of the resources or services is not healthy. It also takes pretty parameter for pretty printing the response.

    curl -i -XGET %CMR-ENDPOINT%/health?pretty=true

Example healthy response body:

```
{
  "echo" : {
    "ok?" : true
  },
  "internal-metadata-db" : {
    "ok?" : true,
    "dependencies" : {
      "oracle" : {
        "ok?" : true
      },
      "echo" : {
        "ok?" : true
      }
    }
  },
  "indexer" : {
    "ok?" : true,
    "dependencies" : {
      "elastic_search" : {
        "ok?" : true
      },
      "echo" : {
        "ok?" : true
      }
    }
  }
}
```

Example un-healthy response body:

```
{
  "echo" : {
    "ok?" : true
  },
  "internal-metadata-db" : {
    "ok?" : true,
    "dependencies" : {
      "oracle" : {
        "ok?" : true
      },
      "echo" : {
        "ok?" : true
      }
    }
  },
  "indexer" : {
    "ok?" : false,
    "dependencies" : {
      "elastic_search" : {
        "ok?" : false,
        "problem" : "Unable to get elasticsearch cluster health, caught exception: Connection refused"
        }
      },
      "echo" : {
        "ok?" : true
      }
    }
  }
}
```

### <a name="associate-any-concepts"></a> Associate any concepts

A new association API has been developed to achieve the goal of being able to associate a concept of any type, with or without revision, to one or more other concepts of any type, with or without revisions. The new association API also allows the associations to include an optional data payload, whose purpose is to describe the association itself. Associations which do not initially have an association data payload, may have it added through an association update.

#### <a name="associate-any-concepts"></a> Concept to concept association important notes

A concept can only be associated with another concept either with or without revisions, not both. A concept cannot be associated to itself, even with different revisions.

#### <a name="concept-associations"></a> Concept associations

A concept, with optional revision id, can be associated to one or more other concepts, with optional revision ids and data payloads.
When the revision id is not present, the latest revision is assumed.

Expected Response Status:
- 200 OK -- if all associations succeeded
- 207 MULTI-STATUS -- if some associations succeeded and some failed due to user error
- 400 BAD REQUEST -- if all associations failed due to user error

In the following example, "3" is optional for S1200000006-PROV1 and "revision_id" is optional for TL1200000008-PROV1.

 __Example__

```
 curl -XPOST \
	-H "Authorization: Bearer XXXXX" \
	-H "Content-Type:application/json" \
   "%CMR-ENDPOINT%/associate/S1200000006-PROV1/3" \
   -d '[{"concept_id": "TL1200000008-PROV1", "revision_id": 1},
         {"concept_id": "V1200000005-PROV1", "data": {"field": "value", "another_field": {"XYZ": "ZYX"}}}]'
```

 __200 OK Example Response__

Note: when two concepts are associated, their concept ids are sorted first. The one that appears at the later position is considered the associated_item.

```
[
    {
        "generic_association": {
            "concept_id": "GA1200449894-CMR",
            "revision_id": 1
        },
        "associated_item": {
            "concept_id": "TL1200000008-PROV1"
        }
    },
    {
        "generic_association": {
            "concept_id": "GA1200449895-CMR",
            "revision_id": 1
        },
        "associated_item": {
            "concept_id": "V1200000005-PROV1"
        }
    }
]
```
#### <a name="associations-in-search-result"></a> Associations in the search result

Latest revisions of associations can be queried by searching for one of the associated concepts using either the JSON or UMM_JSON format. The "associations" field, contains a list of concept ids associated to the concept that was searched for grouped by concept-type. The "association-details" field contains concept ids, as well as association details like revision ids and data payloads if they were included for the particular association. In the example case, a service (S1200000010-PROV1) has been associated to a variable with association details(V1200000012-PROV1), as well as a tool (TL1200000014-PROV1) without any association details.

Note: If you update an association with new data payload information, a new revision of that association will be created. Old revisions of the association will be preserved in the database but won't show up in the search result of the associated concepts.

 __Example__

```
curl -H "CMR-Pretty:true" -H "Authorization Bearer: XXXXX" "%CMR-ENDPOINT%/services.json?concept_id=S1200000010-PROV1"

```

 __Example Response with associated concepts__

```
{
  "hits" : 1,
  "took" : 36,
  "items" : [ {
    "concept_id" : "S1200000010-PROV1",
    "revision_id" : 1,
    "provider_id" : "PROV1",
    "native_id" : "newservice",
    "name" : "NSIDC_ECS_EGI_API_SMAP_L1L2",
    "long_name" : "NSIDC DAAC Programmatic Access Service for Customized Data Access",
    "associations" : {
      "tools" : [ "TL1200000014-PROV1" ],
      "variables" : [ "V1200000012-PROV1" ]
    },
    "association_details" : {
      "tools" : [ {
        "concept_id" : "TL1200000014-PROV1"
      } ],
      "variables" : [ {
        "data" : {
          "field" : "value",
          "another_field" : {
            "XYZ" : "ZYX"
          }
        },
        "concept_id" : "V1200000012-PROV1"
      } ]
    }
  } ]
}
```

#### <a name="concept-dissociations"></a> Concept dissociations

A concept, with optional revision id, can be dissociated from one or more other concepts, with optional revision ids.

Expected Response Status:
- 200 OK -- if all dissociations succeeded
- 207 MULTI-STATUS -- if some dissociations succeeded and some failed due to user error
- 400 BAD REQUEST -- if all dissociations failed due to user error

 __Example__

```
curl -XDELETE \
	-H "CMR-Pretty:true" \
	-H "Authorization: Bearer XXXXX" \
	-H "Content-Type:application/json" \
  "%CMR-ENDPOINT%/associate/S1200000010-PROV1" \
  -d '[{"concept_id": "TL1200000014-PROV1"},
           {"concept_id": "V1200000012-PROV1"}]'
```

 __200 OK Example Response__

```
[ {
  "generic_association" : {
    "concept_id" : "GA1200000015-CMR",
    "revision_id" : 4
  },
  "associated_item" : {
    "concept_id" : "TL1200000014-PROV1"
  }
}, {
  "generic_association" : {
    "concept_id" : "GA1200000016-CMR",
    "revision_id" : 4
  },
  "associated_item" : {
    "concept_id" : "V1200000012-PROV1"
  }
} ]
```