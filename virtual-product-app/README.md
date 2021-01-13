# virtual-product-app

The Virtual Product Application adds the concept of Virtual Products to the CMR. Virtual Products
represent products at a data provider that are generated on demand from users when they are ordered
or downloaded through a URL. A data provider will create a virtual product collection in the CMR.
Then within the Virtual Product App the virtual product will be configured with a source collection.
Whenever any granule is ingested in the source collection an equivalent ingest will be sent for each
of the virtual products that are configured with that source collection.

Virtual products will be supported during the ordering process as well. When a virtual product is
ordered ECHO will use the Virtual Product Apps API to convert virtual product order item ids into
the source ids and send them to the provider.

## Administrative API

### Administrative API Overview

  * /health
    * [GET - Gets the health of the virtual product application.](#application-health)


### <a name="application-health"></a> Application Health

This will report the current health of the application. It checks all resources and services used by the application and reports their health status in the response body in JSON format. The report includes an "ok?" status and a "problem" field for each resource. The report includes an overall "ok?" status and health reports for each of a service's dependencies. It returns HTTP status code 200 when the application is healthy, which means all its interfacing resources and services are healthy; or HTTP status code 503 when one of the resources or services is not healthy. It also takes pretty parameter for pretty printing the response.

    curl -i -XGET %CMR-ENDPOINT%/health?pretty=true

Example healthy response body:

```
{
  "ingest": {
    "ok?": true,
    "dependencies": {
      "oracle": {
        "ok?": true
      },
      "echo": {
        "ok?": true
      },
      "metadata-db": {
        "ok?": true,
        "dependencies": {
          "oracle": {
            "ok?": true
          },
          "echo": {
            "ok?": true
          }
        }
      },
      "indexer": {
        "ok?": true,
        "dependencies": {
          "elastic_search": {
            "ok?": true
          },
          "echo": {
            "ok?": true
          },
          "metadata-db": {
            "ok?": true,
            "dependencies": {
              "oracle": {
                "ok?": true
              },
              "echo": {
                "ok?": true
              }
            }
          },
          "message-queue": {
            "ok?": true
          }
        }
      },
      "message-queue": {
        "ok?": true
      }
    }
  },
  "metadata-db": {
    "ok?": true,
    "dependencies": {
      "oracle": {
        "ok?": true
      },
      "echo": {
        "ok?": true
      }
    }
  },
  "message-queue": {
    "ok?": true
  }
}
```

Example unhealthy response body:

```
{
  "ingest": {
    "ok?": true,
    "dependencies": {
      "oracle": {
        "ok?" : false,
    	"problem" : "Exception occurred while getting connection: oracle.ucp.UniversalConnectionPoolException: Cannot get Connection from Datasource: java.sql.SQLRecoverableException: IO Error: The Network Adapter could not establish the connection"
      },
      "echo": {
        "ok?": true
      },
      "metadata-db": {
        "ok?": true,
        "dependencies": {
          "oracle": {
            "ok?": true
          },
          "echo": {
            "ok?": true
          }
        }
      },
      "indexer": {
        "ok?": true,
        "dependencies": {
          "elastic_search": {
            "ok?": true
          },
          "echo": {
            "ok?": true
          },
          "metadata-db": {
            "ok?": true,
            "dependencies": {
              "oracle": {
                "ok?": true
              },
              "echo": {
                "ok?": true
              }
            }
          },
          "message-queue": {
            "ok?": true
          }
        }
      },
      "message-queue": {
        "ok?": true
      }
    }
  },
  "metadata-db": {
    "ok?": true,
    "dependencies": {
      "oracle": {
        "ok?": true
      },
      "echo": {
        "ok?": true
      }
    }
  },
  "message-queue": {
    "ok?": true
  }
}
```

### <a name="translate-granule-entries"></a> Translate Granule Entries

Translate virtual granule entries within a list of granule-entries into the corresponding source granule entries. The list of granule-entries should be supplied in the request body as a JSON with this schema:

```
{"$schema" "http://json-schema.org/draft-04/schema#"
"title" "Granule Entries"
"description" "Input request from ECHO ordering service for translating virtual granule entries to the corresponding source granule entries"
"type" "array"
"items" {"title" "A granule entry in the order"
         "type" "object"
         "properties" {"concept-id" {"type" "string"}
                       "entry-title" {"type" "string"}
                       "granule-ur" {"type" "string"}}
         "required" ["concept-id" "entry-title" "granule-ur"]}}
```

The response body has the same schema but with virtual granule entries substituted by the corresponding source granule entries and non-virtual entries remaining unchanged. The ordering of the entries in the response remains the same as the corresponding entries in the request. Note that there can be duplicate entries in the response since multiple virtual granules could be mapped to the same source granule. If the request has an entry for a granule which is deleted (or non-existent), the entry is translated to "null" in the JSON response. If the granule-ur of an entry is null, it is considered a collection entry and is ignored during translation. This end-point will be called by ECHO during ordering process.

Sample Request:

```
curl -i -XPOST %CMR-ENDPOINT%/translate-granule-entries -d
[{"granule-ur":"foo","concept-id":"G5-PROV1","entry-title":"A dataset"},
 {"granule-ur":"a virtual granule","concept-id":"G7-LPDAAC_ECS","entry-title":"Some virtual granule dataset"},
 {"granule-ur":"bar","concept-id":"G6-PROV1","entry-title":"A dataset"},
 {"granule-ur":"another virtual granule in the same dataset","concept-id":"G8-LPDAAC_ECS","entry-title":"Some virtual granule dataset"},
 {"granule-ur":"yet an another virtual granule","concept-id":"G9-LPDAAC_ECS","entry-title":"A virtual dataset with same source as G7-LPDAAC_ECS"},
 {"granule-ur":"null","concept-id":"C1-PROV1","entry-title":"A dataset"}]
```

Response:

```
[{"granule-ur":"foo","concept-id":"G5-PROV1","entry-title":"A dataset"},
 {"granule-ur":"the source granule","concept-id":"G1-LPDAAC_ECS","entry-title":"The source dataset"},
 {"granule-ur":"bar","concept-id":"G6-PROV1","entry-title":"A dataset"},
 {"granule-ur":"another source granule","concept-id":"G2-LPDAAC_ECS","entry-title":"The source dataset"},
 {"granule-ur":"the source granule","concept-id":"G1-LPDAAC_ECS","entry-title":"The source dataset"},
 {"granule-ur":"null","concept-id":"C1-PROV1","entry-title":"A dataset"}]
```

***

## License

Copyright © 2021 NASA
