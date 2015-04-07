# cubby-app

The cubbt app provides a centralized caching service for the CMR. Most caching in the CMR should be done in each application in separate in memory cache. Sometimes a single central cache will be needed. The cubby application provides that capability.


## API

TODO

  * /keys
    * GET - fetch all keys
    * /:key
      * GET - Fetch value associated with a key
      * PUT - Set a value associated with a key
      * DELETE - remove a key
  * /reset
    * POST - Clear the cache


## License

Copyright © 2015 NASA
