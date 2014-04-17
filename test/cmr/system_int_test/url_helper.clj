(ns ^{:doc "helper to provide the urls to various service endpoints"}
  cmr.system-int-test.url-helper
  (:require [ring.util.codec :as codec]))

(def elastic_root "http://localhost:9210")

(def create-provider-url "http://localhost:3001/providers")

(defn delete-provider-url
  [provider-id]
  (format "http://localhost:3001/providers/%s" provider-id))

(defn collection-ingest-url
  [provider-id native-id]
  (format "http://localhost:3002/providers/%s/collections/%s"
          provider-id
          native-id))

(defn collection-search-url
  [params]
  (str "http://localhost:3003/collections?" (codec/form-encode params)))

(defn granule-ingest-url
  [provider-id native-id]
  (format "http://localhost:3002/providers/%s/granules/%s"
          provider-id
          native-id))

(defn granule-search-url
  [params]
  (str "http://localhost:3003/granules?" (codec/form-encode params)))

(defn elastic-flush-url
  []
  (str elastic_root "/_flush"))

(defn mdb-concept-url
  "URL to concept in mdb."
  [concept-id revision-id]
  (format "http://localhost:3001/concepts/%s/%s" concept-id revision-id))

(defn mdb-reset-url
  "Force delete all concepts from mdb."
  []
  (format "http://localhost:3001/reset"))

(defn indexer-reset-url
  "Delete and re-create indexes in elastic. Only development team to use this functionality."
  []
  (format "http://localhost:3004/reset"))

;; discard this once oracle impl is in place
(defn mdb-concept-coll-id-url
  "URL to access a collection concept in mdb with given prov and native id."
  [provider-id native-id]
  (format "http://localhost:3001/concept-id/collection/%s/%s" provider-id native-id))


