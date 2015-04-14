# cubby-app

The cubby app provides a centralized caching service for the CMR. Most caching in the CMR should be done in each application in separate in-memory cache. Sometimes a single central cache will be needed. The cubby application provides that capability.


## API

### Overview

  * **/keys**
    * **GET** - fetch all keys.
    * **/:key**
      * **GET** - Fetch value associated with a key
      * **PUT** - Set a value associated with a key
      * **DELETE** - remove a key
  * **/reset**
    * **POST** - Clear the cache


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

### Reset

Clears out everything from the cache.

    curl -XPOST http://localhost:3007/reset


## License

Copyright © 2015 NASA
