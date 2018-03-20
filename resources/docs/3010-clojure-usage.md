#  • Clojure Usage

Fire up the dev REPL:
```bash
$ lein repl
```

Create some clients:

```clj
(def ingest-client (ingest/create-client))
(def search-client (search/create-client))
```

Get a list of providers:

```clj
(def results (ingest/get-providers ingest-client))
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
(def ingest-client (ingest/create-client {:return-body? true}))
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

Note that a subequent release of the CMR client will support changing the
`:return-body?` value on a per-call basis and won't require instantiating
a new client.

Run against a local instance instead of production:

```clj
(def ingest-client (ingest/create-client {:endpoint :local :return-body? true}))
(def results (ingest/get-providers ingest-client))
(pprint results)
```

Create a collection:

```clj
(def authed-ingest-client (ingest/create-client {:endpoint :local
                                                 :token "mock-echo-system-token"
                                                 :return-body? true}))
(def xml-file "/Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/collections/GES_DISC/AIRX3STD_006.xml")
(def results (ingest/create-collection authed-ingest-client
                                       "GES_DISC"
                                       "coll-native-id"
                                       (slurp xml-file)
                                       {:content-type "application/echo10+xml"
                                        :accept "application/json"}))
(print results)
```

Check the collection:

```clj
(def results (search/get-collections search-client {:accept "application/json"}))
```

Create a variable:

```clj
(def json-file "/Users/dmcgregg/lab/NASA/CMR/cmr-edsc-stubs/resources/data/variables/GES_DISC/AIRX3STD/CH4/CH4_VMR_A.json")
(def submit-content-type "application/vnd.nasa.cmr.umm+json;version=1.0; charset=UTF-8")
(def accept-content-type "application/json")
(def results (ingest/create-variable authed-ingest-client
                                     "GES_DISC"
                                     "var-native-id"
                                     (slurp json-file)
                                     {:content-type submit-content-type
                                      :accept accept-content-type}))
(or (pprint (:errors results)) (pprint results))
```

Associate a variable with a collection:

```clj
(require '[clojure.data.json :as json])
(def authed-search-client (search/create-client {:endpoint :local
                                                 :token "mock-echo-system-token"
                                                 :return-body? true}))
(def submit-content-type "application/json")
(def accept-content-type "application/json")
(def results (search/create-variable-association
              authed-search-client
              "V1200000117-GES_DISC"
              (json/write-str
               [{:concept_id "C1200000100-GES_DISC"}])
              {:content-type submit-content-type
               :accept accept-content-type}))
(or (pprint (:errors results)) (pprint results))
```

curl -XPOST -i \
  -H "Content-Type: application/json" \
  -H "Echo-Token: mock-echo-system-token" \
  http://localhost:3003/variables/V1200000117-GES_DISC/associations \
  -d '[{"concept_id": "C1200000100-GES_DISC"}]'
