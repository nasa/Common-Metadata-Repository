(ns cmr.client.graph
  "The ClojureScript implementation of the CMR graph client."
  (:require
   [cmr.client.base.impl :as base-impl]
   [cmr.client.base.protocol :refer [CMRClientAPI]]
   [cmr.client.common.const :as const]
   [cmr.client.common.util :as util]
   [cmr.client.http.core :as http]
   [cmr.client.graph.impl :as graph :refer [->CMRGraphClientData
                                            CMRGraphClientData]]
   [cmr.client.graph.protocol :refer [CMRGraphAPI]])
  (:require-macros [cmr.client.common.util :refer [import-vars]])
  (:refer-clojure :exclude [get]))

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

(extend-type CMRGraphClientData
  CMRClientAPI
  (get-url
    [this segment]
    (base-impl/get-url this segment))
  (get-token
    [this]
    (base/get-token this))
  (get-token-header
    [this]
    (base/get-token-header this)))

(extend-type CMRGraphClientData
  CMRGraphAPI
  (get-collection-url-relation
    ([this concept-id]
     (get-collection-url-relation this concept-id {}))
    ([this concept-id http-options]
     (graph/get-collection-url-relation this concept-id http-options)))
  (get-movie
    ([this query-str]
     (get-movie this query-str {}))
    ([this query-str http-options]
     (graph/get-movie this query-str http-options))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constrcutor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:export create-client
  "The CMR graph client constructor."
  (util/create-service-client-constructor
   :graph
   #'cmr.client.graph/create-client
   ->CMRGraphClientData
   base-impl/create-options
   http/create-client))
