(ns cmr.client.ac
  (:require
   [cmr.client.base :refer [make-options CMRClientAPI]]
   [cmr.client.base.impl :as base]
   [cmr.client.common.const :as const]
   [cmr.client.common.util :as util]
   [cmr.client.http.core :as http]
   [cmr.client.ac.impl :as ac :refer [->CMRAccessControlClientData
                                      CMRAccessControlClientData]]
   [cmr.client.ac.protocol :refer [CMRAccessControlAPI]])
  (:require-macros [cmr.client.common.util :refer [import-vars]])
  (:refer-clojure :exclude [get]))

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

(extend-type CMRAccessControlClientData
  CMRClientAPI
  (get-url
    [this segment]
    (base/get-url this segment)))

(extend-type CMRAccessControlClientData
  CMRAccessControlAPI
  (get-acls
    ([this http-options]
     (get-acls this {} http-options))
    ([this query-params http-options]
     (ac/get-acls this query-params http-options)))
  (get-groups
    ([this http-options]
     (get-groups this {} http-options))
    ([this query-params http-options]
     (ac/get-groups this query-params http-options)))
  (get-health
    ([this]
     (get-health this {}))
    ([this http-options]
     (ac/get-health this http-options)))
  (get-permissions
    ([this http-options]
     (get-permissions this {} http-options))
    ([this query-params http-options]
     (ac/get-permissions this query-params http-options))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constrcutor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:export create-client
  (util/create-service-client-constructor
   :access-control
   #'cmr.client.ac/create-client
   ->CMRAccessControlClientData
   make-options
   http/create-client))
