(ns cmr.client.ac
 (:require
  [cmr.client.base :as base]
  [cmr.client.common.util :as util]
  [cmr.client.http.core :as http]
  [cmr.client.ac.impl :as impl]
  [cmr.client.ac.protocol :as api]
  [potemkin :refer [import-vars]])
 (:import
  (cmr.client.ac.impl CMRAccessControlClientData)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Protocols &tc.   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(import-vars
  [cmr.client.ac.protocol
    get-acls
    get-groups
    get-health
    get-permissions])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend CMRAccessControlClientData
        base/CMRClientAPI
        base/client-behaviour)

(extend CMRAccessControlClientData
        api/CMRAccessControlAPI
        impl/client-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constrcutor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def create-client
  (util/create-service-client-constructor
   :access-control
   #'cmr.client.ac/create-client
   impl/->CMRAccessControlClientData
   base/make-options
   http/create-client))
