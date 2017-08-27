(ns cmr.client.ingest.core
 (:require
  [clj-http.conn-mgr :as conn-mgr]
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
  (impl/->CMRIngestClientOptions
    (:return-body? options)
    (conn-mgr/make-reusable-conn-manager
     ;; Use the same defaults that the `with-connection-pool` uses
     {:timeout 5
      :threads 4})))

(def create-client
  (util/create-service-client-constructor
   :ingest
   #'cmr.client.ingest.core/create-client
   impl/->CMRIngestClientData
   make-options
   http/create-client))
