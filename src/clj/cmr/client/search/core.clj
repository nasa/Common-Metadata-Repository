(ns cmr.client.search.core
 (:require
  [cmr.client.base :as base]
  [cmr.client.common.util :as util]
  [cmr.client.http.core :as http]
  [cmr.client.search.impl :as impl])
 (:import
  (cmr.client.search.impl CMRSearchClientData)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Protocols &tc.   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol CMRSearchAPI
  (get-collections [this] [this http-options] [this query-params http-options])
  (get-concept [this concept-id http-options]
               [this concept-id revision-id http-options])
  (get-granules [this http-options] [this query-params http-options])
  (get-humanizers [this] [this http-options])
  (get-tag [this tag-id http-options] [this tag-id query-params http-options])
  (get-tags [this http-options] [this query-params http-options])
  (get-tiles [this http-options] [this query-params http-options])
  (get-variables [this http-options] [this query-params http-options]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend CMRSearchClientData
        CMRSearchAPI
        impl/client-behaviour)

(extend CMRSearchClientData
        base/CMRClientAPI
        base/client-behaviour)

(def create-client
  (util/create-service-client-constructor
   :search
   #'cmr.client.search.core/create-client
   impl/->CMRSearchClientData
   base/make-options
   http/create-client))
