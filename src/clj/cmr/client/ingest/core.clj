(ns cmr.client.ingest.core
 (:require
  [cmr.client.common.const :as const]
  [cmr.client.common.util :as util]
  [cmr.client.http.core :as http]
  [cmr.client.ingest.impl :as impl])
 (:import (cmr.client.ingest.impl CMRIngestClientData CMRIngestClientOptions)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Protocols &tc.   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol CMRIngestAPI
  (get-url [this segment])
  (get-providers [this]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend CMRIngestClientData
        CMRIngestAPI
        impl/client-behaviour)

(defn make-options
  [options]
  (impl/->CMRIngestClientOptions (:return-body? options)))

(def create-client
  (util/create-service-client-constructor
   :ingest
   #'cmr.client.ingest.core/create-client
   impl/->CMRIngestClientData
   make-options
   http/create-client))
