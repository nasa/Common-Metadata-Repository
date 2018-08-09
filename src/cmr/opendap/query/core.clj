(ns cmr.opendap.query.core
  "This namespace defines records for the accepted URL query parameters or, if
  using HTTP POST, keys in a JSON payload. Additionall, functions for working
  with these parameters are defined here."
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.opendap.query.const :as const]
   [cmr.opendap.query.impl.wcs :as wcs]
   [cmr.opendap.query.impl.cmr :as cmr]
   [cmr.opendap.ous.util.core :as util]
   [cmr.opendap.results.errors :as errors]
   [taoensso.timbre :as log])
  (:import
   (cmr.opendap.query.impl.cmr CollectionCmrStyleParams)
   (cmr.opendap.query.impl.wcs CollectionWcsStyleParams))
  (:refer-clojure :exclude [parse]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Initial Setup & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn unique-params-keys
  [record-constructor]
  "This function returns only the record fields that are unique to the
  record of the given style. This is done by checking against a hard-coded set
  of fields shared that have been declared as common to all other parameter
  styles (see the `const` namespace)."
  (set/difference
   (set (keys (record-constructor {})))
   const/shared-keys))

(defn style?
  [record-constructor raw-params]
  "This function checks the raw params to see if they have any keys that
  overlap with the WCS-style record."
  (seq (set/intersection
        (set (keys raw-params))
        (unique-params-keys record-constructor))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Protocol Defnition   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol CollectionParamsAPI
  (->cmr [this]))

(extend CollectionCmrStyleParams
        CollectionParamsAPI
        cmr/collection-behaviour)

(extend CollectionWcsStyleParams
        CollectionParamsAPI
        wcs/collection-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create
  ([raw-params]
    (create (cond (nil? (:collection-id raw-params)) :missing-collection-id
                  (style? cmr/map->CollectionCmrStyleParams raw-params) :cmr
                  (style? wcs/map->CollectionWcsStyleParams raw-params) :wcs
                  :else :unknown-parameters-type)
            raw-params))
  ([params-type raw-params]
    (case params-type
      :wcs (wcs/create raw-params)
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
