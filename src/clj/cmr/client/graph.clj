(ns cmr.client.graph
  "The Clojure implementation of the CMR graph client."
  (:require
   [cmr.client.base.impl :as base-impl]
   [cmr.client.base.protocol :as base-api]
   [cmr.client.common.util :as util]
   [cmr.client.http.core :as http]
   [cmr.client.graph.impl :as impl]
   [cmr.client.graph.protocol :as api]
   [potemkin :refer [import-vars]])
  (:import
   (cmr.client.graph.impl CMRGraphClientData)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Protocols &tc.   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(import-vars
  [cmr.client.base.protocol
    get-token
    get-token-header
    get-url]
  [cmr.client.graph.protocol
    get-collection-url-relation
    get-movie])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend CMRGraphClientData
        base-api/CMRClientAPI
        base-impl/client-behaviour)

(extend CMRGraphClientData
        api/CMRGraphAPI
        impl/client-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constrcutor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def create-client
  "The CMR graph client constructor."
  (util/create-service-client-constructor
   :graph
   #'cmr.client.graph/create-client
   impl/->CMRGraphClientData
   base-impl/create-options
   http/create-client))
