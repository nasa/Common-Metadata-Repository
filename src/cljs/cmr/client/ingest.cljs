(ns cmr.client.ingest
  (:require
   [cmr.client.common.const :as const]
   [cmr.client.common.util :as util]
   [cmr.client.http :as http])
  (:refer-clojure :exclude [get]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Protocols &tc.   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol CMRIngestAPI
  (^:export get-url [this segment])
  (^:export get-providers [this]))

(defrecord CMRIngestClientOptions [
  return-body?])

(defrecord CMRIngestClientData [
  endpoint
  options
  http-client])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend-type CMRIngestClientData
  CMRIngestAPI
  (get-url
    [this segment]
    (str (:endpoint this) segment))

  (get-providers
    [this]
    (-> this
        :http-client
        (http/get (get-url this "/providers")))))

(defn make-options
  [options]
  (let [options (if (object? options)
                 (js->clj options :keywordize-keys true)
                 options)]
    (->CMRIngestClientOptions (:return-body? options))))

(def ^:export create-client
  (util/create-service-client-constructor
   :ingest
   #'cmr.client.ingest/create-client
   ->CMRIngestClientData
   make-options
   http/create-client))
