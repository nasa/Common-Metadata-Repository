(ns cmr.client.search
  (:require
   [cmr.client.base :refer [make-options CMRClientAPI]]
   [cmr.client.base.impl :as base]
   [cmr.client.common.const :as const]
   [cmr.client.common.util :as util]
   [cmr.client.http :as http]
   [cmr.client.search.impl :as search :refer [->CMRSearchClientData
                                              CMRSearchClientData]])
  (:refer-clojure :exclude [get]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Protocols &tc.   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol CMRSearchAPI
  (^:export get-collections
   [this]
   [this http-options]
   [this query-params http-options])
  (^:export get-concept
   [this concept-id http-options]
   [this concept-id revision-id http-options])
  (^:export get-granules [this http-options] [this query-params http-options])
  (^:export get-humanizers [this] [this http-options])
  (^:export get-tag
   [this tag-id http-options]
   [this tag-id query-params http-options])
  (^:export get-tags [this http-options] [this query-params http-options])
  (^:export get-tiles [this http-options] [this query-params http-options])
  (^:export get-variables
   [this http-options]
   [this query-params http-options]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend-type CMRSearchClientData
  CMRClientAPI
  (get-url
    [this segment]
    (base/get-url this segment)))

(extend-type CMRSearchClientData
  CMRSearchAPI
  (get-collections
    ([this]
     (get-collections this {}))
    ([this http-options]
     (get-collections this {} http-options))
    ([this query-params http-options]
     (search/get-collections this query-params http-options)))
  (get-concept
    ([this concept-id http-options]
     (search/get-concept this concept-id http-options))
    ([this concept-id revision-id http-options]
     (search/get-concept this concept-id revision-id http-options)))
  (get-granules
    ([this http-options]
     (get-granules this {} http-options))
    ([this query-params http-options]
     (search/get-granules this query-params http-options)))
  (get-humanizers
    ([this]
     (get-granules this {}))
    ([this http-options]
     (search/get-granules this http-options)))
  (get-tag
    ([this tag-id http-options]
     (get-tag this tag-id {} http-options))
    ([this tag-id query-params http-options]
     (search/get-concept this tag-id query-params http-options)))
  (get-tags
    ([this http-options]
     (get-tags this {} http-options))
    ([this query-params http-options]
     (search/get-tags this query-params http-options)))
  (get-tiles
    ([this http-options]
     (get-tiles this {} http-options))
    ([this query-params http-options]
     (search/get-tiles this query-params http-options)))
  (get-variables
    ([this http-options]
     (get-variables this {} http-options))
    ([this query-params http-options]
     (search/get-variables this query-params http-options))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constrcutor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:export create-client
  (util/create-service-client-constructor
   :search
   #'cmr.client.search/create-client
   ->CMRSearchClientData
   make-options
   http/create-client))
