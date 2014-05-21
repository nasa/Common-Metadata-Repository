(ns ^{:doc "helper to provide the urls to various service endpoints"}
  cmr.system-int-test.utils.url-helper
  (:require [clojure.string :as str]
            [cmr.common.config :as config]))

(def elastic-port (config/config-value-fn :elastic-port 9210))

(def metadata-db-port (config/config-value-fn :metadata-db-port 3001))

(def ingest-port (config/config-value-fn :ingest-port 3002))

(def indexer-port (config/config-value-fn :indexer-port 3004))

(def search-port (config/config-value-fn :search-port 3013))

(defn elastic-root
  []
  (format "http://localhost:%s" (elastic-port)))

(defn create-provider-url
  []
  (format "http://localhost:%s/providers" (metadata-db-port)))

(defn delete-provider-url
  [provider-id]
  (format "http://localhost:%s/providers/%s" (metadata-db-port) provider-id))

(defn ingest-url
  [provider-id type native-id]
  (format "http://localhost:%s/providers/%s/%ss/%s"
          (ingest-port)
          provider-id
          (name type)
          native-id))

(defn search-url
  [type]
  (format "http://localhost:%s/%ss" (search-port) (name type)))

(defn search-reset-url
  "Clear cache in search app. Only development team to use this functionality."
  []
  (format "http://localhost:%s/reset" (search-port)))

(defn retrieve-concept-url
  [type concept-id]
  (format "http://localhost:%s/concepts/%s" (search-port) concept-id))

(defn elastic-refresh-url
  []
  (str (elastic-root) "/_refresh"))

(defn mdb-concept-url
  "URL to concept in mdb."
  ([concept-id]
   (mdb-concept-url concept-id nil))
  ([concept-id revision-id]
   (if revision-id
     (format "http://localhost:%s/concepts/%s/%s" (metadata-db-port) concept-id revision-id)
     (format "http://localhost:%s/concepts/%s" (metadata-db-port) concept-id))))

(defn mdb-reset-url
  "Force delete all concepts from mdb."
  []
  (format "http://localhost:%s/reset" (metadata-db-port)))

(defn indexer-reset-url
  "Delete and re-create indexes in elastic. Only development team to use this functionality."
  []
  (format "http://localhost:%s/reset" (indexer-port)))

;; discard this once oracle impl is in place
(defn mdb-concept-coll-id-url
  "URL to access a collection concept in mdb with given prov and native id."
  [provider-id native-id]
  (format "http://localhost:%s/concept-id/collection/%s/%s"
          (metadata-db-port)
          provider-id
          native-id))


