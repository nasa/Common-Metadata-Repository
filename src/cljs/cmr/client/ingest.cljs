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

(defn ^:export create-client
  ([]
   (create-client {}))
  ([options]
   (create-client options {}))
  ([options http-options]
   (let [endpoint (util/get-default-endpoint options :ingest)
         options (when (object? options) (js->clj options :keywordize-keys true))
         client-options (->CMRIngestClientOptions (:return-body? options))]
     (->CMRIngestClientData endpoint
                            client-options
                            (http/create-client client-options http-options)))))
