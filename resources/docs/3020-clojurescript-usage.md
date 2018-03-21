#  • ClojureScript Usage

Start a the figwheel REPL:

```bash
$ rlwrap lein figwheel
```

Then open up a browser to
[http://localhost:3449/dev.html][local-web-repl] so that the REPL can
connect to it.

Back in the terminal where you started the REPL:
```clj
(require '[cmr.client.ingest :as ingest]
         '[cmr.client.common.util :refer [with-callback]])
(def client (ingest/create-client))
(def ch (ingest/get-providers client))
```

The ClojureScript API uses the cljs-http library, so requests return a
`core.async` channel. However, if you don't want to work with channels, you
can easily process results in a callback:

```clj
(with-callback ch #(println "Got response:" %))
```
Which gives:
```clj
Got response: {:status 200, :success true, :body [{:provider-id LARC_ASDC ...
```

If you don't need the full response and are just after the data, you can
do the same thing that the Clojure CMR client API does when creating the
client:

```clj
(def client (ingest/create-client {:return-body? true}))
(def ch (ingest/get-providers client))
(with-callback ch #(println "Got body:" %))
```
```
Got body: [{:provider-id LARC_ASDC, :short-name LARC_ASDC ...}
```


<!-- Named page links below: /-->

[local-web-repl]: http://localhost:3449/dev.html
