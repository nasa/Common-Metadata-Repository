(ns cmr.nlp.elastic.client.listener
  (:require
   [cheshire.core :as json]
   [taoensso.timbre :as log])
  (:import
   (java.lang String)
   (org.elasticsearch.action.support.master AcknowledgedRequest
                                            AcknowledgedResponse)
   (org.elasticsearch.common.xcontent XContentType))
  ;; XXX Once we need more than one class here, let's move :gen-class out of
  ;;     the namespace and just use the macro
  (:gen-class
    :name cmr.nlp.elastic.client.listenerAckListener
    :implements [org.elasticsearch.action.support.master.AcknowledgedRequest]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -onResponse
  [^AcknowledgedResponse response]
  (log/info response))

(defn -onFailure
  [^Exception ex]
  (log/error (.getMessage ex))
  (log/trace ex))
