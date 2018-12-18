(ns cmr.nlp.elastic.client.document
  (:require
   [cheshire.core :as json]
   [taoensso.timbre :as log])
  (:import
   (clojure.lang Keyword)
   (java.lang String)
   (org.elasticsearch.action.bulk BulkRequest)
   (org.elasticsearch.action.delete DeleteRequest)
   (org.elasticsearch.action.index IndexRequest)
   (org.elasticsearch.action.update UpdateRequest)
   (org.elasticsearch.common.xcontent XContentType))
  (:refer-clojure :exclude [index update]))

(defn index
  [^String source ^String index-name ^String doc-type ^String doc-id]
  (let [req (new IndexRequest index-name doc-type doc-id)]
    (.source req source XContentType/JSON)
    req))

(defn delete
  [^String index-name ^String doc-type ^String doc-id]
  (new DeleteRequest index-name doc-type doc-id))

(defn update
  [^String source ^String index-name ^String doc-type ^String doc-id]
  (let [req (new UpdateRequest index-name doc-type doc-id)]
    (.doc req source XContentType/JSON)
    req))

(defn upsert
  [^String source ^String index-name ^String doc-type ^String doc-id]
  (let [req (new UpdateRequest index-name doc-type doc-id)]
    (.upsert req source XContentType/JSON)
    req))

(defn- request
  [^Keyword request-type & args]
  (case request-type
    :index (apply index args)
    :delete (apply delete args)
    :update (apply update args)
    :upsert (apply upsert args)))

(defn bulk
  [reqs]
  (let [bulk-req (new BulkRequest)]
    (doseq [r reqs]
      (.add bulk-req r))
    bulk-req))
