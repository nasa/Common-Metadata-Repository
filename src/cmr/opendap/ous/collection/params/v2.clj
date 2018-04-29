(ns cmr.opendap.ous.collection.params.v2
  (:require
   [clojure.set :as set]
   [cmr.opendap.ous.collection.params.const :as const]
   [cmr.opendap.ous.util :as ous-util]
   [cmr.opendap.util :as util]))

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
   ;; `granules` is list of granule concept ids; default behaviour is a
   ;; whitelist.
   granules
   ;; `exclude-granules` is a boolean when set to true causes granules list
   ;; to be a blacklist.
   exclude-granules
   ;; XXX Is there where we want to accept the paging size for granule
   ;;     concepts?
   ;; granule-count
   ;;
   ;; `variables` is a list of variables to be speficied when creating the
   ;; OPeNDAP URL. This is used for subsetting.
   variables
   ;; `spatial-subset` is used the same way as `subset` for WCS.
   ;; `subset` is used to indicate desired spatial subsetting and is used in
   ;; URL queries like so:
   ;;  `?subset=lat(22,34)&subset=lon(169,200)`
   subset
   ;; `bounding-box` is provided for CMR/EDSC-compatibility as an alternative
   ;; to using `subset` for spatial-subsetting.
   bounding-box])

(def params-keys
  (set/difference
   (set (keys (map->CollectionParams {})))
   const/shared-keys))

(defn params?
  [params]
  (seq (set/intersection
        (set (keys params))
        params-keys)))

(defn create-params
  [params]
  (map->CollectionParams
    (assoc params
      :format (or (:format params) const/default-format)
      :granules (ous-util/->seq (:granules params))
      :variables (ous-util/->seq (:variables params))
      :exclude-granules (util/bool (:exclude-granules params)))))

(defrecord CollectionsParams
  [;; This isn't defined for the OUS Prototype, since it didn't support
   ;; submitting multiple collections at a time. As such, there is no
   ;; prototype-oriented record for this.
   ;;
   ;; `collections` is a list of `CollectionParams` records.
   collections])
