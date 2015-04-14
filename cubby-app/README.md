# cubby-app

The cubby app provides a centralized caching service for the CMR. Most caching in the CMR should be done in each application in separate in-memory cache. Sometimes a single central cache will be needed. The cubby application provides that capability.
## API

### Overview

  * **/keys**
    * **GET** - fetch all keys.
    * **DELETE** - TODO make this clear the cache
    * **/:key**
      * **GET** - Fetch value associated with a key
      * **PUT** - Set a value associated with a key
      * **DELETE** - remove a key
  * **/reset**
    * **POST** - Recreates indexes dropping all data.


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


## Admin API

TODO more about creating and updating indexes


What do we want for migrating/index creation

  * single API that any application could expose for migrating the database or elasticsearch
  * If the elasticsearch index doesn't exist yet it creates it
  * If the elasticsearch index exists it updates the mappings (if needed)
  * What about testing this? Should we have a delete index api?
    * Yes
  * What about during deployment of cubby? It will be running and won't have an index.
    * If an set value request comes in elastic will automatically create the index
      * TODO file earthdata infrastructure issue to stop this
    * Cubby read requests will naturally fail.
    * Cubby will check on every write request that it's index exist.
      * This is temporary until the new issue mentioned above is fixed.
      * TODO file issue to reverse this and make it dependent on the other issue.
    * The indexer will fail on reading or writing to cubby until migrated. We will allow this to fail.
      * All uses of the indexer will retry after some period of time.

### Migrate

    curl -XPOST http://localhost:3007/admin/migrate

or

    CMR_ELASTIC_HOST=localhost CMR_ELASTIC_PORT=9200 java -cp target/cmr-cubby-app-0.1.0-SNAPSHOT-standalone.jar cmr.db migrate


So we actually prefer the last one here. Jon likes it best of all as do Chris and I. I won't expose an API to do these things. I'll manually test it.

TODO add cmr.db migration


### Delete index

    curl -XPOST http://localhost:3007/admin/delete-index




### Reset

Drops and recreates the indexes that save cubby data.

    curl -XPOST http://localhost:3007/reset


## License

Copyright © 2015 NASA
