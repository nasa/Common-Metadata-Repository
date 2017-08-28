(ns cmr.client.ingest
 (:require
  [cmr.client.base :as base]
  [cmr.client.common.util :as util]
  [cmr.client.http.core :as http]
  [cmr.client.ingest.impl :as impl]
  [cmr.client.ingest.protocol :as api]
  [potemkin :refer [import-vars]])
 (:import
  (cmr.client.ingest.impl CMRIngestClientData)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Protocols &tc.   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(import-vars
  [cmr.client.ingest.protocol
    get-providers])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend CMRIngestClientData
        base/CMRClientAPI
        base/client-behaviour)

(extend CMRIngestClientData
        api/CMRIngestAPI
        impl/client-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constrcutor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def create-client
  (util/create-service-client-constructor
   :ingest
   #'cmr.client.ingest/create-client
   impl/->CMRIngestClientData
   base/make-options
   http/create-client))
