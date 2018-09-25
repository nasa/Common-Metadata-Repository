(ns cmr.opendap.query.core
  "This namespace defines records for the accepted URL query parameters or, if
  using HTTP POST, keys in a JSON payload. Additionall, functions for working
  with these parameters are defined here."
  (:require
   [clojure.string :as string]
   [cmr.exchange.common.results.errors :as errors]
   [cmr.opendap.query.const :as const]
   [cmr.opendap.query.impl.cmr :as cmr]
   [cmr.opendap.query.impl.giovanni :as giovanni]
   [cmr.opendap.query.impl.wcs :as wcs]
   [cmr.opendap.query.util :as util]
   [cmr.opendap.results.errors :as ous-errors]
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
;;;   Utility/Support Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn missing-required-param?
  [raw-params required-params]
  (some #(nil? (% raw-params)) required-params))

(defn missing-required-params
  [raw-params required-params]
  (->> required-params
       (reduce (fn [acc x] (conj acc (when (nil? (x raw-params)) x))) [])
       (remove nil?)
       vec))

(defn missing-params-errors
  [missing]
  {:errors [(str errors/missing-parameters
                 " "
                 missing)]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- -create
  ([raw-params]
    (-create raw-params {}))
  ([raw-params opts]
    (let [params (util/normalize-params raw-params)
          missing-params (missing-required-params
                          params
                          (:required-params opts))
          dest (:destination opts)]
      (cond (seq missing-params)
            (missing-params-errors missing-params)

            (util/ambiguous-style? params)
            (cmr/create params)

            (or (= :cmr dest) (cmr/style? params))
            (cmr/create params)

            (or (= :giovanni dest) (giovanni/style? params))
            (giovanni/create params)

            (or (= :wcs dest) (wcs/style? params))
            (wcs/create params)

            :else
            {:errors [errors/invalid-parameter
                      (str "Parameters: " params)]}))))

(defn create
  [& args]
  (let [results (apply -create args)]
    (if (errors/erred? results)
        results
        (->cmr results))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   High-level API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; XXX When the query/params code is split out into its own library,
;; ous needs to retain a version of this function that passes the following
;; options to create:
;;
;;    {:required-params #{:collection-id}}

(defn parse
  "This is a convenience function for calling code that wants to create a
  collection params instance. By default, the params are converted to the
  default internal representation. However, in the case of the 2-arity an
  explicit desired type is indicated so no conversion is performed."
  ([raw-params]
    (parse raw-params nil))
  ([raw-params destination]
    (create raw-params {:destination destination
                        :required-params #{:collection-id}})))
