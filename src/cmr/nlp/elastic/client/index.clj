(ns cmr.nlp.elastic.client.index
  (:require
   [cheshire.core :as json]
   [taoensso.timbre :as log])
  (:import
   (java.lang String)
   (org.elasticsearch.action.admin.indices.create CreateIndexRequest)
   (org.elasticsearch.action.admin.indices.delete DeleteIndexRequest)
   (org.elasticsearch.common.xcontent XContentType)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Indexing API Wrappers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create
  ([^String index-name]
    (log/debugf "Creating index with name '%s' ..." index-name)
    (new CreateIndexRequest index-name))
  ([^String index-name ^String source]
    (log/debugf "Creating index with name '%s' and source '%s' ..."
               index-name
               source)
    (let [req (new CreateIndexRequest index-name)]
      (.source req source XContentType/JSON)
      req)))

(defn delete
  [^String index-name]
  (new DeleteIndexRequest index-name))
