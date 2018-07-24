(ns cmr.opendap.ous.query.params.wcs
  (:require
   [clojure.set :as set]
   [cmr.opendap.ous.query.params.const :as const]
   [cmr.opendap.ous.util.core :as util]))

(defrecord CollectionWcsStyleParams
  [;; `format` is any of the formats supported by the target OPeNDAP server,
   ;; such as `json`, `ascii`, `nc`, `nc4`, `dods`, etc.
   format
   ;;
   ;; `coverage` can be:
   ;;  * a list of granule concept ids
   ;;  * a list of granule ccontept ids + a collection concept id
   ;;  * a single collection concept id
   coverage
   ;;
   ;; `rangesubset` is a list of UMM-Var concept ids
   rangesubset
   ;;
   ;; `subset` is used to indicate desired spatial subsetting and is a list of
   ;; lon/lat values, as used in WCS. It is parsed from URL queries like so:
   ;;  `?subset=lat(22,34)&subset=lon(169,200)`
   ;; giving values like so:
   ;;  `["lat(22,34)" "lon(169,200)"]`
   subset
   ;; `timeposition` is used to indicate temporal subsetting with starting
   ;; and ending values being ISO 8601 datetime stamps, separated by a comma.
   timeposition])

(def params-keys
  (set/difference
   (set (keys (map->CollectionWcsStyleParams {})))
   const/shared-keys))

(defn params?
  [params]
  (seq (set/intersection
        (set (keys params))
        params-keys)))

(defn create-params
  [params]
  (map->CollectionWcsStyleParams
    (assoc params :format (or (:format params)
                              const/default-format)
                  :coverage (util/split-comma->sorted-coll (:coverage params))
                  :rangesubset (util/split-comma->sorted-coll (:rangesubset params))
                  :timeposition (util/->coll (:timeposition params)))))
