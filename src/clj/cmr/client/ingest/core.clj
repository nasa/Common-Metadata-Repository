(ns cmr.client.ingest.core
 (:require
  [cmr.client.base :as base]
  [cmr.client.common.util :as util]
  [cmr.client.http.core :as http]
  [cmr.client.ingest.impl :as impl])
 (:import
  (cmr.client.ingest.impl CMRIngestClientData)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Protocols &tc.   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol CMRIngestAPI
  (get-providers [this]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend CMRIngestClientData
        CMRIngestAPI
        impl/client-behaviour)

(extend CMRIngestClientData
        base/CMRClientAPI
        base/client-behaviour)

(def create-client
  (util/create-service-client-constructor
   :ingest
   #'cmr.client.ingest.core/create-client
   impl/->CMRIngestClientData
   base/make-options
   http/create-client))
