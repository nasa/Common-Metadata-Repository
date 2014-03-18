(ns ^{:doc "helper to provide the urls to various service endpoints"}
  cmr-system-int-test.url-helper
  (:require [ring.util.codec :as codec]))

(def url_root "http://localhost:10000")
(def elastic_root "http://localhost:9200")

(defn collection-ingest-url
  [{:keys [provider-id dataset-id]}]
  (format "%s/catalog-rest/providers/%s/datasets/%s"
          url_root
          provider-id
          dataset-id))

(defn collection-search-url
  [params]
  (str "http://localhost:3003/collections?" (codec/form-encode params)))

(defn index-catalog-url
  []
  (str url_root "/catalog-rest/index/catalog"))

(defn elastic-flush-url
  []
  (str elastic_root "/_flush"))
