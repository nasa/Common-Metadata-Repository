# cmr-system-trace

Provides library functions for tracing requests throughout the CMR system. Built on Twitter Zipkin and clj-zipkin.


## Context

It also defines the context object used in the CMR. It's expected that the context will be passed around applications functions as the first argument (where necessary). The context object is a map containing keywords and information about the current request. The following list of keys has been defined. Because the context is just a map different applications can put other information in it like the current user token or start time of a request.

  * :system - the system map of the application currently in use
  * :request - Information related to the current request.
    * :request-id - Contains the request id. (See below)
    * :trace-info - Contains the identifiers used by zipkin for traced operations. See cmr.system-trace.context documentation for more information.

### Request Id

The request id is a unique identifier for a request. It is generated on every request as a Java UUID and logged with any log messages. The request id is sent on requests to other services with the header "cmr-request-id". Any CMR service receiving this will parse it from the header, put it in the context, and use it in log messages. This will allow searching the logs across all services with a single id.

## Useful links to read

  * Zipkin
    * https://blog.twitter.com/2012/distributed-systems-tracing-with-zipkin
    * http://www.infoq.com/presentations/Zipkin
    * https://github.com/twitter/zipkin
    * https://github.com/twitter/zipkin/blob/master/doc/instrument.md
      * A good overview of object model used in Zipkin
    * Dapper http://research.google.com/pubs/pub36356.html
      * Paper that inspired Zipkin
  * clj-zipkin
    * http://blog.guillermowinkler.com/blog/2013/11/28/zipkin-distributed-tracing-using-clojure/
    * https://github.com/guilespi/clj-zipkin


## Installing Zipkin

Contents from https://github.com/twitter/zipkin/blob/master/doc/install.md

  - `git clone https://github.com/twitter/zipkin.git && cd zipkin`
  - Run bin/collector
    - This will download the entire internet via maven. It takes about 45 mins the first time.
  - Start "query" and "web" in separate bash windows after bin/collector finishes installing and is running.

## Running Zipkin

Contents from https://github.com/twitter/zipkin/blob/master/doc/install.md

In three separate bash windows run:

```
bin/collector
bin/query
bin/web
```

Now you can access the Zipkin UI at http://localhost:8080/

## License

Copyright © 2014 NASA

