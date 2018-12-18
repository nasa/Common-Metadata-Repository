(ns cmr.nlp.elastic.client.search
  (:require
   [taoensso.timbre :as log])
  (:import
   (java.lang String)
   (org.elasticsearch.action.search SearchRequest)
   (org.elasticsearch.index.query QueryBuilders)
   (org.elasticsearch.common.xcontent XContentType)
   (org.elasticsearch.search.builder SearchSourceBuilder))
  (:refer-clojure :exclude [get]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Search API Wrappers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn search
  [& index-names]
  (if (nil? index-names)
    (new SearchRequest)
    (new SearchRequest (into-array index-names))))

(defn match-all
  [index-name]
  (let [builder (new SearchSourceBuilder)
        req (search index-name)]
    (.query builder (QueryBuilders/matchAllQuery))
    (.source req builder)
    req))

(defn match
  [index-name field-name term]
  (let [builder (new SearchSourceBuilder)
        req (search index-name)]
    (.query builder (QueryBuilders/matchQuery field-name term))
    (.source req builder)
    req))

(defn term
  [index-name field-name term]
  (let [builder (new SearchSourceBuilder)
        req (search index-name)]
    (.query builder (QueryBuilders/termQuery field-name term))
    (.source req builder)
    req))
