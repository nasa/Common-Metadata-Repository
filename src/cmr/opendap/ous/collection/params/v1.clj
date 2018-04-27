(ns cmr.opendap.ous.collection.params.v1
  (:require
   [clojure.set :as set]
   [cmr.opendap.ous.collection.params.const :as const]
   [cmr.opendap.ous.util :as util]))

(defrecord OusPrototypeParams
  [;; `format` is any of the formats supported by the target OPeNDAP server,
   ;; such as `json`, `ascii`, `nc`, `nc4`, `dods`, etc.
   format
   ;; `coverage` can be:
   ;;  * a list of granule concept ids
   ;;  * a list of granule ccontept ids + a collection id
   ;;  * a single collection id
   coverage
   ;; `rangesubset` is a list of UMM-Var names
   rangesubset
   ;; `subset` is used to indicate desired spatial subsetting and is a list of
   ;; lon/lat values, as used in WCS. It is parsed from URL queries like so:
   ;;  `?subset=lat(22,34)&subset=lon(169,200)`
   ;; giving values like so:
   ;;  `["lat(22,34)" "lon(169,200)"]`
   subset])

(def params-keys
  (set/difference
   (set (keys (map->OusPrototypeParams {})))
   const/shared-keys))

(defn params?
  [params]
  (seq (set/intersection
        (set (keys params))
        params-keys)))

(defn create-params
  [params]
  (map->OusPrototypeParams
    (assoc params :format (or (:format params)
                              const/default-format)
                  :coverage (util/->seq (:coverage params))
                  :rangesubset (util/->seq (:rangesubset params)))))
