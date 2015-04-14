# cubby-app

The cubby app provides a centralized caching service for the CMR. Most caching in the CMR should be done in each application in separate in-memory cache. Sometimes a single central cache will be needed. The cubby application provides that capability.
## API

### Overview

  * **/keys**
    * **GET** - fetch all keys.
    * **DELETE** - Deletes all cached values
    * **/:key**
      * **GET** - Fetch value associated with a key
      * **PUT** - Set a value associated with a key
      * **DELETE** - remove a key
  * **/reset**
    * **POST** - Recreates indexes dropping all data.
  * **/health**
    * **GET** - Returns the health of the application
  * **/caches**
    * **GET** - Returns a list of the in memory caches.
    * **/:cache-name**
      * **GET** - Gets the keys of the given cache
      * **/:cache-key**
        * **GET** - Fetches value of item cached with this key.
    * **/clear-caches**
      * **POST** - Clear caches


### Set Value

Associates a value with a key

    curl -i -XPUT -H "Content-Type: text" http://localhost:3007/keys/my-key-name -d 'save this value'

### Get Value

Retrieves a value associated with a key.

```
curl -i http://localhost:3007/keys/my-key-name
HTTP/1.1 200 OK

save this value
```


### Get All Keys

Gets a list of all the keys that have been saved as a JSON array.

```
curl -i http://localhost:3007/keys
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8

["my-key-name"]
```

### Delete All Keys

Deletes all the cached keys.

    curl -i -XDELETE http://localhost:3007/keys

## Admin API

TODO add cmr.db migration

### Reset

Drops and recreates the indexes that save cubby data.

    curl -XPOST http://localhost:3007/reset

### Querying caches

Endpoints are provided for querying the contents of the various in memory caches used by the application.
The following curl will return the list of caches:

    curl -i http://localhost:3007/caches

The following curl will return the keys for a specific cache:

    curl -i http://localhost:3007/caches/cache-name

This curl will return the value for a specific key in the named cache:

    curl -i http://localhost:3007/caches/cache-name/cache-key

### Check application health

This will report the current health of the application. It checks all resources and services used by the application and reports their healthes in the response body in JSON format. For resources, the report includes an "ok?" status and a "problem" field if the resource is not OK. For services, the report includes an overall "ok?" status for the service and health reports for each of its dependencies. It returns HTTP status code 200 when the application is healthy, which means all its interfacing resources and services are healthy; or HTTP status code 503 when one of the resources or services is not healthy. It also takes pretty parameter for pretty printing the response.

    curl -i -XGET "http://localhost:3007/health?pretty=true"

Example healthy response body:

```
{
  "elastic_search" : {
    "ok?" : true
  },
  "echo" : {
    "ok?" : true
  }
}
```

Example un-healthy response body:

```
{
  "elastic_search" : {
    "ok?" : false,
    "problem" : {
      "status" : "Inaccessible",
      "problem" : "Unable to get elasticsearch cluster health, caught exception: Connection refused"
    }
  },
  "echo" : {
    "ok?" : true
  }
}
```

## License

Copyright © 2015 NASA
