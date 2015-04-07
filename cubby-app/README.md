# cubby-app

The cubby app provides a centralized caching service for the CMR. Most caching in the CMR should be done in each application in separate in memory cache. Sometimes a single central cache will be needed. The cubby application provides that capability.


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

    curl -i -XPUT -H "Content-Type: text" http://localhost:3007/keys/my-key-name -d 'save this value'

Associates a value with a key

### Get Value

```
curl -i http://localhost:3007/keys/my-key-name
HTTP/1.1 200 OK
Date: Tue, 07 Apr 2015 20:06:29 GMT
Content-Length: 15
Server: Jetty(7.6.13.v20130916)

save this value
```

Retrieves a value associated with a key.


### Get All Keys

```
curl -i http://localhost:3007/keys
HTTP/1.1 200 OK
Date: Tue, 07 Apr 2015 20:07:17 GMT
Content-Type: application/json; charset=utf-8
Content-Length: 15
Server: Jetty(7.6.13.v20130916)

["my-key-name"]
```

Gets a list of all the keys that have been saved as a JSON array.

### Reset

curl -XPOST http://localhost:3007/reset

Clears out everything from the cache.

## License

Copyright © 2015 NASA
