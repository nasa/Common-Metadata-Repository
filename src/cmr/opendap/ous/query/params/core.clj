(ns cmr.opendap.ous.query.params.core
  "This namespace defines records for the accepted URL query parameters or, if
  using HTTP POST, keys in a JSON payload. Additionall, functions for working
  with these parameters are defined here."
  (:require
   [clojure.string :as string]
   [cmr.opendap.ous.query.params.wcs :as wcs]
   [cmr.opendap.ous.query.params.cmr :as cmr]
   [cmr.opendap.ous.util.core :as util]
   [cmr.opendap.results.errors :as errors]
   [taoensso.timbre :as log])
  (:refer-clojure :exclude [parse]))

(defn params?
  [type params]
  (case type
    :wcs (wcs/params? params)
    :cmr (cmr/params? params)))

(defn create-params
  [type params]
  (case type
    :wcs (wcs/create-params params)
    :cmr (cmr/create-params params)))

(defn wcs->cmr
  [params]
  (let [subset (:subset params)]
    (-> params
        (assoc :collection-id (or (:collection-id params)
                                  (util/coverage->collection (:coverage params)))
               :granules (util/coverage->granules (:coverage params))
               :variables (:rangesubset params)
               ;; There was never an analog in wcs for exclude-granules, so set
               ;; to false.
               :exclude-granules false
               :bounding-box (when (seq subset)
                              (util/subset->bounding-box subset))
               :temporal (:timeposition params))
        (dissoc :coverage :rangesubset :timeposition)
        (cmr/map->CollectionCmrStyleParams))))

(defn parse
  [raw-params]
  (log/trace "Got params:" raw-params)
  (let [params (util/normalize-params raw-params)]
    (cond (params? :cmr params)
          (do
            (log/trace "Parameters are of type `collection` ...")
            (create-params :cmr params))

          (params? :wcs params)
          (do
            (log/trace "Parameters are of type `ous-prototype` ...")
            (wcs->cmr
             (create-params :wcs params)))

          (:collection-id params)
          (do
            (log/trace "Found collection id; assuming `collection` ...")
            (create-params :cmr params))

          :else
          {:errors [errors/invalid-parameter
                    (str "Parameters: " params)]})))
