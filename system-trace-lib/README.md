# cmr-system-trace

Provides library functions for tracing requests throughout the CMR system. Built on Twitter Zipkin and clj-zipkin.

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

