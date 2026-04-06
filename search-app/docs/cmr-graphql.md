### <a name="cmr-graphql"></a> CMR-Graphql
The CMR has a GraphQL API which can be queried with requests to [%GRAPHQL-ENDPOINT%](%GRAPHQL-ENDPOINT%). The GraphQL API provides an alternative query language for the CMR REST API. The [cmr-graphql](https://github.com/nasa/cmr-graphql) API can retrieve individually requested fields from resources. For example a request with a list of specified fields from a collection would return the values for those fields but, not any other fields on the usual collections response from CMR. The [cmr-graphql](https://github.com/nasa/cmr-graphql) can retrieve data from multiple resources from a single client request so in a single request, data from multiple resources such as the CMR REST API or the [cmr-graphDb](https://github.com/nasa/Common-Metadata-Repository/tree/master/graph-db) instance within a single client request.

### <a name="apollo-studio"></a> Navigating Apollo Studio
Navigating to [%GRAPHQL-ENDPOINT%](%GRAPHQL-ENDPOINT%) on the browser providers an interface through the Apollo Studio services on which the CMR API can be queried. The User interface providers autocomplete, concept schema definitions, and request/response handling using the GraphQL language.

### <a name="cmr-graphql-programmatic-requests"></a> Programmatic requests to CMR-Graphql

The [cmr-graphql](https://github.com/nasa/cmr-graphql) API can handle POST requests from external clients (In the GraphQL query language all requests must be POST requests). A 'Content-Type' with the value 'application/json' must be specified in the header of the request. The body must be valid JSON and should contain the request information you are looking for. The examples below use the `curl` tool. Integration with [cmr-graphql](https://github.com/nasa/cmr-graphql) will vary in implementation by individual programming languages and frameworks which may implement their own libraries or wrappers for GraphQL requests. The Apollo Studio providers, under the more info button (ellipsis button), a `Copy Operation to cURL` which can be used to copy an equivalent `curl` to the query in the interface.

 __Example of retrieving the count from the first page of collections__

```

curl -XPOST '%GRAPHQL-ENDPOINT%' \
-H 'Content-Type: application/json' \
-H 'Authorization: Bearer XXXXX' \
-d '{"query":"{ collections{ count}}"}'

```

__Response__

```
{
  "data": {
    "collections": {
      "count": 94455
    }
  }
}
```

 __Example of retrieving the concept-ids for services associated to a specified collection__

 ```
 curl -XPOST '%GRAPHQL-ENDPOINT%' \
    -H "content-type: application/json" \
    -H "Authorization: XXXXX" \
    -d '{
        "query": "query ($params: CollectionsInput) {collections(params: $params) {items {conceptId services {items {conceptId}}}}}",
        "variables": {
            "params": {
                "conceptId": "C1200000000-PROV1"
            }
        }
    }'
  ```

__Response__

```
{
  "data": {
    "collections": {
      "items": [
        {
          "conceptId": "C1200000000-PROV1",
          "services": {
            "items": [
              {
                "conceptId": "S1200000000-PROV1"
              }
            ]
          }
        }
      ]
    }
  }
}
```



 __Example: Retrieving variables associated to specified collection with a limit for variables returned__

 ```
 curl -XPOST '%GRAPHQL-ENDPOINT%' \
    -H "content-type: application/json" \
    -H "Authorization: XXXXX" \
    -d '{
    "query": "query ($params: CollectionsInput) {collections(params: $params){items{conceptId services{items{conceptId}}}}}",
    "variables": {
        "params": {
            "limit": 2,
            "conceptId": "C1200000000-PROV1"
        },
        "variablesParams2": {
            "limit": 2
        }
    }
}'
```

__Response__

```
{
  "data": {
    "collections": {
      "items": [
        {
          "variables": {
            "items": [
              {
                "conceptId": "V1200000000-PROV1"
              },
              {
                "conceptId": "V1200000001-PROV1"
              }
            ]
          }
        }
      ]
    }
  }
}
```

### <a name="cmr-graphql-addtional-information"></a> Additional Information
The [cmr-graphql](https://github.com/nasa/cmr-graphql) is maintained separately from the CMR REST API, it is expected that new fields added to CMR concepts will lag behind changes to the CMR REST API. Though these should be periodically updated to match the latest changes in the CMR API, requested changes can be made by adding an issue to the [cmr-graphql](https://github.com/nasa/cmr-graphql) repository on
``` https://github.com/nasa/cmr-graphql/issues```.

For additional information on the cmr-graphql as well as more example queries please visit
``` https://github.com/nasa/cmr-graphql#readme ```