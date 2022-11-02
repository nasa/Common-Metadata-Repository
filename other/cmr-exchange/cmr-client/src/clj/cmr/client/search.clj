(ns cmr.client.search
  "The Clojure implementation of the CMR search client."
  (:require
   [cmr.client.base.impl :as base-impl]
   [cmr.client.base.protocol :as base-api]
   [cmr.client.common.util :as util]
   [cmr.client.http.core :as http]
   [cmr.client.search.impl :as impl]
   [cmr.client.search.protocol :as api]
   [potemkin :refer [import-vars]])
  (:import
   (cmr.client.search.impl CMRSearchClientData)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Protocols &tc.   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(import-vars
  [cmr.client.base.protocol
    get-token
    get-token-header
    get-url]
  [cmr.client.search.protocol
    create-variable-association
    get-collections
    get-concept
    get-granules
    get-humanizers
    get-tag
    get-tags
    get-tiles
    get-variables])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend CMRSearchClientData
        base-api/CMRClientAPI
        base-impl/client-behaviour)

(extend CMRSearchClientData
        api/CMRSearchAPI
        impl/client-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constrcutor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def create-client
  "The CMR search client constructor."
  (util/create-service-client-constructor
   :search
   #'cmr.client.search/create-client
   impl/->CMRSearchClientData
   base-impl/create-options
   http/create-client))
