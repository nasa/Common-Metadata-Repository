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
          "cubby": {
            "ok?": true,
            "dependencies": {
              "elastic_search": {
                "ok?": true
              },
              "echo": {
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
          "index-set": {
            "ok?": true,
            "dependencies": {
              "elastic_search": {
                "ok?": true
              },
              "echo": {
                "ok?": true
              }
            }
          },
          "rabbit-mq": {
            "ok?": true
          }
        }
      },
      "rabbit-mq": {
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
  "rabbit-mq": {
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
          "cubby": {
            "ok?": true,
            "dependencies": {
              "elastic_search": {
                "ok?": true
              },
              "echo": {
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
          "index-set": {
            "ok?": true,
            "dependencies": {
              "elastic_search": {
                "ok?": true
              },
              "echo": {
                "ok?": true
              }
            }
          },
          "rabbit-mq": {
            "ok?": true
          }
        }
      },
      "rabbit-mq": {
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
  "rabbit-mq": {
    "ok?": true
  }
}
```

***

## License

Copyright © 2015 NASA
