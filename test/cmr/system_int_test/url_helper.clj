(ns ^{:doc "helper to provide the urls to various service endpoints"}
  cmr.system-int-test.url-helper
  (:require [ring.util.codec :as codec]))

(def elastic_root "http://localhost:9210")

(defn collection-ingest-url
  [provider-id native-id]
  (format "http://localhost:3002/providers/%s/collections/%s"
          provider-id
          native-id))

(defn collection-search-url
  [params]
  (str "http://localhost:3003/collections?" (codec/form-encode params)))

(defn elastic-flush-url
  []
  (str elastic_root "/_flush"))

(defn mdb-endpoint
  "Returns the host and port of metadata db"
  []
  {:host "localhost"
   :port "3001"})

(defn indexer-endpoint
  "Returns the host and port of indexer app"
  []
  {:host "localhost"
   :port "3004"})

(defn construct-ingest-rest-url
  "Construct ingest url based on concept."
  [concept]
  (let [host "localhost"
        port 3002
        {:keys [provider-id concept-type native-id ]} concept
        ctx-part (str "providers" "/" provider-id  "/" "collections" "/" native-id )
        ingest-rest-url (str "http://" host ":" port "/" ctx-part)]
    ingest-rest-url))

