(ns cmr.opendap.ous.query.params.core
  "This namespace defines records for the accepted URL query parameters or, if
  using HTTP POST, keys in a JSON payload. Additionall, functions for working
  with these parameters are defined here."
  (:require
   [clojure.string :as string]
   [cmr.opendap.errors :as errors]
   [cmr.opendap.ous.query.params.v1 :as v1]
   [cmr.opendap.ous.query.params.v2 :as v2]
   [cmr.opendap.ous.util :as util]
   [taoensso.timbre :as log])
  (:refer-clojure :exclude [parse]))

(defn params?
  [type params]
  (case type
    :v1 (v1/params? params)
    :v2 (v2/params? params)))

(defn create-params
  [type params]
  (case type
    :v1 (v1/create-params params)
    :v2 (v2/create-params params)))

(defn v1->v2
  [params]
  (let [subset (:subset params)]
    (-> params
        (assoc :collection-id (or (:collection-id params)
                                  (util/coverage->collection (:coverage params)))
               :granules (util/coverage->granules (:coverage params))
               :variables (:rangesubset params)
               ;; There was never an analog in v1 for exclude-granules, so set
               ;; to false.
               :exclude-granules false
               :bounding-box (when (seq subset)
                              (util/subset->bounding-box subset))
               :temporal (:timeposition params))
        (dissoc :coverage :rangesubset :timeposition)
        (v2/map->CollectionParams))))

(defn parse
  [raw-params]
  (log/trace "Got params:" raw-params)
  (let [params (util/normalize-params raw-params)]
    (cond (params? :v2 params)
          (do
            (log/trace "Parameters are of type `collection` ...")
            (create-params :v2 params))

          (params? :v1 params)
          (do
            (log/trace "Parameters are of type `ous-prototype` ...")
            (v1->v2
             (create-params :v1 params)))

          (:collection-id params)
          (do
            (log/trace "Found collection id; assuming `collection` ...")
            (create-params :v2 params))

          :else
          {:errors [errors/invalid-parameter
                    (str "Parameters: " params)]})))
