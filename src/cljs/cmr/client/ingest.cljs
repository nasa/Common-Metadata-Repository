(ns cmr.client.ingest
  "The ClojureScript implementation of the CMR ingest client."
  (:require
   [cmr.client.base :refer [make-options]]
   [cmr.client.base.impl :as base-impl]
   [cmr.client.base.protocol :refer [CMRClientAPI]]
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
    (base-impl/get-url this segment)))

(extend-type CMRIngestClientData
  CMRIngestAPI
  (get-providers
    [this]
    (ingest/get-providers this)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constrcutor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:export create-client
  "The CMR ingest client constructor."
  (util/create-service-client-constructor
   :ingest
   #'cmr.client.ingest/create-client
   ->CMRIngestClientData
   base-impl/create-options
   http/create-client))
