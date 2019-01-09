(ns cmr.client.ingest
  "The Clojure implementation of the CMR ingest client."
  (:require
   [cmr.client.base.impl :as base-impl]
   [cmr.client.base.protocol :as base-api]
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
  [cmr.client.base.protocol
    get-url
    get-token
    get-token-header]
  [cmr.client.ingest.protocol
    get-providers
    create-collection
    update-collection
    create-variable
    update-variable])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend CMRIngestClientData
        base-api/CMRClientAPI
        base-impl/client-behaviour)

(extend CMRIngestClientData
        api/CMRIngestAPI
        impl/client-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constrcutor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def create-client
  "The CMR ingest client constructor."
  (util/create-service-client-constructor
   :ingest
   #'cmr.client.ingest/create-client
   impl/->CMRIngestClientData
   base-impl/create-options
   http/create-client))
