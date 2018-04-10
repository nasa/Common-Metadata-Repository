(ns cmr.opendap.ous.collection
  (:require
   [clojure.set :as set]))

;;; We're going to codify parameters with records to keep things well
;;; documented. Additionally, this will make converting between parameter
;;; schemes an explicit operation on explicit data.

;; Notes on representing spatial extents.
;;
;; EDSC uses URL-encoded long/lat numbers representing a bounding box
;; Note that the ordering is the same as that used by CMR (see below).
;;  `-9.984375%2C56.109375%2C19.828125%2C67.640625`
;; which URL-decodes to:
;;  `-9.984375,56.109375,19.828125,67.640625`
;;
;; OPeNDAP download URLs have something I haven't figured out yet; given that
;; one of the numbers if over 180, it can't be degrees ... it might be what
;; WCS uses for `x` and `y`?
;;  `Latitude[22:34],Longitude[169:200]`
;;
;; The OUS Prototype uses the WCS standard for lat/long:
;;  `SUBSET=axis[,crs](low,high)`
;; For lat/long this takes the following form:
;;  `subset=lat(56.109375,67.640625)&subset=lon(-9.984375,19.828125)`
;;
;; CMR supports bounding spatial extents by describing a rectangle using four
;; comma-separated values:
;;  1. lower left longitude
;;  2. lower left latitude
;;  3. upper right longitude
;;  4. upper right latitude
;; For example:
;;  `bounding_box==-9.984375,56.109375,19.828125,67.640625`
;;
;; Google's APIs use lower left, upper right, but the specify lat first, then
;; long:
;;  `southWest = LatLng(56.109375,-9.984375);`
;;  `northEast = LatLng(67.640625,19.828125);`

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
   ;; `exclude-granules?` is a boolean when set to true causes granules list
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

(def shared-keys
  #{:format :subset})

(def ous-prototype-params-keys
  (set/difference
   (set (keys (map->OusPrototypeParams {})))
   shared-keys))

(def collection-params-keys
  (set/difference
   (set (keys (map->CollectionParams {})))
   shared-keys))

(defn ous-prototype-params?
  [params]
  (seq (set/intersection
        (set (keys params))
        ous-prototype-params-keys)))

(defn collection-params?
  [params]
  (seq (set/intersection
        (set (keys params))
        collection-params-keys)))

(defn get-opendap-urls
  [params]
  (cond (collection-params? params)
        (map->CollectionParams params)
        (ous-prototype-params? params)
        (map->OusPrototypeParams params)
        :else {:error :unsupported-parameters}))
