#  • Clojure Usage

```bash
$ lein repl
```
```clj
(def client (ingest/create-client))
(def results (ingest/get-providers client))
(pprint results)
```
```clj
{:request-time 1007,
 :repeatable? false,
 :protocol-version {:name "HTTP", :major 1, :minor 1},
 :streaming? true,
 :chunked? false,
 :reason-phrase "OK",
 :headers
 {"CMR-Request-Id" "5b2b1090-bd3f-4776-a903-168c34f7a44c",
  "Content-Type" "application/json; charset=utf-8",
  "Date" "Sun, 27 Aug 2017 06:25:06 GMT",
  "Server" "nginx",
  "Strict-Transport-Security" "max-age=31536000; includeSubDomains;",
  "Vary" "Accept-Encoding, User-Agent",
  "Connection" "keep-alive"},
 :orig-content-encoding nil,
 :status 200,
 :length -1,
 :body
 [{:provider-id "LARC_ASDC",
   :short-name "LARC_ASDC",
   :cmr-only false,
   :small false}
   ...]}
```

Or, if you just want the body:

```clj
(def client (ingest/create-client {:return-body? true}))
(def results (ingest/get-providers client))
(pprint results)
```
```clj
[{:provider-id "LARC_ASDC",
  :short-name "LARC_ASDC",
  :cmr-only false,
  :small false}
 {:provider-id "USGS_EROS",
  :short-name "USGS_EROS",
  :cmr-only true,
  :small false}
 ...]
```

Run against a local instance instead of production:

```clj
(def client (ingest/create-client {:endpoint :local :return-body? true}))
(def results (ingest/get-providers client))
(pprint results)
```

Create a collection:

```clj
(def client (ingest/create-client {:endpoint :local
                                   :token "mock-echo-system-token"
                                   :return-body? true}))
(def xml-file "/Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/collections/GES_DISC/AIRX3STD_006.xml")
(def results (ingest/create-collection client
                                       "GES_DISC"
                                       "coll-native-id"
                                       (slurp xml-file)
                                       {:content-type "application/echo10+xml"
                                        :accept "application/json"}))
(print results)
```

Check the collection:

```clj
(def client (search/create-client {:endpoint :local :return-body? true}))
(def results (search/get-collections client {:accept "application/json"}))
```
