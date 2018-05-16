(ns cmr.opendap.ous.query.params.v2
  (:require
   [clojure.set :as set]
   [cmr.opendap.ous.query.params.const :as const]
   [cmr.opendap.ous.util :as ous-util]
   [cmr.opendap.util :as util]
   [taoensso.timbre :as log]))

(defrecord CollectionParams
  [;; `collection-id` is the concept id for the collection in question. Note
   ;; that the collection concept id is not provided in query params,
   ;; but in the path as part of the REST URL. Regardless, we offer it here as
   ;; a record field.
   collection-id
   ;;
   ;; `format` is any of the formats supported by the target OPeNDAP server,
   ;; such as `json`, `ascii`, `nc`, `nc4`, `dods`, etc.
   format
   ;;
   ;; `granules` is list of granule concept ids; default behaviour is a
   ;; whitelist.
   granules
   ;;
   ;; `exclude-granules` is a boolean when set to true causes granules list
   ;; to be a blacklist.
   exclude-granules
   ;;
   ;; XXX Is this where we want to accept the paging size for granule
   ;;     concepts?
   ;; granule-count
   ;;
   ;; `variables` is a list of variables to be speficied when creating the
   ;; OPeNDAP URL. This is used for subsetting.
   variables
   ;;
   ;; `subset` is used the same way as `subset` for WCS: to indicate desired
   ;; spatial subsetting in URL queries like so:
   ;;  `?subset=lat(56.109375,67.640625)&subset=lon(-9.984375,19.828125)`
   subset
   ;;
   ;; `bounding-box` is provided for CMR/EDSC-compatibility as an alternative
   ;; to using `subset` for spatial-subsetting.
   bounding-box
   ;; `temporal` is used to indicate temporal subsetting with starting
   ;; and ending values being ISO 8601 datetime stamps.
   temporal])

(def params-keys
  (set/difference
   (set (keys (map->CollectionParams {})))
   const/shared-keys))

(defn params?
  [params]
  (seq (set/intersection
        (set (keys params))
        params-keys)))

(defn not-array?
  [array]
  (or (nil? array)
      (empty? array)))

(defn create-params
  [params]
  (let [bounding-box (ous-util/->seq (:bounding-box params))
        subset (:subset params)
        granules-array (ous-util/->seq (get params (keyword "granules[]")))
        variables-array (ous-util/->seq (get params (keyword "variables[]")))]
    (log/trace "bounding-box:" bounding-box)
    (log/trace "subset:" subset)
    (log/trace "granules-array:" granules-array)
    (log/trace "variables-array:" variables-array)
    (map->CollectionParams
      (assoc params
        :format (or (:format params) const/default-format)
        :granules (if (not-array? granules-array)
                       (ous-util/->seq (:granules params))
                       granules-array)
        :variables (if (not-array? variables-array)
                       (ous-util/->seq (:variables params))
                       variables-array)
        :exclude-granules (util/bool (:exclude-granules params))
        :subset (if (seq bounding-box)
                 (ous-util/bounding-box->subset bounding-box)
                 (:subset params))
        :bounding-box (if (seq bounding-box)
                       (mapv #(Float/parseFloat %) bounding-box)
                       (when (seq subset)
                        (ous-util/subset->bounding-box subset)))))))

(defrecord CollectionsParams
  [;; This isn't defined for the OUS Prototype, since it didn't support
   ;; submitting multiple collections at a time. As such, there is no
   ;; prototype-oriented record for this.
   ;;
   ;; `collections` is a list of `CollectionParams` records.
   collections])
