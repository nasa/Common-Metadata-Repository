(ns cmr.nlp.elastic.client.pipeline
  (:require
   [cheshire.core :as json]
   [taoensso.timbre :as log])
  (:import
   (java.lang String)
   (org.elasticsearch.action.ingest DeletePipelineRequest
                                    GetPipelineRequest
                                    PutPipelineRequest)
   (org.elasticsearch.common.xcontent XContentType))
  (:refer-clojure :exclude [get]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Indexing API Wrappers   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get
  [^String pipeline-id]
  (new GetPipelineRequest pipeline-id))

(defn put
  ([^String pipeline-id ^String source]
    (put pipeline-id source :json))
  ([^String pipeline-id ^String source content-type]
    (log/debugf "Putting pipeline with id '%s' and source '%s' ..."
                pipeline-id
                source)
    (new PutPipelineRequest pipeline-id
                            (byte-array (.getBytes source))
                            (case content-type
                              :json XContentType/JSON))))

(defn delete
  [^String pipeline-id]
  (new DeletePipelineRequest pipeline-id))
