(ns ^{:doc "helper to provide the urls to various service endpoints"}
  cmr-system-int-test.url-helper
  (:require [ring.util.codec :as codec]))

(def elastic_root "http://localhost:9200")

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
