(ns cmr.opendap.query.core
  "This namespace defines records for the accepted URL query parameters or, if
  using HTTP POST, keys in a JSON payload. Additionall, functions for working
  with these parameters are defined here."
  (:require
   [clojure.string :as string]
   [cmr.opendap.query.const :as const]
   [cmr.opendap.query.impl.cmr :as cmr]
   [cmr.opendap.query.impl.giovanni :as giovanni]
   [cmr.opendap.query.impl.wcs :as wcs]
   [cmr.opendap.query.util :as util]
   [cmr.opendap.results.errors :as errors]
   [taoensso.timbre :as log])
  (:import
   (cmr.opendap.query.impl.cmr CollectionCmrStyleParams)
   (cmr.opendap.query.impl.giovanni CollectionGiovanniStyleParams)
   (cmr.opendap.query.impl.wcs CollectionWcsStyleParams))
  (:refer-clojure :exclude [parse]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Protocol Defnition   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol CollectionParamsAPI
  (->cmr [this] "Convert params record to CMR.")
  (->query-string [this] (str "Format all the defined parameters as a search "
                              "URL that represents a canonical query in the "
                              "service defined by the implementation.")))

(extend CollectionCmrStyleParams
        CollectionParamsAPI
        cmr/collection-behaviour)

(extend CollectionGiovanniStyleParams
        CollectionParamsAPI
        giovanni/collection-behaviour)

(extend CollectionWcsStyleParams
        CollectionParamsAPI
        wcs/collection-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create
  ([raw-params]
    (create (cond (nil? (:collection-id raw-params)) :missing-collection-id
                  (cmr/style? raw-params) :cmr
                  (giovanni/style? raw-params) :giovanni
                  (wcs/style? raw-params) :wcs
                  (util/ambiguous-style? raw-params) :cmr
                  :else :unknown-parameters-type)
            raw-params))
  ([params-type raw-params]
    (case params-type
      :wcs (wcs/create raw-params)
      :giovanni (giovanni/create raw-params)
      :cmr (cmr/create raw-params)
      :missing-collection-id {:errors [errors/missing-collection-id]}
      :unknown-parameters-type {:errors [errors/invalid-parameter
                                         (str "Parameters: " raw-params)]})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   High-level API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse
  "This is a convenience function for calling code that wants to create a
  collection params instance "
  [raw-params]
  (let [collection-params (create raw-params)]
    (if (errors/erred? collection-params)
      collection-params
      (->cmr collection-params))))
