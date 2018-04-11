(ns cmr.opendap.ous.collection
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.opendap.ous.util :as util]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-format "nc")

(def shared-keys
  #{:collection-id :format :subset})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Records and Support Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord OusPrototypeParams
  [;; `format` is any of the formats supported by the target OPeNDAP server,
   ;; such as `json`, `ascii`, `nc`, `nc4`, `dods`, etc.
   format
   ;; `coverage` can be:
   ;;  * a list of granule concept ids
   ;;  * a list of granule ccontept ids + a collection id
   ;;  * a single collection id
   coverage
   ;; `rangesubset` is a list of comma-separated UMM-Var names
   rangesubset
   ;; `subset` is used to indicate desired spatial subsetting and is used in
   ;; URL queries like so:
   ;;  `?subset=lat(22,34)&subset=lon(169,200)`
   subset])

(def ous-prototype-params-keys
  (set/difference
   (set (keys (map->OusPrototypeParams {})))
   shared-keys))

(defn ous-prototype-params?
  [params]
  (seq (set/intersection
        (set (keys params))
        ous-prototype-params-keys)))

(defn create-ous-prototype-params
  [params]
  (map->OusPrototypeParams
    (assoc params
      :format (or (:format params) default-format))))

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

(defrecord CollectionsParams
  [;; This isn't defined for the OUS Prototype, since it didn't support
   ;; submitting multiple collections at a time. As such, there is no
   ;; prototype-oriented record for this.
   ;;
   ;; `collections` is a list of `CollectionParams` records.
   collections])

(def collection-params-keys
  (set/difference
   (set (keys (map->CollectionParams {})))
   shared-keys))

(defn collection-params?
  [params]
  (seq (set/intersection
        (set (keys params))
        collection-params-keys)))

(defn create-collection-params
  [params]
  (map->CollectionParams
    (assoc params
      :format (or (:format params) default-format)
      :granules (util/->seq (:granules params))
      :variables (util/->seq (:variables params)))))

(defn params?
  [type params]
  (case type
    :v1 (ous-prototype-params? params)
    :v2 (collection-params? params)))

(defn create-params
  [type params]
  (case type
    :v1 (create-ous-prototype-params params)
    :v2 (create-collection-params params)))
