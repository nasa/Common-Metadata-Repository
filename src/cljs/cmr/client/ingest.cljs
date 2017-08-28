(ns cmr.client.ingest
  (:require
   [cmr.client.base :refer [make-options CMRClientAPI]]
   [cmr.client.base.impl :as base]
   [cmr.client.common.const :as const]
   [cmr.client.common.util :as util]
   [cmr.client.http.core :as http]
   [cmr.client.ingest.impl :as ingest :refer [->CMRIngestClientData
                                              CMRIngestClientData]]
   [cmr.client.ingest.protocol :refer [CMRIngestAPI]])
  (:require-macros [cmr.client.common.util :refer [import-vars]])
  (:refer-clojure :exclude [get]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Protocols &tc.   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(import-vars
  [cmr.client.ingest.protocol
    get-providers])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend-type CMRIngestClientData
  CMRClientAPI
  (get-url
    [this segment]
    (base/get-url this segment)))

(extend-type CMRIngestClientData
  CMRIngestAPI
  (get-providers
    [this]
    (ingest/get-providers this)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constrcutor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:export create-client
  (util/create-service-client-constructor
   :ingest
   #'cmr.client.ingest/create-client
   ->CMRIngestClientData
   make-options
   http/create-client))
