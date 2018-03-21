(ns cmr.client.search
  "The ClojureScript implementation of the CMR search client."
  (:require
   [cmr.client.base.impl :as base-impl]
   [cmr.client.base.protocol :refer [CMRClientAPI]]
   [cmr.client.common.const :as const]
   [cmr.client.common.util :as util]
   [cmr.client.http.core :as http]
   [cmr.client.search.impl :as search :refer [->CMRSearchClientData
                                              CMRSearchClientData]]
   [cmr.client.search.protocol :refer [CMRSearchAPI]])
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

(extend-type CMRSearchClientData
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

(extend-type CMRSearchClientData
  CMRSearchAPI
  (create-variable-association
    ([this concept-id collection-data]
     (create-variable-association this {}))
    ([this concept-id collection-data http-options]
     (create-variable-association this {} http-options))
    ([this concept-id collection-data query-params http-options]
     (search/create-variable-association
      this concept-id collection-data query-params http-options)))
  (get-collections
    ([this]
     (get-collections this {}))
    ([this http-options]
     (get-collections this {} http-options))
    ([this query-params http-options]
     (search/get-collections this query-params http-options)))
  (get-concept
    ([this concept-id]
     (get-concept this concept-id {}))
    ([this concept-id http-options]
     (search/get-concept this concept-id http-options))
    ([this concept-id revision-id http-options]
     (search/get-concept this concept-id revision-id http-options)))
  (get-granules
    ([this]
     (get-granules this {}))
    ([this http-options]
     (get-granules this {} http-options))
    ([this query-params http-options]
     (search/get-granules this query-params http-options)))
  (get-humanizers
    ([this]
     (get-humanizers this {}))
    ([this http-options]
     (search/get-humanizers this http-options)))
  (get-tag
    ([this tag-id]
     (get-tag this tag-id {}))
    ([this tag-id http-options]
     (get-tag this tag-id {} http-options))
    ([this tag-id query-params http-options]
     (search/get-concept this tag-id query-params http-options)))
  (get-tags
    ([this]
     (get-tags this {}))
    ([this http-options]
     (get-tags this {} http-options))
    ([this query-params http-options]
     (search/get-tags this query-params http-options)))
  (get-tiles
    ([this]
     (get-tiles this {}))
    ([this http-options]
     (get-tiles this {} http-options))
    ([this query-params http-options]
     (search/get-tiles this query-params http-options)))
  (get-variables
    ([this]
     (get-variables this {}))
    ([this http-options]
     (get-variables this {} http-options))
    ([this query-params http-options]
     (search/get-variables this query-params http-options))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constrcutor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:export create-client
  "The CMR search client constructor."
  (util/create-service-client-constructor
   :search
   #'cmr.client.search/create-client
   ->CMRSearchClientData
   base-impl/create-options
   http/create-client))
